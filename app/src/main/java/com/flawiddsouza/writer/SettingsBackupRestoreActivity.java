package com.flawiddsouza.writer;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsBackupRestoreActivity extends AppCompatActivity {

    private String DB_PATH = null;
    private final String BACKUP_PATH = Environment.getExternalStorageDirectory() + "/Writer.db";
    private static final String TAG = "BackupRestore";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_backup_restore);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getResources().getString(R.string.backup_restore_heading));

        DB_PATH = getApplicationInfo().dataDir + "/databases/Writer";

        setLastLocalBackupDate();
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

    public void copy(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

    private void setLastLocalBackupDate() {
        /* uncomment when you want to reenable local backup section in the backup page */
        /*File file = new File(BACKUP_PATH);
        TextView lastLocalBackupDate = (TextView) findViewById(R.id.last_local_backup_date);
        if (file.exists()) {
            Date lastModDate = new Date(file.lastModified());
            lastLocalBackupDate.setText(lastModDate.toString());
        } else {
            lastLocalBackupDate.setText("Never");
        }*/
    }

    public void localBackup(View view) {
        File f = new File(DB_PATH);
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(f);
            fos = new FileOutputStream(BACKUP_PATH);
            while (true) {
                int i = fis.read();
                if (i != -1) {
                    fos.write(i);
                } else {
                    break;
                }
            }
            fos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
                fis.close();
                Toast.makeText(this, "Saved to " + BACKUP_PATH, Toast.LENGTH_SHORT).show();
                setLastLocalBackupDate();
            } catch (Exception e) {
                Log.d(TAG, e.getLocalizedMessage());
                Toast.makeText(this, "Failed to create local backup!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void localRestore(View view) {
        new AlertDialog.Builder(this)
                .setMessage("Restoring this backup will remove all entries created before the backup! Are you sure you want to continue?")
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                    File backupFile = new File(BACKUP_PATH);
                    if (backupFile.exists()) {
                        try {
                            copy(backupFile, new File(DB_PATH));
                        } catch (Exception e) {
                            Log.d(TAG, e.getLocalizedMessage());
                            Toast.makeText(SettingsBackupRestoreActivity.this, "Failed to restore local backup!", Toast.LENGTH_SHORT).show();
                        } finally {
                            Toast.makeText(SettingsBackupRestoreActivity.this, "Local backup restored!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(SettingsBackupRestoreActivity.this, "Local backup not found!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    public void manualBackupExport(View view) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String dateStr = dateFormat.format(new Date());
        String fileName = "WriterBackup_" + dateStr + ".db";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        startActivityForResult(intent, 102);
    }

    public void manualBackupRestore(View view) {
        new AlertDialog.Builder(this)
        .setMessage("Select a backup file for restoration. This will remove all entries created before the backup! Are you sure you want to continue?")
        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); // Use appropriate MIME type as per your need

            startActivityForResult(intent, 101);
        })
        .setNegativeButton(android.R.string.no, null)
        .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handling the user's file selection for backup export
        if (requestCode == 102 && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    File src = new File(DB_PATH);
                    copy(src, uri);
                    Toast.makeText(this, "Backup exported successfully!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.d(TAG, "Failed to export backup: " + e.getMessage());
                    Toast.makeText(this, "Failed to export backup!", Toast.LENGTH_SHORT).show();
                }
            }
        }
        // Handling the user's file selection for backup restore
        else if (requestCode == 101 && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    File dst = new File(DB_PATH);
                    copy(uri, dst);
                    Toast.makeText(this, "Backup restored successfully!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.d(TAG, "Failed to restore backup: " + e.getMessage());
                    Toast.makeText(this, "Failed to restore backup!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void copy(File src, Uri dstUri) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = getContentResolver().openOutputStream(dstUri)) {
            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    public void copy(Uri srcUri, File dst) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(srcUri);
             OutputStream out = new FileOutputStream(dst)) {
            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}