package com.flawiddsouza.writer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBar.LayoutParams;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

public class EditorActivity extends AppCompatActivity {

    private EditText title;
    private EditText editText;
    private boolean edit;
    private long activeCategory;
    private long id;
    WriterDatabaseHandler handler;
    Entry thisEntry;

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
        handler = WriterDatabaseHandler.getInstance(this);

        Bundle bundle = getIntent().getExtras();
        edit = bundle.getBoolean("edit");
        activeCategory = bundle.getLong("activeCategory");

        if(edit) {
            id = bundle.getLong("id");
            thisEntry = handler.getEntry(id);
            title.setText(thisEntry.title);
            editText.setText(thisEntry.body);
            editText.setSelection(editText.getText().length()); // Place cursor at the end of text
        }

        // Privacy Mode
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(preferences.getBoolean("Privacy_Mode_Boolean", false)) {
            editText.setTextColor(preferences.getInt("Privacy_Mode_Color", R.color.black));
        }
    }

    @Override
    public void onBackPressed() {
        if(!edit) {
            Entry newEntry = new Entry();
            newEntry.title = title.getText().toString();
            newEntry.body = editText.getText().toString();
            newEntry.categoryId = activeCategory;
            handler.addEntry(newEntry);
        } else {
            Entry updatedEntry = new Entry();
            updatedEntry.title = title.getText().toString();
            updatedEntry.body = editText.getText().toString();
            if(!(thisEntry.title.equals(updatedEntry.title)) && !(thisEntry.body.equals(updatedEntry.body)) // if title and body both have changed
                    || !(thisEntry.title.equals(updatedEntry.title)) && (thisEntry.body.equals(updatedEntry.body)) // if title has changed
                    || (thisEntry.title.equals(updatedEntry.title)) && !(thisEntry.body.equals(updatedEntry.body))) { // if body has changed
                handler.updateEntry(id, updatedEntry);
            }

            if(updatedEntry.title.isEmpty() && updatedEntry.body.isEmpty()) {
                handler.deleteEntry(id);
            }
        }

        finish();
        return;
    }
}
