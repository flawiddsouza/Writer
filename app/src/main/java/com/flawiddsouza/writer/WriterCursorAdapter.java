package com.flawiddsouza.writer;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

public class WriterCursorAdapter extends CursorAdapter {

    private String title;
    private String body;

    public WriterCursorAdapter(Context context, Cursor cursor) {
        super(context, cursor, 0);
    }

    private int getItemViewType(Cursor cursor) {
        title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        if (title.isEmpty() || body.isEmpty()) {
            return 0;
        } else {
            return 1;
        }
    }

    @Override
    public int getItemViewType(int position) {
        Cursor cursor = (Cursor) getItem(position);
        return getItemViewType(cursor);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    // The newView method is used to inflate a new view and return it,
    // you don't bind any data to the view at this point.
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        if(title.isEmpty() || body.isEmpty()) {
            return LayoutInflater.from(context).inflate(R.layout.list_item_1, parent, false);
        } else {
            return LayoutInflater.from(context).inflate(R.layout.list_item_2, parent, false);
        }
    }

    // The bindView method is used to bind all data to a given view
    // such as setting the text on a TextView.
    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean privacyEnabled = preferences.getBoolean("Privacy_Mode_Boolean", false);

        if(!title.isEmpty() && !body.isEmpty()) { // if both are not empty
            TextView tvTitle = (TextView) view.findViewById(R.id.text1);
            TextView tvBody = (TextView) view.findViewById(R.id.text2);
            String bodyWithoutLineBreaks = body.replace("\n", "").replace("\r", "");
            tvTitle.setText(title);
            tvBody.setText(bodyWithoutLineBreaks);

            // Privacy Mode
            if(privacyEnabled) {
                tvTitle.setTextColor(preferences.getInt("Privacy_Mode_Color", R.color.black));
                tvBody.setTextColor(preferences.getInt("Privacy_Mode_Color", R.color.black));
            }

        } else if (!title.isEmpty() && body.isEmpty()) { // if title is not empty
            TextView tvTitle = (TextView) view.findViewById(R.id.text1);
            tvTitle.setText(title);

            // Privacy Mode
            if(privacyEnabled) {
                tvTitle.setTextColor(preferences.getInt("Privacy_Mode_Color", R.color.black));
            }

        } else if (title.isEmpty() && !body.isEmpty()) { // if body is not empty
            TextView tvBody = (TextView) view.findViewById(R.id.text1);
            tvBody.setText(body);

            // Privacy Mode
            if(privacyEnabled) {
                tvBody.setTextColor(preferences.getInt("Privacy_Mode_Color", R.color.black));
            }
        }

    }
}
