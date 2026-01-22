package com.flawiddsouza.writer

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Dialog for setting up encryption password.
 * Shows password and confirm password fields with validation.
 */
class PasswordSetupDialog @JvmOverloads constructor(
    private val context: Context,
    private val onPasswordSet: (CharArray) -> Unit,
    private val onCancel: (() -> Unit)? = null,
    private val title: String = "Set Encryption Password",
    private val message: String = "Enter a password to encrypt this note. You'll need this password to view the note later."
) {
    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        builder.setMessage(message)

        // Create layout
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        // Password field
        val passwordLabel = TextView(context)
        passwordLabel.text = "Password:"
        layout.addView(passwordLabel)

        val passwordInput = EditText(context)
        passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordInput.hint = "Enter password"
        layout.addView(passwordInput)

        // Confirm password field
        val confirmLabel = TextView(context)
        confirmLabel.text = "Confirm Password:"
        confirmLabel.setPadding(0, 20, 0, 0)
        layout.addView(confirmLabel)

        val confirmInput = EditText(context)
        confirmInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        confirmInput.hint = "Re-enter password"
        layout.addView(confirmInput)

        builder.setView(layout)

        builder.setPositiveButton("Set Password") { dialog, _ ->
            val password = passwordInput.text.toString()
            val confirm = confirmInput.text.toString()

            // Validate password
            if (password.length < 6) {
                Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                // Clear fields
                passwordInput.text.clear()
                confirmInput.text.clear()
                return@setPositiveButton
            }

            if (password != confirm) {
                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                // Clear fields
                passwordInput.text.clear()
                confirmInput.text.clear()
                return@setPositiveButton
            }

            // Password is valid
            val passwordChars = password.toCharArray()

            // Clear text fields
            passwordInput.text.clear()
            confirmInput.text.clear()

            // Call callback
            onPasswordSet(passwordChars)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            passwordInput.text.clear()
            confirmInput.text.clear()
            onCancel?.invoke()
            dialog.cancel()
        }

        builder.setOnCancelListener {
            onCancel?.invoke()
        }

        builder.setCancelable(true)
        val dialog = builder.create()

        // Show keyboard automatically when dialog appears
        dialog.setOnShowListener {
            passwordInput.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(passwordInput, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.show()
    }
}
