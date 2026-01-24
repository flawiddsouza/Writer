package com.flawiddsouza.writer

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.flawiddsouza.writer.sync.ApiClient
import com.flawiddsouza.writer.sync.ConflictStorage
import com.flawiddsouza.writer.sync.SyncEncryptionHelper
import com.flawiddsouza.writer.sync.SyncEngine
import com.flawiddsouza.writer.sync.SyncRepository
import com.flawiddsouza.writer.sync.SyncWorker
import com.flawiddsouza.writer.sync.WriterSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncSettingsActivity : AppCompatActivity() {

    private lateinit var serverUrlInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var signOutButton: Button
    private lateinit var syncNowButton: Button
    private lateinit var statusText: TextView
    private lateinit var accountText: TextView
    private lateinit var syncFrequencyGroup: RadioGroup
    private lateinit var wifiOnlyCheckbox: CheckBox

    private lateinit var syncEngine: SyncEngine
    private lateinit var repository: SyncRepository
    private var isLoggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_settings)

        // Initialize ConflictStorage
        ConflictStorage.initialize(this)

        // Initialize SyncEncryptionHelper
        SyncEncryptionHelper.initialize(this)

        // Initialize repository and sync engine
        repository = WriterSyncRepository(this)
        syncEngine = SyncEngine(this, repository)

        // Initialize views
        serverUrlInput = findViewById(R.id.server_url_input)
        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        signOutButton = findViewById(R.id.sign_out_button)
        syncNowButton = findViewById(R.id.sync_now_button)
        statusText = findViewById(R.id.status_text)
        accountText = findViewById(R.id.account_text)
        syncFrequencyGroup = findViewById(R.id.sync_frequency_group)
        wifiOnlyCheckbox = findViewById(R.id.wifi_only_checkbox)

        // Load saved settings
        loadSettings()
        updateUI()

        // Set click listeners
        loginButton.setOnClickListener { handleLogin() }
        signOutButton.setOnClickListener { handleSignOut() }
        syncNowButton.setOnClickListener { handleSyncNow() }

        // Listen to sync frequency changes
        syncFrequencyGroup.setOnCheckedChangeListener { _, _ -> updateSyncSchedule() }
        wifiOnlyCheckbox.setOnCheckedChangeListener { _, _ -> updateSyncSchedule() }
    }

    private fun loadSettings() {
        val serverUrl = syncEngine.getServerUrl()
        val email = syncEngine.getUserEmail()

        serverUrlInput.setText(serverUrl ?: "https://")

        if (email != null) {
            emailInput.setText(email)
            accountText.text = "Account: $email"
            isLoggedIn = true
        }

        // Load sync preferences - using default values
        findViewById<RadioButton>(R.id.sync_15_min).isChecked = true
        wifiOnlyCheckbox.isChecked = false
    }

    private fun updateUI() {
        if (isLoggedIn) {
            emailInput.isEnabled = false
            passwordInput.isEnabled = false
            serverUrlInput.isEnabled = false
            loginButton.visibility = View.GONE
            signOutButton.visibility = View.VISIBLE
            syncNowButton.isEnabled = true
            accountText.visibility = View.VISIBLE

            val lastSync = repository.getLastSyncTimestamp()
            statusText.text = "Last sync: $lastSync"
        } else {
            emailInput.isEnabled = true
            passwordInput.isEnabled = true
            serverUrlInput.isEnabled = true
            loginButton.visibility = View.VISIBLE
            signOutButton.visibility = View.GONE
            syncNowButton.isEnabled = false
            accountText.visibility = View.GONE
            statusText.text = "Not configured"
        }
    }

    private fun handleLogin() {
        val serverUrl = serverUrlInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (serverUrl.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading
        loginButton.isEnabled = false
        loginButton.text = "Logging in..."

        // Initialize API client
        ApiClient.initialize(serverUrl)

        // Perform login in background
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                ApiClient.login(email, password)
            }

            result.fold(
                onSuccess = { response ->
                    // Save credentials
                    syncEngine.setServerUrl(serverUrl)
                    syncEngine.setUserEmail(email)
                    syncEngine.setUserToken(response.token)

                    isLoggedIn = true
                    Toast.makeText(this@SyncSettingsActivity, "Login successful!", Toast.LENGTH_SHORT).show()

                    // Check encryption setup status
                    setupEncryptionIfNeeded()

                    updateUI()
                },
                onFailure = { exception ->
                    val errorMsg = exception.message ?: "Login failed"
                    Toast.makeText(this@SyncSettingsActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            )

            loginButton.isEnabled = true
            loginButton.text = "Login / Register"
        }
    }

    private fun setupEncryptionIfNeeded() {
        CoroutineScope(Dispatchers.Main).launch {
            // Check if we have master key locally
            if (SyncEncryptionHelper.hasMasterKey()) {
                // Already set up
                schedulePeriodicSync()
                return@launch
            }

            // Check if server has encrypted master key
            val masterKeyResult = withContext(Dispatchers.IO) {
                ApiClient.getMasterKey()
            }

            masterKeyResult.fold(
                onSuccess = { response ->
                    if (response.exists && response.encryptedMasterKey != null) {
                        // Server has key - prompt user to enter password to unlock
                        showUnlockMasterKeyDialog(response.encryptedMasterKey)
                    } else {
                        // First device - create new master key
                        showCreateEncryptionPasswordDialog()
                    }
                },
                onFailure = { error ->
                    Toast.makeText(this@SyncSettingsActivity, "Failed to check encryption status: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun showCreateEncryptionPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_encryption_password, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.password_input)
        val confirmPasswordInput = dialogView.findViewById<EditText>(R.id.confirm_password_input)
        val strengthText = dialogView.findViewById<TextView>(R.id.password_strength_text)

        // Real-time password strength check
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val pwd = s?.toString()?.toCharArray() ?: charArrayOf()
                val strength = SyncEncryptionHelper.getPasswordStrength(pwd)
                strengthText.text = when (strength) {
                    0 -> "⚠️ Very weak"
                    1 -> "⚠️ Weak"
                    2 -> "⚠️ Fair"
                    3 -> "✓ Good"
                    4 -> "✓✓ Very strong"
                    else -> ""
                }
                strengthText.setTextColor(when (strength) {
                    0, 1 -> 0xFFFF0000.toInt()
                    2 -> 0xFFFFA500.toInt()
                    else -> 0xFF00AA00.toInt()
                })
                pwd.fill('0')
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        AlertDialog.Builder(this)
            .setTitle("Create Encryption Password")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val password = passwordInput.text.toString().toCharArray()
                val confirmPassword = confirmPasswordInput.text.toString().toCharArray()

                if (!password.contentEquals(confirmPassword)) {
                    Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show()
                    password.fill('0')
                    confirmPassword.fill('0')
                    showCreateEncryptionPasswordDialog()
                    return@setPositiveButton
                }

                val (valid, message) = SyncEncryptionHelper.validatePassword(password)
                if (!valid) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    password.fill('0')
                    confirmPassword.fill('0')
                    showCreateEncryptionPasswordDialog()
                    return@setPositiveButton
                }

                setupMasterKey(password)

                password.fill('0')
                confirmPassword.fill('0')
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupMasterKey(password: CharArray) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // Generate random master key
                    val masterKeyBytes = SyncEncryptionHelper.generateMasterKey()

                    // Encrypt master key with user's password
                    val encryptedMasterKey = SyncEncryptionHelper.encryptMasterKeyWithPassword(masterKeyBytes, password)

                    // Upload to server
                    ApiClient.uploadMasterKey(encryptedMasterKey)

                    // Save master key locally
                    SyncEncryptionHelper.saveMasterKey(masterKeyBytes)

                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    password.fill('0')
                }
            }

            result.fold(
                onSuccess = {
                    Toast.makeText(this@SyncSettingsActivity, "Encryption setup complete!", Toast.LENGTH_SHORT).show()
                    schedulePeriodicSync()
                },
                onFailure = { error ->
                    Toast.makeText(this@SyncSettingsActivity, "Failed to setup encryption: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun showUnlockMasterKeyDialog(encryptedMasterKey: String) {
        val passwordInput = EditText(this).apply {
            hint = "Enter encryption password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val container = FrameLayout(this).apply {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val dpi = resources.displayMetrics.density
            params.leftMargin = (20 * dpi).toInt()
            params.rightMargin = (20 * dpi).toInt()
            passwordInput.layoutParams = params
            addView(passwordInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Unlock Encryption")
            .setMessage("Enter your encryption password to sync on this device")
            .setView(container)
            .setPositiveButton("Unlock") { _, _ ->
                val password = passwordInput.text.toString().toCharArray()
                unlockMasterKey(encryptedMasterKey, password)
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun unlockMasterKey(encryptedMasterKey: String, password: CharArray) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // Decrypt master key with user's password
                    val masterKeyBytes = SyncEncryptionHelper.decryptMasterKeyWithPassword(encryptedMasterKey, password)

                    // Save master key locally
                    SyncEncryptionHelper.saveMasterKey(masterKeyBytes)

                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    password.fill('0')
                }
            }

            result.fold(
                onSuccess = {
                    Toast.makeText(this@SyncSettingsActivity, "Encryption unlocked successfully!", Toast.LENGTH_SHORT).show()
                    schedulePeriodicSync()
                },
                onFailure = { error ->
                    Toast.makeText(this@SyncSettingsActivity, "Wrong password or decryption failed", Toast.LENGTH_LONG).show()
                    // Show dialog again
                    showUnlockMasterKeyDialog(encryptedMasterKey)
                }
            )
        }
    }

    private fun schedulePeriodicSync() {
        updateSyncSchedule()
        // Trigger initial sync
        SyncWorker.triggerImmediateSync(this@SyncSettingsActivity)
    }

    private fun handleSignOut() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out? Your local data will remain intact.")
            .setPositiveButton("Sign Out") { _, _ ->
                // Cancel sync worker
                SyncWorker.cancelPeriodicSync(this)

                // Clear sync state and encryption
                syncEngine.clearSyncState()
                SyncEncryptionHelper.clearAll()

                isLoggedIn = false
                emailInput.setText("")
                passwordInput.setText("")
                accountText.text = ""

                updateUI()

                Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleSyncNow() {
        syncNowButton.isEnabled = false
        syncNowButton.text = "Syncing..."
        statusText.text = "Syncing..."

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                syncEngine.performSync()
            }

            if (result.success) {
                statusText.text = "Synced ${result.entriesSynced} notes, ${result.categoriesSynced} categories. ${result.conflictsDetected} conflicts."

                if (result.conflictsDetected > 0) {
                    Toast.makeText(
                        this@SyncSettingsActivity,
                        "${result.conflictsDetected} conflicts detected. Please resolve them.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@SyncSettingsActivity, "Sync completed successfully!", Toast.LENGTH_SHORT).show()
                }
            } else {
                statusText.text = "Sync failed: ${result.error}"
                Toast.makeText(this@SyncSettingsActivity, "Sync failed: ${result.error}", Toast.LENGTH_LONG).show()
            }

            syncNowButton.isEnabled = true
            syncNowButton.text = "Sync Now"
        }
    }

    private fun updateSyncSchedule() {
        if (!isLoggedIn) {
            return
        }

        val intervalMinutes = when (syncFrequencyGroup.checkedRadioButtonId) {
            R.id.sync_manual -> {
                // Cancel periodic sync for manual only
                SyncWorker.cancelPeriodicSync(this)
                return
            }
            R.id.sync_15_min -> 15L
            R.id.sync_1_hour -> 60L
            R.id.sync_6_hours -> 360L
            else -> return
        }

        val wifiOnly = wifiOnlyCheckbox.isChecked

        SyncWorker.schedulePeriodic(this, intervalMinutes, wifiOnly)
    }
}
