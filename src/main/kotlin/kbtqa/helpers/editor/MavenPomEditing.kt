package kbtqa.helpers.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlComment
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText

/**
 * XML PSI helpers shared by the actions that edit a `pom.xml`.
 *
 * All of them must be called inside a write action, on a committed document; the editing ones
 * inside [editKeepingComments]. Inserted tags are not formatted here: the platform formats them
 * when the changes are flushed, and only them, so the rest of the pom keeps the user's layout.
 */
internal object MavenPomEditing {

    /**
     * Returns the direct child of the root `<project>` tag named [tagName], creating it when it is
     * missing. A created section is placed after the first present anchor from [anchors] and gets a
     * blank line above it so that it does not stick to the preceding tag.
     */
    fun findOrCreateRootSection(
        project: Project,
        xmlFile: XmlFile,
        tagName: String,
        anchors: List<String>
    ): XmlTag? {
        val root = xmlFile.rootTag ?: return null
        root.findFirstSubTag(tagName)?.let { return expandIfSelfClosed(project, it, tagName) }

        val emptySection = createEmptyTag(project, tagName)
        val anchorName = MavenProperties.anchorName(root.subTags.map { it.name }, anchors)
        val anchor = anchorName?.let { root.findFirstSubTag(it) }
        val section = if (anchor != null) {
            root.addAfter(emptySection, anchor) as? XmlTag
        } else {
            root.addSubTag(emptySection, true)
        } ?: return null

        // Appended after a sibling, the block would otherwise stick to the tag above it
        return if (anchor != null) separateWithBlankLine(project, section, tagName) else section
    }

    /** Turns `<tag/>` into `<tag></tag>` so that sub-tags can be added to it. */
    fun expandIfSelfClosed(project: Project, tag: XmlTag, tagName: String): XmlTag {
        if (!tag.isEmpty) return tag
        return tag.replace(createEmptyTag(project, tagName)) as? XmlTag ?: tag
    }

    fun createEmptyTag(project: Project, tagName: String): XmlTag =
        XmlElementFactory.getInstance(project).createTagFromText("<$tagName>\n</$tagName>")

    /**
     * Adds [tagText] as the last sub-tag of [parent]. The factory parses without the POM default
     * namespace in scope, so any `xmlns` it adds is dropped again.
     */
    fun addChildTag(project: Project, parent: XmlTag, tagText: String): XmlTag {
        val newTag = XmlElementFactory.getInstance(project).createTagFromText(tagText)
        val inserted = parent.addSubTag(newTag, false)
        inserted.getAttribute("xmlns")?.delete()
        return inserted
    }

    /** Inserts [tagText] into [parent] right before [before], dropping any namespace the factory added. */
    fun addChildTagBefore(project: Project, parent: XmlTag, before: XmlTag, tagText: String): XmlTag? {
        val newTag = XmlElementFactory.getInstance(project).createTagFromText(tagText)
        val inserted = parent.addBefore(newTag, before) as? XmlTag ?: return null
        inserted.getAttribute("xmlns")?.delete()
        return inserted
    }

    /** Inserts [tagText] into [parent] right after [after], dropping any namespace the factory added. */
    fun addChildTagAfter(project: Project, parent: XmlTag, after: XmlTag, tagText: String): XmlTag? {
        val newTag = XmlElementFactory.getInstance(project).createTagFromText(tagText)
        val inserted = parent.addAfter(newTag, after) as? XmlTag ?: return null
        inserted.getAttribute("xmlns")?.delete()
        return inserted
    }

    /**
     * Inserts [tagText] into [parent] after the first present tag from [anchors]. Without an anchor
     * the tag is appended, or prepended when [appendWhenNoAnchor] is false.
     */
    fun insertChild(
        project: Project,
        parent: XmlTag,
        tagText: String,
        anchors: List<String>,
        appendWhenNoAnchor: Boolean = true
    ): XmlTag? {
        val anchor = MavenProperties.anchorName(parent.subTags.map { it.name }, anchors)
            ?.let { parent.findFirstSubTag(it) }
        if (anchor != null) return addChildTagAfter(project, parent, anchor, tagText)

        val newTag = XmlElementFactory.getInstance(project).createTagFromText(tagText)
        val inserted = parent.addSubTag(newTag, !appendWhenNoAnchor)
        inserted.getAttribute("xmlns")?.delete()
        return inserted
    }

