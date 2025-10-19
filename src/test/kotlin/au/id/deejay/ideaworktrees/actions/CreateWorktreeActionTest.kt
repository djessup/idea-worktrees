package au.id.deejay.ideaworktrees.actions

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.*
import java.io.File

/**
 * Tests for CreateWorktreeAction dialog validation logic.
 */
class CreateWorktreeActionTest : BasePlatformTestCase() {

    fun testValidationRejectsEmptyBranchName() {
        val dialog = createDialog()

        // Set empty branch name
        setBranchName(dialog, "")
        setWorktreePath(dialog, "/valid/path")

        val validationInfo = invokeDoValidate(dialog)
        assertNotNull("Should have validation error for empty branch name", validationInfo)
        assertTrue(validationInfo!!.message.contains("Branch name cannot be empty"))
    }

    fun testValidationRejectsEmptyPath() {
        val dialog = createDialog()

        // Set valid branch but empty path
        setBranchName(dialog, "feature/test")
        setWorktreePath(dialog, "")

        val validationInfo = invokeDoValidate(dialog)
        assertNotNull("Should have validation error for empty path", validationInfo)
        assertTrue(validationInfo!!.message.contains("Worktree path cannot be empty"))
    }

    fun testValidationRejectsPathTooLong() {
        val dialog = createDialog()

        // Create a path longer than 260 characters
        val longPath = "/very/long/path/" + "a".repeat(300)

        setBranchName(dialog, "feature/test")
        setWorktreePath(dialog, longPath)

        val validationInfo = invokeDoValidate(dialog)
        assertNotNull("Should have validation error for path too long", validationInfo)
        assertTrue(validationInfo!!.message.contains("Path is too long"))
        assertTrue(validationInfo.message.contains("260"))
    }

    fun testValidationRejectsWindowsReservedNames() {
        val dialog = createDialog()
        setBranchName(dialog, "feature/test")

        // Test various Windows reserved names
        val reservedNames = listOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )

        for (reservedName in reservedNames) {
            // Test exact match
            setWorktreePath(dialog, "/parent/$reservedName")
            var validationInfo = invokeDoValidate(dialog)
            assertNotNull("Should reject reserved name: $reservedName", validationInfo)
            assertTrue(validationInfo!!.message.contains("reserved Windows filename"))

            // Test lowercase
            setWorktreePath(dialog, "/parent/${reservedName.lowercase()}")
            validationInfo = invokeDoValidate(dialog)
            assertNotNull("Should reject reserved name (lowercase): ${reservedName.lowercase()}", validationInfo)

            // Test with extension
            setWorktreePath(dialog, "/parent/$reservedName.txt")
            validationInfo = invokeDoValidate(dialog)
            assertNotNull("Should reject reserved name with extension: $reservedName.txt", validationInfo)
        }
    }

    fun testValidationAcceptsValidPath() {
        val dialog = createDialog()

        setBranchName(dialog, "feature/test")
        setWorktreePath(dialog, "/valid/path/to/worktree")

        val validationInfo = invokeDoValidate(dialog)
        assertNull("Should accept valid path", validationInfo)
    }

    fun testValidationAcceptsPathWithReservedNameAsSubstring() {
        val dialog = createDialog()

        setBranchName(dialog, "feature/test")

        // These should be accepted because the reserved name is just a substring
        setWorktreePath(dialog, "/parent/CONSOLE")
        assertNull("Should accept CONSOLE (not CON)", invokeDoValidate(dialog))

        setWorktreePath(dialog, "/parent/PRINTER")
        assertNull("Should accept PRINTER (not PRN)", invokeDoValidate(dialog))

        setWorktreePath(dialog, "/parent/COM10")
        assertNull("Should accept COM10 (not COM1-9)", invokeDoValidate(dialog))
    }

    fun testValidationAcceptsLongButValidPath() {
        val dialog = createDialog()

        // Create a path that's exactly 260 characters (at the limit)
        val pathAt260 = "/parent/" + "a".repeat(260 - 8)

        setBranchName(dialog, "feature/test")
        setWorktreePath(dialog, pathAt260)

        assertEquals("Path should be exactly 260 chars", 260, pathAt260.length)
        val validationInfo = invokeDoValidate(dialog)
        assertNull("Should accept path at exactly 260 characters", validationInfo)
    }

    // Helper methods to access private dialog fields via reflection

    private fun createDialog(): Any {
        // Use reflection to create the private CreateWorktreeDialog class
        val dialogClass = Class.forName("au.id.deejay.ideaworktrees.actions.CreateWorktreeDialog")
        val constructor = dialogClass.getDeclaredConstructor(
            com.intellij.openapi.project.Project::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(project)
    }

    private fun invokeDoValidate(dialog: Any): ValidationInfo? {
        val method = dialog.javaClass.getDeclaredMethod("doValidate")
        method.isAccessible = true
        return method.invoke(dialog) as? ValidationInfo
    }

    private fun setBranchName(dialog: Any, value: String) {
        val field = dialog.javaClass.getDeclaredField("branchNameField")
        field.isAccessible = true
        val textField = field.get(dialog) as javax.swing.JTextField
        textField.text = value
    }

    private fun setWorktreePath(dialog: Any, value: String) {
        val field = dialog.javaClass.getDeclaredField("pathField")
        field.isAccessible = true
        val pathField = field.get(dialog) as com.intellij.openapi.ui.TextFieldWithBrowseButton
        pathField.text = value
    }
}

