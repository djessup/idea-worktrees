package com.adobe.ideaworktrees.actions

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import java.nio.file.Paths
import javax.swing.JList

class WorktreeComboBoxRendererTest : BasePlatformTestCase() {

    fun testRendererFormatsSelectionAndDefaults() {
        val renderer = WorktreeComboBoxRenderer()
        val basePath = Paths.get(requireNotNull(project.basePath))
        val worktree = WorktreeInfo(
            path = basePath.resolve("worktrees").resolve("feature"),
            branch = "feature/branch",
            commit = "0123456"
        )

        val list = JList(arrayOf(worktree))

        val selectedComponent = renderer.getListCellRendererComponent(
            list,
            worktree,
            0,
            true,
            true
        ) as JBLabel

        assertEquals("${worktree.displayName} — ${worktree.path}", selectedComponent.text)
        assertTrue(selectedComponent.isOpaque)
        assertEquals(list.selectionBackground, selectedComponent.background)
        assertEquals(list.selectionForeground, selectedComponent.foreground)

        val defaultComponent = renderer.getListCellRendererComponent(
            list,
            worktree,
            0,
            false,
            false
        ) as JBLabel

        assertEquals(list.background, defaultComponent.background)
        assertEquals(list.foreground, defaultComponent.foreground)
    }
}