    /** Returns the direct child of [parent] named [tagName], inserting an empty one as [insertChild] does. */
    fun findOrCreateChild(
        project: Project,
        parent: XmlTag,
        tagName: String,
        anchors: List<String>,
        appendWhenNoAnchor: Boolean = true
    ): XmlTag? {
        parent.findFirstSubTag(tagName)?.let { return expandIfSelfClosed(project, it, tagName) }
        return insertChild(project, parent, "<$tagName>\n</$tagName>", anchors, appendWhenNoAnchor)
    }

    /** Deletes [tag] together with the whitespace in front of it, so that no blank line is left behind. */
    fun deleteTag(tag: XmlTag) {
        val previous = tag.prevSibling
        if (previous is PsiWhiteSpace || (previous is XmlText && previous.text.isBlank())) {
            previous.delete()
        }
        tag.delete()
    }

    /** The `<build><plugins>` entry of [xmlFile] whose groupId and artifactId [matches] accepts. */
    fun findBuildPlugin(xmlFile: XmlFile, matches: (String?, String?) -> Boolean): XmlTag? {
        val plugins = xmlFile.rootTag
            ?.findFirstSubTag(MavenKotlinPlugin.BUILD_TAG)
            ?.findFirstSubTag(MavenKotlinPlugin.PLUGINS_TAG)
            ?: return null
        return findPluginIn(plugins, matches)
    }

    /** The `<plugin>` among [plugins] whose groupId and artifactId [matches] accepts. */
    fun findPluginIn(plugins: XmlTag, matches: (String?, String?) -> Boolean): XmlTag? =
        plugins.findSubTags(MavenKotlinPlugin.PLUGIN_TAG).firstOrNull {
            matches(
                childText(it, MavenKotlinPlugin.GROUP_ID_TAG),
                childText(it, MavenKotlinPlugin.ARTIFACT_ID_TAG)
            )
        }

    /** The goals an `<execution>` lists. */
    fun goalsOf(execution: XmlTag): List<String> =
        execution.findFirstSubTag(MavenKotlinPlugin.GOALS_TAG)
            ?.findSubTags(MavenKotlinPlugin.GOAL_TAG)
            .orEmpty()
            .map { it.value.trimmedText }

    /** The trimmed text of the direct child of [tag] named [childName]. */
    fun childText(tag: XmlTag, childName: String): String? =
        tag.findFirstSubTag(childName)?.value?.trimmedText

