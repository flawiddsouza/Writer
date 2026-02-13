package com.flawiddsouza.writer;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

public class EntryCursorAdapter extends CursorAdapter {

    private String title;
    private String body;
    private boolean isEncrypted;

    public EntryCursorAdapter(Context context, Cursor cursor) {
        super(context, cursor, 0);
    }

    private int getItemViewType(Cursor cursor) {
        title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        isEncrypted = cursor.getInt(cursor.getColumnIndexOrThrow("is_encrypted")) == 1;
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

        // Reset background
        view.setBackgroundColor(Color.TRANSPARENT);

        // Get overlay views
        View scanlinesOverlay = view.findViewById(R.id.scanlines_overlay);
        View chromaticOverlay = view.findViewById(R.id.chromatic_overlay);

        if(!title.isEmpty() && !body.isEmpty()) { // if both are not empty
            TextView tvTitle = (TextView) view.findViewById(R.id.text1);
            TextView tvBody = (TextView) view.findViewById(R.id.text2);

            // Show encrypted notes subtly
            if(isEncrypted) {
                tvTitle.setText(title);
                tvBody.setText("•••"); // Just dots, very subtle
            } else {
                String bodyWithoutLineBreaks = body.replace("\n", "").replace("\r", "");
                tvTitle.setText(title);
                tvBody.setText(bodyWithoutLineBreaks);
            }

            // Reset styles
            tvTitle.setTypeface(null, Typeface.NORMAL);
            tvBody.setTypeface(null, Typeface.NORMAL);
            tvTitle.setTextColor(Color.BLACK);
            tvBody.setTextColor(Color.parseColor("#757575")); // Default gray
            tvTitle.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
            tvBody.setShadowLayer(0, 0, 0, Color.TRANSPARENT);

            // Privacy Mode
            if(privacyEnabled) {
                applyPrivacyMode(context, preferences, tvTitle, tvBody, scanlinesOverlay, chromaticOverlay);
            } else {
                // Hide overlays when privacy mode is off
                if(scanlinesOverlay != null) scanlinesOverlay.setVisibility(View.GONE);
                if(chromaticOverlay != null) chromaticOverlay.setVisibility(View.GONE);
            }

        } else if (!title.isEmpty() && body.isEmpty()) { // if title is not empty
            TextView tvTitle = (TextView) view.findViewById(R.id.text1);

            // No indication for encrypted - completely subtle
            tvTitle.setText(title);
            tvTitle.setTypeface(null, Typeface.NORMAL);
            tvTitle.setTextColor(Color.BLACK);
            tvTitle.setShadowLayer(0, 0, 0, Color.TRANSPARENT);

            // Privacy Mode
            if(privacyEnabled) {
                applyPrivacyMode(context, preferences, tvTitle, null, scanlinesOverlay, chromaticOverlay);
            } else {
                // Hide overlays when privacy mode is off
                if(scanlinesOverlay != null) scanlinesOverlay.setVisibility(View.GONE);
                if(chromaticOverlay != null) chromaticOverlay.setVisibility(View.GONE);
            }

        } else if (title.isEmpty() && !body.isEmpty()) { // if body is not empty
            TextView tvBody = (TextView) view.findViewById(R.id.text1);

            // Show dots if encrypted, otherwise show body
            if(isEncrypted) {
                tvBody.setText("•••");
            } else {
                tvBody.setText(body);
            }

            tvBody.setTypeface(null, Typeface.NORMAL);
            tvBody.setTextColor(Color.BLACK);
            tvBody.setShadowLayer(0, 0, 0, Color.TRANSPARENT);

            // Privacy Mode
            if(privacyEnabled) {
                applyPrivacyMode(context, preferences, tvBody, null, scanlinesOverlay, chromaticOverlay);
            } else {
                // Hide overlays when privacy mode is off
                if(scanlinesOverlay != null) scanlinesOverlay.setVisibility(View.GONE);
                if(chromaticOverlay != null) chromaticOverlay.setVisibility(View.GONE);
            }
        }

    }

    private void applyPrivacyMode(Context context, SharedPreferences preferences,
                                  TextView text1, TextView text2,
                                  View scanlinesOverlay, View chromaticOverlay) {
        boolean opacityEnabled = preferences.getBoolean("Privacy_Mode_Opacity_Enabled", false);
        boolean shadowEnabled = preferences.getBoolean("Privacy_Mode_Shadow_Enabled", false);
        boolean scanlinesEnabled = preferences.getBoolean("Privacy_Mode_Scanlines_Enabled", false);
        boolean chromaticEnabled = preferences.getBoolean("Privacy_Mode_Chromatic_Enabled", false);
        int opacityIntensity = preferences.getInt("Privacy_Mode_Opacity_Value", 128);
        int shadowIntensity = preferences.getInt("Privacy_Mode_Shadow_Value", 128);
        int scanlinesIntensity = preferences.getInt("Privacy_Mode_Scanlines_Value", 50);
        int chromaticIntensity = preferences.getInt("Privacy_Mode_Chromatic_Value", 30);

        // Calculate text and shadow alpha
        int textAlpha = 255;
        int shadowAlpha = 0;

        if (opacityEnabled && !shadowEnabled) {
            // Only opacity: reduce text transparency
            textAlpha = 255 - opacityIntensity;
        } else if (shadowEnabled && !opacityEnabled) {
            // Only shadow: transparent text with shadow
            textAlpha = 0;
            shadowAlpha = 60 + (int)(shadowIntensity / 2.8f); // 60-150 range
        } else if (opacityEnabled && shadowEnabled) {
            // Both: text is transparent, opacity controls shadow transparency
            textAlpha = 0;
            int baseShadowAlpha = 60 + (int)(shadowIntensity / 2.8f);
            shadowAlpha = (int)(baseShadowAlpha * (255 - opacityIntensity) / 255.0f);
        }

        // Apply shadow if enabled
        if (shadowEnabled && shadowAlpha > 0) {
            float shadowRadius = 2 + (shadowIntensity / 40f); // 2-8 range (more blur)
            float shadowOffset = 0.5f + (shadowIntensity / 200f); // 0.5-1.8 range (less offset)

            text1.setShadowLayer(shadowRadius, shadowOffset, shadowOffset,
                Color.argb(shadowAlpha, 0, 0, 0));
            if(text2 != null) {
                text2.setShadowLayer(shadowRadius, shadowOffset, shadowOffset,
                    Color.argb(shadowAlpha, 0, 0, 0));
            }
        }

        // Apply final text color
        text1.setTextColor(Color.argb(textAlpha, 0, 0, 0));
        if(text2 != null) {
            text2.setTextColor(Color.argb(textAlpha, 117, 117, 117)); // #757575 with alpha
        }

        // Apply scanlines if enabled
        if (scanlinesEnabled && scanlinesOverlay != null) {
            ScanlinesDrawable scanlinesDrawable = new ScanlinesDrawable(scanlinesIntensity);
            scanlinesOverlay.setBackground(scanlinesDrawable);
            scanlinesOverlay.setVisibility(View.VISIBLE);
        } else if(scanlinesOverlay != null) {
            scanlinesOverlay.setVisibility(View.GONE);
        }

        // Apply chromatic aberration if enabled
        if (chromaticEnabled && chromaticOverlay != null) {
            ChromaticAberrationDrawable chromaticDrawable = new ChromaticAberrationDrawable(chromaticIntensity);
            chromaticOverlay.setBackground(chromaticDrawable);
            chromaticOverlay.setVisibility(View.VISIBLE);
        } else if(chromaticOverlay != null) {
            chromaticOverlay.setVisibility(View.GONE);
        }
    }
}
