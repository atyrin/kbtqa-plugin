package kbtqa.helpers.editor

import com.intellij.ui.TitledSeparator
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Renders a popup list whose items fall into groups: the group's title goes above the first item of
 * each group. Groups are told apart by the item above in the list as shown, so the titles stay in
 * place when speed search filters the list.
 */
internal class GroupedListRenderer<T>(
    private val label: (T) -> String,
    private val group: (T) -> String,
) : ListCellRenderer<T> {

    private val defaultRenderer = DefaultListCellRenderer()

    override fun getListCellRendererComponent(
        list: JList<out T>,
        value: T,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val base = defaultRenderer.getListCellRendererComponent(list, label(value), index, isSelected, cellHasFocus)
        // A negative index renders the value outside the list, where no title belongs
        if (index < 0) return base
        val previous = if (index > 0) list.model.getElementAt(index - 1) else null
        if (previous != null && group(previous) == group(value)) return base

        val panel = JPanel(BorderLayout())
        panel.add(TitledSeparator(group(value)), BorderLayout.NORTH)
        panel.add(base, BorderLayout.CENTER)
        panel.isOpaque = true
        panel.background = list.background
        return panel
    }
}