    /**
     * Moves the caret into [tag]'s value: to the end of it, or between the tags when it is empty.
     * The document must already be unblocked.
     */
    fun moveCaretIntoTag(editor: Editor, tag: XmlTag) {
        // The editor may be gone by now when the edit followed a background lookup
        if (editor.isDisposed || !tag.isValid) return
        val offset = tag.textRange.startOffset + MavenProperties.caretOffsetInTagText(tag.text)
        editor.selectionModel.removeSelection()
        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * Runs [edit] on [xmlFile], flushes the PSI changes into [document] and returns what [edit]
     * returned. Must be called inside a write command.
     *
     * Flushing makes the platform format the inserted tags — and, with each, the whitespace right
     * after it, which re-indents a comment that follows an inserted tag, typically commented-out XML
     * that Ctrl+/ put at column 0. Comments that start a line therefore get their original
     * indentation back, so that nothing outside the inserted tags changes.
     */
    fun <T> editKeepingComments(project: Project, xmlFile: XmlFile, document: Document, edit: () -> T): T {
        val documentManager = PsiDocumentManager.getInstance(project)
        // Make the PSI tree match what the user sees before reading it
        documentManager.commitDocument(document)
        val indents = commentIndents(xmlFile, document)

        val result = edit()

        // PSI modifications block the document; offsets are only usable once it is unblocked
        documentManager.doPostponedOperationsAndUnblockDocument(document)
        restoreCommentIndents(xmlFile, document, indents)
        documentManager.commitDocument(document)
        return result
    }

    /**
     * Runs [edit] on the `kotlin-maven-plugin` of [xmlFile] as a write command named [commandName],
     * and moves the caret into the tag [edit] returns. Unlike the other helpers, it opens the write
     * command itself. Returns `false`, changing nothing, when the pom does not declare that plugin.
     */
    fun editKotlinPlugin(
        project: Project,
        editor: Editor,
        xmlFile: XmlFile,
        commandName: String,
        edit: (XmlTag) -> XmlTag?
    ): Boolean {
        if (!xmlFile.isValid) return true

        var pluginMissing = false
        WriteCommandAction.runWriteCommandAction(project, commandName, null, {
            val target = editKeepingComments(project, xmlFile, editor.document) {
                val plugin = findBuildPlugin(xmlFile, MavenKotlinPlugin::isKotlinMavenPlugin)
                if (plugin == null) {
                    pluginMissing = true
                    return@editKeepingComments null
                }
                // Formatting the inserted tags may reparse them, so keep hold of the caret target through a pointer
                edit(plugin)?.let { SmartPointerManager.createPointer(it) }
            }
            target?.element?.let { moveCaretIntoTag(editor, it) }
        }, xmlFile)
        return !pluginMissing
    }

    /**
     * The indentation of each comment that starts a line, by the comment's text: text rather than
     * PSI identity, because moving a tag copies the comments inside it.
     */
    private fun commentIndents(xmlFile: XmlFile, document: Document): Map<String, String> =
        PsiTreeUtil.findChildrenOfType(xmlFile, XmlComment::class.java)
            .mapNotNull { comment -> lineIndent(document, comment.textRange.startOffset)?.let { comment.text to it } }
            .groupBy({ it.first }, { it.second })
            // The same text indented differently in several places cannot be told apart afterwards
            .filterValues { it.distinct().size == 1 }
            .mapValues { it.value.first() }

    private fun restoreCommentIndents(xmlFile: XmlFile, document: Document, indents: Map<String, String>) {
        if (indents.isEmpty()) return
        val shifted = PsiTreeUtil.findChildrenOfType(xmlFile, XmlComment::class.java).mapNotNull { comment ->
            val original = indents[comment.text] ?: return@mapNotNull null
            val start = comment.textRange.startOffset
            val current = lineIndent(document, start) ?: return@mapNotNull null
            if (current == original) null else Triple(start - current.length, start, original)
        }
        // From the end of the file, so that the offsets still to be used stay valid
        for ((from, to, indent) in shifted.sortedByDescending { it.first }) {
            document.replaceString(from, to, indent)
        }
    }

    /** The whitespace between the start of the line and [offset], or null when [offset] does not start the line's content. */
    private fun lineIndent(document: Document, offset: Int): String? {
        val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
        return document.charsSequence.subSequence(lineStart, offset).toString().takeIf { it.isBlank() }
    }

    /**
     * Puts a blank line above [tag] unless there already is one. Returns the re-resolved tag,
     * looked up as the root section named [tagName].
     */
    fun separateWithBlankLine(project: Project, tag: XmlTag, tagName: String): XmlTag {
        val documentManager = PsiDocumentManager.getInstance(project)
        val file = tag.containingFile ?: return tag
        val document = documentManager.getDocument(file) ?: return tag
        // Offsets are only meaningful once the PSI changes are flushed into the document
        documentManager.doPostponedOperationsAndUnblockDocument(document)

        val line = document.getLineNumber(tag.textRange.startOffset)
        if (line == 0) return tag
        val previousLine = document.getText(
            TextRange(document.getLineStartOffset(line - 1), document.getLineEndOffset(line - 1))
        )
        if (previousLine.isBlank()) return tag

        document.insertString(document.getLineStartOffset(line), "\n")
        documentManager.commitDocument(document)
        return (file as? XmlFile)?.rootTag?.findFirstSubTag(tagName) ?: tag
    }
}
