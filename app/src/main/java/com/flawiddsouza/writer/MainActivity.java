package com.flawiddsouza.writer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {

    WriterCursorAdapter writerAdapter;
    WriterDatabaseHandler handler;
    private boolean searchClicked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = WriterDatabaseHandler.getInstance(this); // init handler

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ListView myListView = findViewById(R.id.mainListView);
        writerAdapter = new WriterCursorAdapter(this, createCursor());
        myListView.setAdapter(writerAdapter);

        myListView.setTextFilterEnabled(true);

        writerAdapter.setFilterQueryProvider(searchQuery -> createCursorFiltered(searchQuery));

        myListView.setOnItemClickListener((parent, view, position, id) -> editNote(id));
        registerForContextMenu(myListView); // Register the ListView for Context menu
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem search = menu.findItem(R.id.action_search);
        MenuItem cancelSearch = menu.findItem(R.id.action_cancel_search);

        if (searchClicked) {
            search.setVisible(false);
            cancelSearch.setVisible(true);
        } else {
            cancelSearch.setVisible(false);
            search.setVisible(true);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        if(id == R.id.action_search) {
            searchClicked = true;
            invalidateOptionsMenu();

            getSupportActionBar().setDisplayShowTitleEnabled(false);

            EditText editText = new EditText(MainActivity.this);

            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//                    Toast.makeText(MainActivity.this, editText.getText().toString(), Toast.LENGTH_LONG).show();
                    writerAdapter.getFilter().filter(editText.getText().toString());
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });

            editText.setTextColor(Color.WHITE);
            editText.setBackgroundColor(Color.TRANSPARENT);

            getSupportActionBar().setCustomView(editText);
            getSupportActionBar().setDisplayShowCustomEnabled(true);

            editText.getLayoutParams().width = LinearLayout.LayoutParams.MATCH_PARENT;

            editText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);

            return true;
        }

        if(id == R.id.action_cancel_search) {
            searchClicked = false;
            invalidateOptionsMenu();

            // close soft keyboard
            View view = this.getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }

            writerAdapter.swapCursor(createCursor()); // reset adapter to unfiltered query

            getSupportActionBar().setDisplayShowCustomEnabled(false);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        writerAdapter.swapCursor(createCursor());
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, view, menuInfo);
        menu.add(0, view.getId(), 0, "Details");
        menu.add(0, view.getId(), 0, "Copy"); // groupId, itemId, order, title
        menu.add(0, view.getId(), 0, "Share");
        menu.add(0, view.getId(), 0, "Delete");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item){
        AdapterView.AdapterContextMenuInfo activeListItem = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if(item.getTitle() == "Details"){
            Entry thisEntry = handler.getEntry(activeListItem.id);
            SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MMM-yy hh:mm a");
            new AlertDialog.Builder(this)
                    .setMessage("Created on: " + DATE_FORMAT.format(thisEntry.createdAt) + '\n' + "Updated on: " + DATE_FORMAT.format(thisEntry.updatedAt))
                    .show();
        }
        else if(item.getTitle() == "Copy"){
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            Entry thisEntry = handler.getEntry(activeListItem.id);
            ClipData clip;
            if (!thisEntry.title.isEmpty()) {
                clip = ClipData.newPlainText("A Note", thisEntry.title + '\n' + thisEntry.body);
            } else {
                clip = ClipData.newPlainText("A Note", thisEntry.body);
            }
            clipboard.setPrimaryClip(clip);
            Toast.makeText(MainActivity.this, "Note Copied", Toast.LENGTH_SHORT).show();
        }
        else if(item.getTitle() == "Share") {
            Entry thisEntry = handler.getEntry(activeListItem.id);
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            if (!thisEntry.title.isEmpty()) {
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, thisEntry.title);
                sendIntent.putExtra(Intent.EXTRA_TEXT, thisEntry.body);
            } else {
                sendIntent.putExtra(Intent.EXTRA_TEXT, thisEntry.body);
            }
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "Share Note"));
        }
        else if(item.getTitle() == "Delete"){
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage("Do you really want to delete this?")
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                        handler.deleteEntry(activeListItem.id);
                        writerAdapter.swapCursor(createCursor());
                        Toast.makeText(MainActivity.this, "Note Deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
        } else{
            return false;
        }
        return true;
    }

    public void createNote(View view) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("edit", false);
        startActivity(intent);
    }

    public void editNote(long id) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("edit", true);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    public Cursor createCursor() {
        SQLiteDatabase db = handler.getReadableDatabase();
        return db.rawQuery("SELECT * FROM entries ORDER BY updated_at DESC", null);
    }

    public Cursor createCursorFiltered(CharSequence searchString) {
        SQLiteDatabase db = handler.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM entries WHERE title LIKE ? OR body LIKE ? ORDER BY updated_at DESC", new String[]{'%' + searchString.toString() + '%', '%' + searchString.toString() + '%'});
        return cursor;
    }
}
