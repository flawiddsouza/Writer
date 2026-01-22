package com.flawiddsouza.writer

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Dialog for verifying encryption password.
 * Shows single password field with verification callback.
 */
class PasswordVerifyDialog @JvmOverloads constructor(
    private val context: Context,
    private val onPasswordEntered: (CharArray) -> Unit,
    private val onCancel: (() -> Unit)? = null,
    private val title: String = "Enter Password",
    private val message: String = "This note is encrypted. Enter your password to view it."
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

        builder.setView(layout)

        builder.setPositiveButton("Unlock") { dialog, _ ->
            val password = passwordInput.text.toString()
            val passwordChars = password.toCharArray()

            // Clear text field
            passwordInput.text.clear()

            // Call callback - verification happens in the caller
            onPasswordEntered(passwordChars)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            passwordInput.text.clear()
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
