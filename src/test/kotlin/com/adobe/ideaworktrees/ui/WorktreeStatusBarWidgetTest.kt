package com.adobe.ideaworktrees.ui

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

class WorktreeStatusBarWidgetTest : BasePlatformTestCase() {

    fun testFactoryIsAlwaysAvailable() {
        val factory = WorktreeStatusBarWidgetFactory()
        assertTrue(factory.isAvailable(project))
        assertEquals(WorktreeStatusBarWidgetFactory.ID, factory.id)
    }

    fun testWidgetHiddenWhenProjectNotGitRepository() {
        val factory = WorktreeStatusBarWidgetFactory()
        val widget = factory.createWidget(project) as WorktreeStatusBarWidget

        val method = WorktreeStatusBarWidget::class.java.getDeclaredMethod(
            "getWidgetState",
            VirtualFile::class.java
        )
        method.isAccessible = true
        val state = method.invoke(widget, *arrayOf<Any?>(null))
        val widgetStateClass = method.returnType
        val hiddenField = widgetStateClass.getField("HIDDEN")
        val hiddenValue = hiddenField.get(null)

        assertSame(hiddenValue, state)
    }

}
