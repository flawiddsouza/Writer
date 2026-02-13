package com.flawiddsouza.writer;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBar.LayoutParams;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import android.view.View;
import android.widget.EditText;
import android.widget.ToggleButton;
import android.widget.Toast;

public class EditorActivity extends AppCompatActivity {

    private EditText title;
    private EditText editText;
    private ToggleButton lockToggle;
    private android.view.View scanlinesOverlay;
    private android.view.View chromaticOverlay;
    private boolean edit;
    private long activeCategory;
    private long id;
    WriterDatabaseHandler handler;
    Entry thisEntry;
    private boolean isEncrypted;
    private char[] encryptionPassword;
    private String originalBodyText;
    private boolean isProgrammaticToggleChange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        ActionBar actionBar = getSupportActionBar();
        View view = getLayoutInflater().inflate(R.layout.custom_action_bar, null);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT); // Why? See: http://stackoverflow.com/a/23399652/4932305
        actionBar.setCustomView(view, layoutParams);

        title = (EditText) findViewById(R.id.actionBarTitle);
        editText = (EditText) findViewById(R.id.editText);
        lockToggle = (ToggleButton) findViewById(R.id.lockToggle);
        scanlinesOverlay = findViewById(R.id.scanlines_overlay);
        chromaticOverlay = findViewById(R.id.chromatic_overlay);
        handler = WriterDatabaseHandler.getInstance(this);

        Bundle bundle = getIntent().getExtras();
        edit = bundle.getBoolean("edit");
        activeCategory = bundle.getLong("activeCategory");

        if(edit) {
            id = bundle.getLong("id");
            thisEntry = handler.getEntry(id);
            title.setText(thisEntry.title);

            // Handle encrypted notes
            isEncrypted = thisEntry.isEncrypted;
            if(isEncrypted) {
                // Get password from session or bundle for THIS note
                encryptionPassword = CryptoManager.INSTANCE.getSessionPassword(id);
                if(encryptionPassword == null) {
                    encryptionPassword = bundle.getCharArray("password");
                }

                // Get pre-decrypted body from MainActivity (no need to decrypt again)
                String decryptedBody = bundle.getString("decryptedBody");
                if(decryptedBody != null) {
                    editText.setText(decryptedBody);
                    originalBodyText = decryptedBody;
                } else {
                    // Fallback: shouldn't happen for encrypted notes
                    Toast.makeText(this, "Failed to open encrypted note", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                lockToggle.setChecked(true);
            } else {
                editText.setText(thisEntry.body);
                originalBodyText = thisEntry.body; // Store original plaintext
                lockToggle.setChecked(false);
            }

            editText.setSelection(editText.getText().length()); // Place cursor at the end of text
        } else {
            isEncrypted = false;
            lockToggle.setChecked(false);
            originalBodyText = ""; // Initialize for new notes
        }

        // Set up lock toggle click listener (for password change)
        lockToggle.setOnClickListener(v -> {
            if(lockToggle.isChecked() && isEncrypted) {
                // Already locked and encrypted - change password (don't unlock)
                new PasswordSetupDialog(EditorActivity.this, newPassword -> {
                    // Clear old password and set new one
                    if(encryptionPassword != null) {
                        CryptoManager.INSTANCE.clearPassword(encryptionPassword);
                    }
                    encryptionPassword = newPassword;
                    Toast.makeText(EditorActivity.this, "Password changed. Note will be re-encrypted on save", Toast.LENGTH_SHORT).show();
                    return kotlin.Unit.INSTANCE;
                }, () -> {
                    // User cancelled - do nothing
                    return kotlin.Unit.INSTANCE;
                }, "Change Encryption Password", "Enter new password for this note").show();
            }
        });

        // Set up lock toggle change listener (for setting password when unlocked)
        lockToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Ignore programmatic toggle changes
            if(isProgrammaticToggleChange) {
                return;
            }

            if(isChecked && !isEncrypted) {
                // User toggled to locked on unencrypted note - set password
                new PasswordSetupDialog(EditorActivity.this, password -> {
                    encryptionPassword = password;
                    isEncrypted = true;
                    Toast.makeText(EditorActivity.this, "Note will be encrypted on save", Toast.LENGTH_SHORT).show();
                    return kotlin.Unit.INSTANCE;
                }, () -> {
                    // User cancelled - uncheck the toggle
                    isProgrammaticToggleChange = true;
                    lockToggle.setChecked(false);
                    isProgrammaticToggleChange = false;
                    return kotlin.Unit.INSTANCE;
                }).show();
            } else if(!isChecked && isEncrypted) {
                // User tried to unlock an encrypted note - block it and restore lock
                isProgrammaticToggleChange = true;
                lockToggle.setChecked(true);
                isProgrammaticToggleChange = false;
            }
        });

        // Set up long press to remove encryption (only works when encrypted)
        lockToggle.setOnLongClickListener(v -> {
            if(lockToggle.isChecked() && isEncrypted) {
                // Show confirmation dialog to remove encryption
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Remove Encryption?")
                    .setMessage("Are you sure you want to remove encryption from this note? It will be saved as plain text.")
                    .setPositiveButton("Remove Encryption", (dialog, which) -> {
                        isEncrypted = false;
                        if(encryptionPassword != null) {
                            CryptoManager.INSTANCE.clearPassword(encryptionPassword);
                        }
                        encryptionPassword = null;
                        isProgrammaticToggleChange = true;
                        lockToggle.setChecked(false);
                        isProgrammaticToggleChange = false;
                        // Different message for new vs existing encrypted notes
                        String toastMsg = (edit && thisEntry.isEncrypted)
                            ? "Encryption will be removed on save"
                            : "Encryption removed";
                        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return true; // Consume the long click event
            }
            return false; // Don't consume if not encrypted
        });


        // Privacy Mode
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(preferences.getBoolean("Privacy_Mode_Boolean", false)) {
            boolean opacityEnabled = preferences.getBoolean("Privacy_Mode_Opacity_Enabled", false);
            boolean shadowEnabled = preferences.getBoolean("Privacy_Mode_Shadow_Enabled", false);
            boolean scanlinesEnabled = preferences.getBoolean("Privacy_Mode_Scanlines_Enabled", false);
            boolean chromaticEnabled = preferences.getBoolean("Privacy_Mode_Chromatic_Enabled", false);
            int opacityIntensity = preferences.getInt("Privacy_Mode_Opacity_Value", 128);
            int shadowIntensity = preferences.getInt("Privacy_Mode_Shadow_Value", 128);
            int scanlinesIntensity = preferences.getInt("Privacy_Mode_Scanlines_Value", 50);
            int chromaticIntensity = preferences.getInt("Privacy_Mode_Chromatic_Value", 30);

            // Reset shadow first
            title.setShadowLayer(0, 0, 0, android.graphics.Color.TRANSPARENT);
            editText.setShadowLayer(0, 0, 0, android.graphics.Color.TRANSPARENT);

            // Calculate text and shadow alpha
            int textAlpha = 255;
            int shadowAlpha = 0;

            if (opacityEnabled && !shadowEnabled) {
                // Only opacity: reduce text transparency
                textAlpha = 255 - opacityIntensity;
            } else if (shadowEnabled && !opacityEnabled) {
                // Only shadow: transparent text with shadow
                textAlpha = 0;
                shadowAlpha = 60 + (int)(shadowIntensity / 2.8f); // 60-150 range
            } else if (opacityEnabled && shadowEnabled) {
                // Both: text is transparent, opacity controls shadow transparency
                textAlpha = 0;
                int baseShadowAlpha = 60 + (int)(shadowIntensity / 2.8f);
                shadowAlpha = (int)(baseShadowAlpha * (255 - opacityIntensity) / 255.0f);
            }

            // Apply shadow if enabled
            if (shadowEnabled && shadowAlpha > 0) {
                float shadowRadius = 2 + (shadowIntensity / 40f); // 2-8 range (more blur)
                float shadowOffset = 0.5f + (shadowIntensity / 200f); // 0.5-1.8 range (less offset)

                title.setShadowLayer(
                    shadowRadius,
                    shadowOffset,
                    shadowOffset,
                    android.graphics.Color.argb(shadowAlpha, 0, 0, 0)
                );
                editText.setShadowLayer(
                    shadowRadius,
                    shadowOffset,
                    shadowOffset,
                    android.graphics.Color.argb(shadowAlpha, 0, 0, 0)
                );
            }

            // Apply final text color
            title.setTextColor(android.graphics.Color.argb(textAlpha, 0, 0, 0));
            editText.setTextColor(android.graphics.Color.argb(textAlpha, 0, 0, 0));

            // Apply scanlines if enabled
            if (scanlinesEnabled) {
                ScanlinesDrawable scanlinesDrawable = new ScanlinesDrawable(scanlinesIntensity);
                scanlinesOverlay.setBackground(scanlinesDrawable);
                scanlinesOverlay.setVisibility(android.view.View.VISIBLE);
            } else {
                scanlinesOverlay.setVisibility(android.view.View.GONE);
            }

            // Apply chromatic aberration if enabled
            if (chromaticEnabled) {
                ChromaticAberrationDrawable chromaticDrawable = new ChromaticAberrationDrawable(chromaticIntensity);
                chromaticOverlay.setBackground(chromaticDrawable);
                chromaticOverlay.setVisibility(android.view.View.VISIBLE);
            } else {
                chromaticOverlay.setVisibility(android.view.View.GONE);
            }
        }

        // Handle back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                String entryTitle = title.getText().toString();
                String entryBody = editText.getText().toString();

                if(!edit) {
                    // Defensive check: encrypted note must have password
                    if(isEncrypted && encryptionPassword == null) {
                        Toast.makeText(EditorActivity.this, "Encryption error: password not set", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Entry newEntry = new Entry();
                    newEntry.title = entryTitle;
                    newEntry.isEncrypted = isEncrypted;

                    // Encrypt body if needed
                    if(isEncrypted && encryptionPassword != null) {
                        try {
                            newEntry.body = CryptoManager.INSTANCE.encrypt(entryBody, encryptionPassword);
                        } catch (Exception e) {
                            Toast.makeText(EditorActivity.this, "Encryption failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                    } else {
                        newEntry.body = entryBody;
                    }

                    newEntry.categoryId = activeCategory;
                    long newId = handler.addEntry(newEntry);

                    // Store session password for this new note if encrypted and save succeeded
                    if(newId != -1 && isEncrypted && encryptionPassword != null) {
                        CryptoManager.INSTANCE.setSessionPassword(newId, encryptionPassword);
                    }
                } else {
                    // Defensive check: encrypted note must have password
                    if(isEncrypted && encryptionPassword == null) {
                        Toast.makeText(EditorActivity.this, "Encryption error: password not set", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Entry updatedEntry = new Entry();
                    updatedEntry.title = entryTitle;
                    updatedEntry.isEncrypted = isEncrypted;

                    // Encrypt body if needed
                    if(isEncrypted && encryptionPassword != null) {
                        try {
                            // Reuse salt from original encrypted body if available (for instant re-encryption)
                            byte[] existingSalt = null;
                            if(thisEntry.isEncrypted && thisEntry.body != null) {
                                existingSalt = CryptoManager.INSTANCE.extractSalt(thisEntry.body);
                            }
                            updatedEntry.body = CryptoManager.INSTANCE.encrypt(entryBody, encryptionPassword, existingSalt);
                        } catch (Exception e) {
                            Toast.makeText(EditorActivity.this, "Encryption failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                    } else {
                        updatedEntry.body = entryBody;
                    }

                    // Check if anything has changed
                    boolean titleChanged = !thisEntry.title.equals(updatedEntry.title);
                    // Compare plaintext body, not encrypted strings (to avoid re-encryption false positives)
                    boolean bodyChanged = !originalBodyText.equals(entryBody);
                    boolean encryptionChanged = thisEntry.isEncrypted != updatedEntry.isEncrypted;

                    if(titleChanged || bodyChanged || encryptionChanged) {
                        handler.updateEntry(id, updatedEntry);
                    }

                    // Update session password for this note if encrypted
                    if(isEncrypted && encryptionPassword != null) {
                        CryptoManager.INSTANCE.setSessionPassword(id, encryptionPassword);
                    } else if(!isEncrypted) {
                        // Clear session if encryption was removed
                        CryptoManager.INSTANCE.clearSessionPassword(id);
                    }

                    if(updatedEntry.title.isEmpty() && entryBody.isEmpty()) {
                        handler.deleteEntry(id);
                        // Clear session password for deleted note
                        CryptoManager.INSTANCE.clearSessionPassword(id);
                    }
                }

                finish();
            }
        });
    }
}
