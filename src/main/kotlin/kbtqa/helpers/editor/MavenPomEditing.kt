package kbtqa.helpers.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * XML PSI helpers shared by the actions that edit a `pom.xml`.
 *
 * All of them must be called inside a write action, on a committed document.
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
        val created = if (anchor != null) {
            root.addAfter(emptySection, anchor) as? XmlTag
        } else {
            root.addSubTag(emptySection, true)
        } ?: return null

        // The new tag's indentation lives in whitespace owned by <project>, so reformat the root
        val rootPointer = SmartPointerManager.createPointer(root)
        CodeStyleManager.getInstance(project).reformat(root)
        val section = rootPointer.element?.findFirstSubTag(tagName) ?: created

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

    /** Reformats [tag] and returns it re-resolved, since reformatting may reparse the file. */
    fun reformat(project: Project, tag: XmlTag): XmlTag? {
        val pointer = SmartPointerManager.createPointer(tag)
        CodeStyleManager.getInstance(project).reformat(tag)
        return pointer.element
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
