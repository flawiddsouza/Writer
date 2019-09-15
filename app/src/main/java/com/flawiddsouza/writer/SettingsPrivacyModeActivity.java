package com.flawiddsouza.writer;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsPrivacyModeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_privacy_mode);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getResources().getString(R.string.privacy_mode_heading));

        CheckBox privacyModeCheckbox = (CheckBox) findViewById(R.id.privacy_mode_checkbox);
        SeekBar privacyMeter = (SeekBar) findViewById(R.id.privacy_meter);
        TextView privacyModeTextSample = (TextView) findViewById(R.id.privacy_mode_text_sample);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        privacyMeter.setProgress(preferences.getInt("Privacy_Meter_Value", 0));
        privacyModeTextSample.setTextColor(preferences.getInt("Privacy_Mode_Color", R.color.black));
        privacyModeCheckbox.setChecked(preferences.getBoolean("Privacy_Mode_Boolean", false));

        privacyModeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("Privacy_Mode_Boolean", isChecked);
                editor.apply();
            }
        });

        privacyMeter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                privacyModeTextSample.setTextColor(Color.argb(255-progress,0,0,0)); // forward progress moves from 100% opacity to 0% opacity
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("Privacy_Meter_Value", progress);
                editor.putInt("Privacy_Mode_Color", Color.argb(255-progress,0,0,0));
                editor.apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // nothing here
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // nothing here either
            }
        });
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
