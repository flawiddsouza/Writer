package com.flawiddsouza.writer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {

    WriterDatabaseHandler handler;
    EntryCursorAdapter entryCursorAdapter;

    private boolean searchClicked = false;

    private CategoryCursorAdapter categoryCursorAdapter;

    private long activeCategory = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = WriterDatabaseHandler.getInstance(this); // init handler

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ListView mainListView = findViewById(R.id.mainListView);
        entryCursorAdapter = new EntryCursorAdapter(this, createEntriesCursor());
        mainListView.setAdapter(entryCursorAdapter);

        mainListView.setTextFilterEnabled(true);

        entryCursorAdapter.setFilterQueryProvider(searchQuery -> createEntriesCursorFiltered(searchQuery));

        mainListView.setOnItemClickListener((parent, view, position, id) -> editNote(id));
        registerForContextMenu(mainListView); // Register the ListView for Context menu

        // Categories Drawer
        ListView drawerListView = findViewById(R.id.navList);
        drawerListView.setOnItemClickListener((parent, view, position, id) -> changeCategory(id));
        registerForContextMenu(drawerListView);
        categoryCursorAdapter = new CategoryCursorAdapter(this, createCategoriesCursor());
        drawerListView.setAdapter(categoryCursorAdapter);

        DrawerLayout mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                //Called when a drawer's position changes.
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                findViewById(R.id.drawer_holder).bringToFront();
                mDrawerLayout.requestLayout();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                // Called when a drawer has settled in a completely closed state.
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                // Called when the drawer motion state changes. The new state will be one of STATE_IDLE, STATE_DRAGGING or STATE_SETTLING.
            }
        });
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
                    entryCursorAdapter.getFilter().filter(editText.getText().toString());
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

            entryCursorAdapter.swapCursor(createEntriesCursor()); // reset adapter to unfiltered query

            getSupportActionBar().setDisplayShowCustomEnabled(false);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        entryCursorAdapter.swapCursor(createEntriesCursor());
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, view, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if(view.getId() == R.id.navList) {
            if(info.id != -1) { // only if the long pressed category isn't `Main`
                menu.add(0, view.getId(), 0, "Rename");
                menu.add(0, view.getId(), 0, "Delete");
            }
        } else if(view.getId() == R.id.mainListView) {
            menu.add(1, view.getId(), 0, "Details");
            menu.add(1, view.getId(), 0, "Copy"); // groupId, itemId, order, title
            menu.add(1, view.getId(), 0, "Share");
            menu.add(1, view.getId(), 0, "Delete");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item){
        AdapterView.AdapterContextMenuInfo activeListItem = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if(item.getGroupId() == 0) {
            if (item.getTitle() == "Rename") {
                renameCategory(activeListItem.id);
            } else if (item.getTitle() == "Delete") {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Deleting a category will also delete all the notes under it! Do you really want to do this?")
                        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                            handler.deleteCategory(activeListItem.id);
                            categoryCursorAdapter.swapCursor(createCategoriesCursor());
                            if(activeCategory == activeListItem.id) { // if the deleted category is the active category, then
                                activeCategory = -1;
                                entryCursorAdapter.swapCursor(createEntriesCursor());
                            }
                            Toast.makeText(MainActivity.this, "Category Deleted", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
            }
        } else if(item.getGroupId() == 1) {
            if (item.getTitle() == "Details") {
                Entry thisEntry = handler.getEntry(activeListItem.id);
                SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MMM-yy hh:mm a");
                new AlertDialog.Builder(this)
                        .setMessage("Created on: " + DATE_FORMAT.format(thisEntry.createdAt) + '\n' + "Updated on: " + DATE_FORMAT.format(thisEntry.updatedAt))
                        .show();
            } else if (item.getTitle() == "Copy") {
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
            } else if (item.getTitle() == "Share") {
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
            } else if (item.getTitle() == "Delete") {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Do you really want to delete this?")
                        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                            handler.deleteEntry(activeListItem.id);
                            entryCursorAdapter.swapCursor(createEntriesCursor());
                            Toast.makeText(MainActivity.this, "Note Deleted", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
            }
        }
        return false;
    }

    public void createNote(View view) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("edit", false);
        intent.putExtra("activeCategory", activeCategory);
        startActivity(intent);
    }

    public void editNote(long id) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("edit", true);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    public Cursor createEntriesCursor() {
        SQLiteDatabase db = handler.getReadableDatabase();
        if (activeCategory == -1) {
            return db.rawQuery("SELECT * FROM entries WHERE category_id IS NULL ORDER BY updated_at DESC", null);
        } else {
            return db.rawQuery("SELECT * FROM entries WHERE category_id = ? ORDER BY updated_at DESC", new String[]{ Long.toString(activeCategory) });
        }
    }

    public Cursor createEntriesCursorFiltered(CharSequence searchString) {
        SQLiteDatabase db = handler.getReadableDatabase();
        if (activeCategory == -1) {
            return db.rawQuery("SELECT * FROM (SELECT * FROM entries WHERE category_id IS NULL) WHERE title LIKE ? OR body LIKE ? ORDER BY updated_at DESC", new String[]{ '%' + searchString.toString() + '%', '%' + searchString.toString() + '%' });
        } else {
            return db.rawQuery("SELECT * FROM (SELECT * FROM entries WHERE category_id = ?) WHERE title LIKE ? OR body LIKE ? ORDER BY updated_at DESC", new String[]{ Long.toString(activeCategory), '%' + searchString.toString() + '%', '%' + searchString.toString() + '%' });
        }
    }

    public Cursor createCategoriesCursor() {
        SQLiteDatabase db = handler.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM categories ORDER BY created_at ASC", null);
        MatrixCursor matrixCursor = new MatrixCursor(new String[] { "_id", "name" });
        matrixCursor.addRow(new Object[] { -1, "Main" });
        MergeCursor mergeCursor = new MergeCursor(new Cursor[] { matrixCursor, cursor });
        return mergeCursor;
    }

    public void addCategory(View view) {
        EditText txtBox = new EditText(this);
        txtBox.setSingleLine();
        // we use a FrameLayout to add left and right margin to the EditText
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        float dpi = getApplicationContext().getResources().getDisplayMetrics().density;
        params.leftMargin = (int) (20 * dpi);
        params.rightMargin = (int) (20 * dpi);
        txtBox.setLayoutParams(params);
        container.addView(txtBox);
        AlertDialog inputDialog = new AlertDialog.Builder(this)
                .setTitle("Enter Category Name")
                .setView(container)
                .setPositiveButton("Add", (dialog, whichButton) -> {
                    String name = txtBox.getText().toString();
                    Category newCategory = new Category();
                    newCategory.name = txtBox.getText().toString();
                    handler.addCategory(newCategory);
                    categoryCursorAdapter.swapCursor(createCategoriesCursor());
                })
                .setNegativeButton("Cancel", (dialog, whichButton) -> dialog.dismiss())
                .create();
        inputDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        inputDialog.show();
    }

    public void renameCategory(long id) {
        EditText txtBox = new EditText(this);
        txtBox.setSingleLine();
        String categoryName = handler.getCategoryName(id);
        txtBox.setText(categoryName);
        // we use a FrameLayout to add left and right margin to the EditText
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        float dpi = getApplicationContext().getResources().getDisplayMetrics().density;
        params.leftMargin = (int) (20 * dpi);
        params.rightMargin = (int) (20 * dpi);
        txtBox.setLayoutParams(params);
        container.addView(txtBox);
        AlertDialog inputDialog = new AlertDialog.Builder(this)
                .setTitle("Rename Category")
                .setView(container)
                .setPositiveButton("Rename", (dialog, whichButton) -> {
                    String name = txtBox.getText().toString();
                    Category renamedCategory = new Category();
                    renamedCategory.name = txtBox.getText().toString();
                    handler.updateCategory(id, renamedCategory);
                    categoryCursorAdapter.swapCursor(createCategoriesCursor());
                })
                .setNegativeButton("Cancel", (dialog, whichButton) -> dialog.dismiss())
                .create();
        inputDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        inputDialog.show();
    }

    private void changeCategory(long id) {
        activeCategory = id;
        entryCursorAdapter.swapCursor(createEntriesCursor());
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawers();
    }
}
