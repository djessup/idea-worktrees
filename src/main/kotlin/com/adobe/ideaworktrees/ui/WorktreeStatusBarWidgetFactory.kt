package com.adobe.ideaworktrees.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/**
 * Factory for creating the Git Worktree status bar widget.
 */
class WorktreeStatusBarWidgetFactory : StatusBarWidgetFactory {

    companion object {
        const val ID = "com.adobe.ideaworktrees.WorktreeStatusBarWidgetFactory"
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = "Git Worktree"

    override fun isAvailable(project: Project): Boolean {
        // Widget is available for all projects
        // The widget itself will hide if the project is not a Git repository
        return true
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return WorktreeStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        // Disposal is handled by the widget itself
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }
}

