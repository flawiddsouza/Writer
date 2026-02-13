package com.flawiddsouza.writer;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsPrivacyModeActivity extends AppCompatActivity {

    private TextView privacyModeTextSample;
    private android.view.View previewScanlinesOverlay;
    private android.view.View previewChromaticOverlay;
    private SeekBar opacityMeter;
    private SeekBar shadowMeter;
    private SeekBar scanlinesMeter;
    private SeekBar chromaticMeter;
    private SharedPreferences preferences;
    private CheckBox opacityCheckbox;
    private CheckBox shadowOnlyCheckbox;
    private CheckBox scanlinesCheckbox;
    private CheckBox chromaticCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_privacy_mode);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getResources().getString(R.string.privacy_mode_heading));

        CheckBox privacyModeCheckbox = (CheckBox) findViewById(R.id.privacy_mode_checkbox);
        opacityCheckbox = (CheckBox) findViewById(R.id.privacy_mode_opacity);
        shadowOnlyCheckbox = (CheckBox) findViewById(R.id.privacy_mode_shadow_only);
        scanlinesCheckbox = (CheckBox) findViewById(R.id.privacy_mode_scanlines);
        chromaticCheckbox = (CheckBox) findViewById(R.id.privacy_mode_chromatic);
        opacityMeter = (SeekBar) findViewById(R.id.opacity_meter);
        shadowMeter = (SeekBar) findViewById(R.id.shadow_meter);
        scanlinesMeter = (SeekBar) findViewById(R.id.scanlines_meter);
        chromaticMeter = (SeekBar) findViewById(R.id.chromatic_meter);
        privacyModeTextSample = (TextView) findViewById(R.id.privacy_mode_text_sample);
        previewScanlinesOverlay = findViewById(R.id.preview_scanlines_overlay);
        previewChromaticOverlay = findViewById(R.id.preview_chromatic_overlay);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Load saved preferences
        privacyModeCheckbox.setChecked(preferences.getBoolean("Privacy_Mode_Boolean", false));
        opacityCheckbox.setChecked(preferences.getBoolean("Privacy_Mode_Opacity_Enabled", false));
        shadowOnlyCheckbox.setChecked(preferences.getBoolean("Privacy_Mode_Shadow_Enabled", false));
        scanlinesCheckbox.setChecked(preferences.getBoolean("Privacy_Mode_Scanlines_Enabled", false));
        chromaticCheckbox.setChecked(preferences.getBoolean("Privacy_Mode_Chromatic_Enabled", false));
        opacityMeter.setProgress(preferences.getInt("Privacy_Mode_Opacity_Value", 128));
        shadowMeter.setProgress(preferences.getInt("Privacy_Mode_Shadow_Value", 128));
        scanlinesMeter.setProgress(preferences.getInt("Privacy_Mode_Scanlines_Value", 50));
        chromaticMeter.setProgress(preferences.getInt("Privacy_Mode_Chromatic_Value", 30));

        // Update preview with saved settings
        updatePreview();

        // Main enable/disable checkbox listener
        privacyModeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("Privacy_Mode_Boolean", isChecked);
                editor.apply();
            }
        });

        // Opacity checkbox listener
        opacityCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("Privacy_Mode_Opacity_Enabled", isChecked);
                editor.apply();
                updatePreview();
            }
        });

        // Shadow only checkbox listener
        shadowOnlyCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("Privacy_Mode_Shadow_Enabled", isChecked);
                editor.apply();
                updatePreview();
            }
        });

        // Opacity SeekBar listener
        opacityMeter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("Privacy_Mode_Opacity_Value", progress);
                editor.apply();
                updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Shadow SeekBar listener
        shadowMeter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("Privacy_Mode_Shadow_Value", progress);
                editor.apply();
                updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Scanlines checkbox listener
        scanlinesCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("Privacy_Mode_Scanlines_Enabled", isChecked);
                editor.apply();
                updatePreview();
            }
        });

        // Scanlines SeekBar listener
        scanlinesMeter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("Privacy_Mode_Scanlines_Value", progress);
                editor.apply();
                updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Chromatic aberration checkbox listener
        chromaticCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("Privacy_Mode_Chromatic_Enabled", isChecked);
                editor.apply();
                updatePreview();
            }
        });

        // Chromatic aberration SeekBar listener
        chromaticMeter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("Privacy_Mode_Chromatic_Value", progress);
                editor.apply();
                updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void updatePreview() {
        int opacityIntensity = opacityMeter.getProgress();
        int shadowIntensity = shadowMeter.getProgress();
        int scanlinesIntensity = scanlinesMeter.getProgress();
        int chromaticIntensity = chromaticMeter.getProgress();
        boolean opacityEnabled = opacityCheckbox.isChecked();
        boolean shadowEnabled = shadowOnlyCheckbox.isChecked();
        boolean scanlinesEnabled = scanlinesCheckbox.isChecked();
        boolean chromaticEnabled = chromaticCheckbox.isChecked();

        // Reset to defaults first
        privacyModeTextSample.setTextColor(Color.BLACK);
        privacyModeTextSample.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        previewScanlinesOverlay.setVisibility(android.view.View.GONE);
        previewChromaticOverlay.setVisibility(android.view.View.GONE);

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

            privacyModeTextSample.setShadowLayer(
                shadowRadius,
                shadowOffset,
                shadowOffset,
                Color.argb(shadowAlpha, 0, 0, 0)
            );
        }

        privacyModeTextSample.setTextColor(Color.argb(textAlpha, 0, 0, 0));

        // Apply scanlines if enabled
        if (scanlinesEnabled) {
            ScanlinesDrawable scanlinesDrawable = new ScanlinesDrawable(scanlinesIntensity);
            previewScanlinesOverlay.setBackground(scanlinesDrawable);
            previewScanlinesOverlay.setVisibility(android.view.View.VISIBLE);
        }

        // Apply chromatic aberration if enabled
        if (chromaticEnabled) {
            ChromaticAberrationDrawable chromaticDrawable = new ChromaticAberrationDrawable(chromaticIntensity);
            previewChromaticOverlay.setBackground(chromaticDrawable);
            previewChromaticOverlay.setVisibility(android.view.View.VISIBLE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
