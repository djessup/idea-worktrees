package au.id.deejay.ideaworktrees.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/**
 * Factory for creating the Git Worktree status bar widget.
 */
class WorktreeStatusBarWidgetFactory : StatusBarWidgetFactory {

    companion object {
        const val ID = "au.id.deejay.ideaworktrees.WorktreeStatusBarWidgetFactory"
    }

    /**
     * @return Unique identifier for the factory.
     */
    override fun getId(): String = ID

    /**
     * @return Display name used in the status bar settings UI.
     */
    override fun getDisplayName(): String = "Git Worktree"

    /**
     * Declares that the widget can be toggled on for any project.
     */
    override fun isAvailable(project: Project): Boolean {
        // Widget is available for all projects
        // The widget itself will hide if the project is not a Git repository
        return true
    }

    /**
     * Instantiates the status bar widget for the supplied project.
     */
    override fun createWidget(project: Project): StatusBarWidget {
        return WorktreeStatusBarWidget(project)
    }

    /**
     * Provides explicit hook for disposal, though the widget handles cleanup.
     */
    override fun disposeWidget(widget: StatusBarWidget) {
        // Disposal is handled by the widget itself
    }

    /**
     * Allows the widget to be enabled on any status bar configuration.
     */
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }
}
