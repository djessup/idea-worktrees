package au.id.deejay.ideaworktrees.actions

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Renderer that displays worktree display name and path within combo boxes.
 */
internal class WorktreeComboBoxRenderer : ListCellRenderer<WorktreeInfo> {
    private val label = JBLabel()

    /**
     * Formats the combo box cell with the worktree metadata.
     */
    override fun getListCellRendererComponent(
        list: JList<out WorktreeInfo>,
        value: WorktreeInfo?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        // Combine display name and path for clear disambiguation.
        label.text = value?.let { "${it.displayName} — ${it.path}" } ?: ""
        label.border = JBUI.Borders.empty(2, 6)
        if (isSelected) {
            label.background = list.selectionBackground
            label.foreground = list.selectionForeground
        } else {
            label.background = list.background
            label.foreground = list.foreground
        }
        label.isOpaque = true
        return label
    }
}
