package au.id.deejay.ideaworktrees.actions

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComboBox

class CompareWorktreesDialogTest : BasePlatformTestCase() {

    fun testValidationRequiresDistinctSelections() {
        val projectPath = Paths.get(requireNotNull(project.basePath))
        val worktrees = listOf(
            WorktreeInfo(projectPath, "main", "1111111", isMain = true),
            WorktreeInfo(projectPath.resolveSibling(projectPath.fileName.toString() + "-feature"), "feature/branch", "2222222")
        )

        val dialogClass = Class.forName("au.id.deejay.ideaworktrees.actions.CompareWorktreesDialog")
        val constructor = dialogClass.getDeclaredConstructor(
            com.intellij.openapi.project.Project::class.java,
            List::class.java
        )
        constructor.isAccessible = true

        val dialogRef = AtomicReference<DialogWrapper>()
        ApplicationManager.getApplication().invokeAndWait {
            @Suppress("UNCHECKED_CAST")
            dialogRef.set(constructor.newInstance(project, worktrees) as DialogWrapper)
        }

        val dialog = dialogRef.get()
        val validationMethod = dialogClass.getDeclaredMethod("doValidate").apply {
            isAccessible = true
        }

        try {
            val initialSelection = AtomicReference<Pair<WorktreeInfo, WorktreeInfo>>()
            ApplicationManager.getApplication().invokeAndWait {
                @Suppress("UNCHECKED_CAST")
                initialSelection.set(dialogClass.getDeclaredMethod("getSelection").invoke(dialog) as Pair<WorktreeInfo, WorktreeInfo>)
            }
            val selection = initialSelection.get()
            assertEquals(worktrees[0], selection.first)
            assertEquals(worktrees[1], selection.second)

            val initialValidation = AtomicReference<ValidationInfo?>()
            ApplicationManager.getApplication().invokeAndWait {
                initialValidation.set(validationMethod.invoke(dialog) as ValidationInfo?)
            }
            assertNull(initialValidation.get())

            val targetComboField = dialogClass.getDeclaredField("targetCombo").apply {
                isAccessible = true
            }
            val targetCombo = targetComboField.get(dialog) as JComboBox<*>
            ApplicationManager.getApplication().invokeAndWait {
                @Suppress("UNCHECKED_CAST")
                (targetCombo as JComboBox<WorktreeInfo>).selectedIndex = 0
            }

            val invalidValidation = AtomicReference<ValidationInfo?>()
            ApplicationManager.getApplication().invokeAndWait {
                invalidValidation.set(validationMethod.invoke(dialog) as ValidationInfo?)
            }
            val validationInfo = invalidValidation.get()
            assertNotNull(validationInfo)
            assertTrue(validationInfo!!.message.contains("Select two different worktrees"))

            ApplicationManager.getApplication().invokeAndWait {
                @Suppress("UNCHECKED_CAST")
                (targetCombo as JComboBox<WorktreeInfo>).selectedIndex = 1
            }

            val validAgain = AtomicReference<ValidationInfo?>()
            ApplicationManager.getApplication().invokeAndWait {
                validAgain.set(validationMethod.invoke(dialog) as ValidationInfo?)
            }
            assertNull(validAgain.get())
        } finally {
            val disposable = dialog.disposable
            ApplicationManager.getApplication().invokeAndWait {
                dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
            }
            Disposer.dispose(disposable)
        }
    }
}
