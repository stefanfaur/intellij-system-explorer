package ro.faur.explorer.gitpanel.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JCheckBox
import javax.swing.JComponent

class CreateStashDialog(
    project: Project,
    defaultMessageHint: String
) : DialogWrapper(project, true) {

    private val messageField = JBTextField(40).apply {
        emptyText.text = defaultMessageHint
    }
    private val includeUntrackedBox = JCheckBox("Include untracked files").apply {
        isSelected = false
    }

    init {
        title = "Create Stash"
        isOKActionEnabled = true  // empty message is valid — git uses its default
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Message (optional):", messageField)
            .addComponent(includeUntrackedBox)
            .panel
    }

    override fun getPreferredFocusedComponent(): JComponent = messageField

    fun getMessage(): String? = messageField.text.trim().ifBlank { null }
    fun isIncludeUntracked(): Boolean = includeUntrackedBox.isSelected
}
