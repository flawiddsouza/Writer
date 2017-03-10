package com.flawiddsouza.writer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.content.ContentValues.TAG;

public class WriterDatabaseHandler extends SQLiteOpenHelper {

    private static WriterDatabaseHandler sInstance;

    // Database Info
    private static final String DATABASE_NAME = "Writer"; // (BuildConfig.DEBUG) ? "/sdcard/writer.db" : "Writer";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_ENTRIES = "entries";
    private static final String KEY_ENTRY_TITLE = "title";
    private static final String KEY_ENTRY_BODY = "body";

    public static synchronized WriterDatabaseHandler getInstance(Context context) {
        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            sInstance = new WriterDatabaseHandler(context.getApplicationContext());
        }
        return sInstance;
    }

    public WriterDatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Called when the database connection is being configured.
    // Configure database settings for things like foreign key support, write-ahead logging, etc.
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setLocale(Locale.getDefault());
    }

    // These is where we need to write create table statements.
    // This is called when database is created.
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL("CREATE TABLE entries ( _id INTEGER PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, created_at TIMESTAMP DEFAULT (datetime(CURRENT_TIMESTAMP, 'localtime')), updated_at TIMESTAMP DEFAULT (datetime(CURRENT_TIMESTAMP, 'localtime')) );");
            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
        }
    }

    // This method is called when database is upgraded like
    // modifying the table structure,
    // adding constraints to database, etc
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // SQL for upgrading the tables
    }

    // Insert a entry into the database
    public void addEntry(Entry entry) {
        if(!entry.title.isEmpty() || !entry.body.isEmpty()) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put(KEY_ENTRY_TITLE, entry.title);
                values.put(KEY_ENTRY_BODY, entry.body);
                db.insertOrThrow(TABLE_ENTRIES, null, values);
                db.setTransactionSuccessful();
            } catch (Exception e) {
                Log.d(TAG, "Error while trying to add entry to database");
            } finally {
                db.endTransaction();
            }
        }
    }

    // get entry for given id from database
    public Entry getEntry(long id) {
        Entry thisEntry = new Entry();
        SQLiteDatabase db = getReadableDatabase();
        try {
            Cursor cursor = db.rawQuery("SELECT * FROM entries WHERE _id=?", new String[]{Long.toString(id)});
            if(cursor.getCount() == 1) {
                cursor.moveToFirst(); // select first row
                thisEntry.title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ENTRY_TITLE));
                thisEntry.body = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ENTRY_BODY));
            }
        } catch (Exception e) {
            Log.e("cursor error", e.getLocalizedMessage());
        }
        return thisEntry;
    }

    public void updateEntry(long id, Entry entry) {
        if(!entry.title.isEmpty() || !entry.body.isEmpty()) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put(KEY_ENTRY_TITLE, entry.title);
                values.put(KEY_ENTRY_BODY, entry.body);
                values.put("updated_at", getDateTime());
                db.update(TABLE_ENTRIES, values, "_id=?", new String[] { Long.toString(id) });
                db.setTransactionSuccessful();
            } catch (Exception e) {
                Log.d(TAG, "Error while trying to update entry from database");
            } finally {
                db.endTransaction();
            }
        }
    }

    // Delete entry from the database
    public void deleteEntry(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_ENTRIES, "_id=?", new String[] { Long.toString(id) });
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to delete entry from database");
        } finally {
            db.endTransaction();
        }
    }

    public List<Entry> getEntries() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM entries ORDER BY updated_at DESC", null);
        List<Entry> list = new ArrayList<>();
        if (cursor.moveToFirst()) {
            while (cursor.isAfterLast() == false) {
                Entry entry = new Entry();
                entry.title = cursor.getString(cursor.getColumnIndex(KEY_ENTRY_TITLE));
                entry.body = cursor.getString(cursor.getColumnIndex(KEY_ENTRY_BODY));
                list.add(entry);
                cursor.moveToNext();
            }
        }
        return list;
    }

    private String getDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }
}