package ro.faur.explorer.gitpanel.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import ro.faur.explorer.gitpanel.BranchNameValidator
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CreateBranchDialog(project: Project) : DialogWrapper(project, true) {

    private val nameField = JBTextField(30)
    private val errorLabel = JBLabel("").apply {
        foreground = JBColor.RED
    }

    init {
        title = "Create New Branch"
        isOKActionEnabled = false
        init()
        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onTextChanged()
            override fun removeUpdate(e: DocumentEvent) = onTextChanged()
            override fun changedUpdate(e: DocumentEvent) = onTextChanged()
        })
    }

    private fun onTextChanged() {
        val text = nameField.text.trim()
        val error = BranchNameValidator.validate(text)
        errorLabel.text = error ?: ""
        isOKActionEnabled = error == null && nameField.text.isNotBlank()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Branch name:", nameField)
            .addComponent(errorLabel)
            .panel
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    fun getBranchName(): String = nameField.text.trim()
}
