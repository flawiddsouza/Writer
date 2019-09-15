package com.flawiddsouza.writer;

import android.app.AlertDialog;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Date;

public class SettingsBackupRestoreActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    private String DB_PATH = null;
    private final String BACKUP_PATH = Environment.getExternalStorageDirectory() + "/Writer.db";
    private static final String TAG = "BackupRestore";
    private GoogleApiClient mGoogleApiClient;
    private DriveId mDriveId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_backup_restore);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getResources().getString(R.string.backup_restore_heading));

        DB_PATH = getApplicationInfo().dataDir + "/databases/Writer";

        setLastLocalBackupDate();
        setLastCloudBackupDateFromPreferences();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_APPFOLDER)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        mDriveId = preferences.getString("DB_backup_driveID", null) != null ? DriveId.decodeFromString(preferences.getString("DB_backup_driveID", null)) : null;
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

    @Override
    protected void onStart() {
        connectToGoogleAPI();
        super.onStart();
    }

    @Override
    protected void onResume() {
        connectToGoogleAPI();
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }

        try {
            result.startResolutionForResult(this, 3);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle connectionHint) {
        if(isOnline()) {
            if (mDriveId == null) { // this code is run only once per app install
                Drive.DriveApi.requestSync(mGoogleApiClient);
                Query query = new Query.Builder()
                        .addFilter(Filters.eq(SearchableField.TITLE, "Writer.db"))
                        .addFilter(Filters.eq(SearchableField.MIME_TYPE, "application/x-sqlite3"))
                        .build();
                queryDriveID(query);
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended:" + "HIT!");
        // connection terminated
    }

    private void queryDriveID(Query query) { // only used once, separated for code readability
        Drive.DriveApi.query(mGoogleApiClient, query)
                .setResultCallback(result -> {
                    if (!result.getStatus().isSuccess()) {
                        Log.d(TAG, "Problem while retrieving results");
                        return;
                    }
                    MetadataBuffer mdb = result.getMetadataBuffer();
                    Log.d(TAG, "Results found: " + mdb.getCount());
                    if (mdb.getCount() > 0) {
                        mDriveId = mdb.get(0).getDriveId();
                        Log.d(TAG, mDriveId.encodeToString());
                        setPreferences("DB_backup_driveID", mDriveId.encodeToString());
                    } else {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        queryDriveID(query);
                    }
                    mdb.release();
                    setLastCloudBackupDateFromCloud();
                });
    }

    private void setPreferences(String key, String value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, value);
        editor.apply();
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
    private void copyInputStreamToFile(InputStream in, File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setLastLocalBackupDate() {
        File file = new File(BACKUP_PATH);
        TextView lastLocalBackupDate = (TextView) findViewById(R.id.last_local_backup_date);
        if (file.exists()) {
            Date lastModDate = new Date(file.lastModified());
            lastLocalBackupDate.setText(lastModDate.toString());
        } else {
            lastLocalBackupDate.setText("Never");
        }
    }

    private void setLastCloudBackupDateFromPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String date = preferences.getString("Cloud_Backup_Date", null);
        TextView lastCloudBackupDate = (TextView) findViewById(R.id.last_cloud_backup_date);
        if (date != null){
            lastCloudBackupDate.setText(date);
        } else {
            lastCloudBackupDate.setText("Never");
        }
    }

    private void setLastCloudBackupDateFromCloud() {
        TextView lastCloudBackupDate = (TextView) findViewById(R.id.last_cloud_backup_date);
        if(mDriveId != null) {
            AsyncTask.execute(() -> {
                DriveFile file = mDriveId.asDriveFile();
                file.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, null).await();
                DriveResource.MetadataResult result = file.getMetadata(mGoogleApiClient).await();
                Metadata metadata = result.getMetadata();
                runOnUiThread(() -> {
                    setPreferences("Cloud_Backup_Date", metadata.getModifiedDate().toString());
                    lastCloudBackupDate.setText(metadata.getModifiedDate().toString());
                });
            });
        }
    }

    private boolean isOnline() {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
            int     exitValue = ipProcess.waitFor();
            return (exitValue == 0);
        } catch (IOException | InterruptedException e) { e.printStackTrace(); }
        return false;
    }

    private void connectToGoogleAPI() {
        if(isOnline() && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
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
            } catch (Exception e) {
                Log.d(TAG, e.getLocalizedMessage());
                Toast.makeText(this, "Failed to create local backup!", Toast.LENGTH_SHORT).show();
            } finally {
                Toast.makeText(this, "Saved to " + BACKUP_PATH, Toast.LENGTH_SHORT).show();
                setLastLocalBackupDate();
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

    public void cloudBackup(View view) {
        connectToGoogleAPI();
        if(isOnline()) {
            if(mGoogleApiClient.isConnected()) {
                if (mDriveId != null) { // this prevents NullPointerException when the mDriveId is null
                    DriveFile file = mDriveId.asDriveFile();
                    // delete DB backup if it exists
                    // not doing this will result in duplicate backup files of the same name being created
                    file.delete(mGoogleApiClient).setResultCallback(result -> {
                        Log.d(TAG, "" + result.getStatusMessage());
                    });
                }

                Drive.DriveApi.newDriveContents(mGoogleApiClient)
                        .setResultCallback(result -> {
                            // If the operation was not successful, we cannot do anything and must fail.
                            if (!result.getStatus().isSuccess()) {
                                return;
                            }

                            OutputStream outputStream = result.getDriveContents().getOutputStream();
                            if (outputStream != null) try {
                                InputStream is = new FileInputStream(DB_PATH);
                                byte[] buf = new byte[4096];
                                int c;
                                while ((c = is.read(buf, 0, buf.length)) > 0) {
                                    outputStream.write(buf, 0, c);
                                    outputStream.flush();
                                }
                                is.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                try {
                                    outputStream.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                    .setMimeType("application/x-sqlite3")
                                    .setTitle("Writer.db")
                                    .build();

                            Drive.DriveApi.getAppFolder(mGoogleApiClient)
                                    .createFile(mGoogleApiClient, metadataChangeSet, result.getDriveContents())
                                    .setResultCallback(result1 -> {
                                        if (!result1.getStatus().isSuccess()) {
                                            Log.d(TAG, result1.getStatus().getStatusMessage());
                                            Toast.makeText(this, "Cloud backup failed!", Toast.LENGTH_SHORT).show();
                                            return;
                                        }
                                        Log.d(TAG, "Created a file in App Folder: " + result1.getDriveFile().getDriveId());
                                        Toast.makeText(this, "Cloud backup created!", Toast.LENGTH_SHORT).show();

                                        mDriveId = result1.getDriveFile().getDriveId();
                                        setPreferences("DB_backup_driveID", mDriveId.encodeToString());
                                        setLastCloudBackupDateFromCloud();
                                    });
                        });
            } else {
                Toast.makeText(this, "Error! Couldn't connect to Google Drive.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "You're offline! Please connect to the internet.", Toast.LENGTH_SHORT).show();
        }
    }

    public void cloudRestore(View view) {
        connectToGoogleAPI();
        if(isOnline()) {
            if(mGoogleApiClient.isConnected()) {
                new AlertDialog.Builder(this)
                        .setMessage("Restoring this backup will remove all entries created before the backup! Are you sure you want to continue?")
                        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                            if (mDriveId != null) {
                                try {
                                    DriveFile file = mDriveId.asDriveFile();
                                    file.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, null)
                                            .setResultCallback(result -> {
                                                if (!result.getStatus().isSuccess()) {
                                                    Toast.makeText(SettingsBackupRestoreActivity.this, "Failed to restore cloud backup!", Toast.LENGTH_SHORT).show();
                                                    return;
                                                } else {
                                                    Toast.makeText(SettingsBackupRestoreActivity.this, "Cloud backup restored!", Toast.LENGTH_SHORT).show();
                                                }
                                                DriveContents contents = result.getDriveContents();
                                                copyInputStreamToFile(contents.getInputStream(), new File(DB_PATH));
                                                contents.discard(mGoogleApiClient);
                                            });
                                } catch (Exception e) {
                                    Log.d(TAG, e.getLocalizedMessage());
                                    Toast.makeText(SettingsBackupRestoreActivity.this, "Failed to restore cloud backup!", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(SettingsBackupRestoreActivity.this, "Cloud backup not found!", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
            } else {
                Toast.makeText(this, "Error! Couldn't connect to Google Drive.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "You're offline! Please connect to the internet.", Toast.LENGTH_SHORT).show();
        }
    }
}