package com.flawiddsouza.writer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

public class MainActivity extends AppCompatActivity {

    WriterCursorAdapter writerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ListView myListView = (ListView) findViewById(R.id.mainListView);
        writerAdapter = new WriterCursorAdapter(this, createCursor());
        myListView.setAdapter(writerAdapter);

        myListView.setOnItemClickListener((parent, view, position, id) -> editNote(view, id));

        myListView.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage("Do you really want to delete this?")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            WriterDatabaseHandler handler = WriterDatabaseHandler.getInstance(getApplicationContext());
                            handler.deleteEntry(id);
                            writerAdapter.swapCursor(createCursor());
                        }})
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            return true; // prevent further processing of the click event
        });
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

    public void createNote(View view) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("edit", false);
        startActivity(intent);
    }

    public void editNote(View view, long id) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("edit", true);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    public Cursor createCursor() {
        WriterDatabaseHandler handler = WriterDatabaseHandler.getInstance(this);
        SQLiteDatabase db = handler.getReadableDatabase();
        return db.rawQuery("SELECT * FROM entries ORDER BY updated_at DESC", null);
    }
}
