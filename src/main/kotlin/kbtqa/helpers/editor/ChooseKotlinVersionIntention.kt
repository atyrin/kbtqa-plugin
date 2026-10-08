package kbtqa.helpers.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import kbtqa.helpers.versions.KotlinVersionsService
import kbtqa.helpers.versions.VersionsService.VersionChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.VisibleForTesting

/**
 * Intention on the `<kotlin.version>` property of a pom.xml that sets it from a popup of
 * `kotlin-maven-plugin` versions, grouped as dev, experimental and Maven Central, newest first in
 * each. Typing filters the list. A version Maven Central has is listed there only, since it needs
 * no extra repository; one that both JetBrains repositories have is listed under dev.
 */
class ChooseKotlinVersionIntention : PsiElementBaseIntentionAction(), DumbAware {

    companion object {
        private const val TEXT = "Choose Kotlin version"
        private const val REPOSITORY_HINT =
            "dev and experimental builds need their repository: QA Helpers | Configure Repositories"
        private const val UNAVAILABLE_HINT = "Could not load Kotlin versions: none of the repositories answered"
    }

    /** A popup entry: a version and the channel it is listed under. */
    internal data class VersionItem(val version: String, val channel: String)

    override fun getFamilyName(): String = TEXT

    override fun getText(): String = TEXT

    // The popup comes first; only the chosen version is written, in a command of its own
    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
        MavenProperties.isPomFile(element.containingFile?.name) && kotlinVersionTag(element) != null

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val tag = kotlinVersionTag(element) ?: return
        val pointer = SmartPointerManager.createPointer(tag)
        val current = tag.value.trimmedText

        object : Task.Backgroundable(project, "Loading Kotlin versions", true) {
            private var items: List<VersionItem>? = null

            override fun run(indicator: ProgressIndicator) {
                items = service<KotlinVersionsCache>().channels()?.let(::versionItems)
            }

            override fun onSuccess() {
                if (editor.isDisposed) return
                val loaded = items
                if (loaded == null) {
                    HintManager.getInstance().showErrorHint(editor, UNAVAILABLE_HINT)
                    return
                }
                showPopup(project, editor, pointer, loaded, current)
            }
        }.queue()
    }

    private fun showPopup(
        project: Project,
        editor: Editor,
        pointer: SmartPsiElementPointer<XmlTag>,
        items: List<VersionItem>,
        current: String
    ) {
        val builder = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle("Kotlin Version")
            .setRenderer(GroupedListRenderer<VersionItem>({ it.version }, { it.channel }))
            .setNamerForFiltering { it.version }
            .setAdText(REPOSITORY_HINT)
            .setItemChosenCallback { setVersion(project, editor, pointer, it.version) }
        // Start from the version the pom has, when it is one of the listed ones
        items.firstOrNull { it.version == current }?.let { builder.setSelectedValue(it, true) }
        builder.createPopup().showInBestPositionFor(editor)
    }

    /** What choosing [version] in the popup does. */
    @VisibleForTesting
    internal fun setVersion(project: Project, editor: Editor, pointer: SmartPsiElementPointer<XmlTag>, version: String) {
        val tag = pointer.element ?: return
        WriteCommandAction.runWriteCommandAction(project, TEXT, null, {
            val target = MavenPomEditing.expandIfSelfClosed(project, tag, tag.name)
            target.value.text = version
            // PSI modifications block the document; offsets are only usable once it is unblocked
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            MavenPomEditing.moveCaretIntoTag(editor, target)
        }, tag.containingFile)
    }

    /**
     * The versions of [channels] in their order, each once. One that Maven Central has counts as a
     * Maven Central version; otherwise it goes to the first channel that has it.
     */
    @VisibleForTesting
    internal fun versionItems(channels: List<VersionChannel>): List<VersionItem> {
        val owners = mutableMapOf<String, String>()
        // Maven Central, the only channel without a repository to add, claims its versions first
        for (channel in channels.sortedBy { it.repositoryUrl != null }) {
            channel.versions.forEach { owners.putIfAbsent(it, channel.name) }
        }
        return channels.flatMap { channel ->
            channel.versions.filter { owners[it] == channel.name }.map { VersionItem(it, channel.name) }
        }
    }

    /** The `<kotlin.version>` property [element] is in, its name or its value. */
    private fun kotlinVersionTag(element: PsiElement): XmlTag? {
        val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false) ?: return null
        return tag.takeIf {
            it.name == MavenKotlinPlugin.KOTLIN_VERSION_PROPERTY && it.parentTag?.name == MavenProperties.PROPERTIES_TAG
        }
    }
}

/**
 * The versions [ChooseKotlinVersionIntention] offers, fetched on first use and reused for a while,
 * so that choosing again does not wait for the network.
 */
@Service(Service.Level.APP)
class KotlinVersionsCache(private val scope: CoroutineScope) {

    private companion object {
        const val TTL_MILLIS = 10 * 60 * 1000L
    }

    private var pending: Deferred<List<VersionChannel>>? = null
    private var fetchedAt = 0L

    /**
     * The channels, waiting cancellably for a fetch in progress; `null` when none of the
     * repositories answered. Must be called under a progress indicator, off the EDT.
     */
    fun channels(): List<VersionChannel>? {
        val deferred = synchronized(this) {
            val current = pending
            if (current != null && System.currentTimeMillis() - fetchedAt < TTL_MILLIS) {
                current
            } else {
                scope.async { service<KotlinVersionsService>().getMavenPluginVersionChannels() }
                    .also { pending = it; fetchedAt = System.currentTimeMillis() }
            }
        }
        val channels = ProgressIndicatorUtils.awaitWithCheckCanceled(deferred.asCompletableFuture())
        if (channels.all { it.versions.isEmpty() }) {
            // Offline: try again next time rather than keep an empty answer
            synchronized(this) { if (pending === deferred) pending = null }
            return null
        }
        return channels
    }
}
