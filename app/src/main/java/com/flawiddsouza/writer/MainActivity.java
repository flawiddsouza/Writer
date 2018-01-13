package com.flawiddsouza.writer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {

    WriterCursorAdapter writerAdapter;
    WriterDatabaseHandler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = WriterDatabaseHandler.getInstance(this); // init handler

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ListView myListView = (ListView) findViewById(R.id.mainListView);
        writerAdapter = new WriterCursorAdapter(this, createCursor());
        myListView.setAdapter(writerAdapter);

        myListView.setOnItemClickListener((parent, view, position, id) -> editNote(id));
        registerForContextMenu(myListView); // Register the ListView for Context menu
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
}
