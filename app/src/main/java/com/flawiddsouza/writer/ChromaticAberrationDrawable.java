package com.flawiddsouza.writer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

public class ChromaticAberrationDrawable extends Drawable {
    private Paint redPaint;
    private Paint cyanPaint;
    private int intensity;
    private float stripWidth;

    public ChromaticAberrationDrawable(int intensity) {
        this.intensity = intensity;
        // Intensity 0-100 controls the effect strength
        // Calculate strip width based on intensity (wider strips = more visible effect)
        stripWidth = 20 + (intensity / 2f); // 20-70 pixel strips

        // Calculate alpha based on intensity (0-100 -> 10-80 alpha)
        int alpha = 10 + (int)(intensity * 0.7f);

        // Red channel paint
        redPaint = new Paint();
        redPaint.setColor(0xFF0000); // Red
        redPaint.setAlpha(alpha);
        redPaint.setStyle(Paint.Style.FILL);

        // Cyan channel paint (opposite of red for chromatic aberration)
        cyanPaint = new Paint();
        cyanPaint.setColor(0x00FFFF); // Cyan
        cyanPaint.setAlpha(alpha);
        cyanPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void draw(Canvas canvas) {
        int height = getBounds().height();
        int width = getBounds().width();

        // Draw alternating vertical strips of red and cyan
        // The offset creates the chromatic aberration effect
        float offset = 1 + (intensity / 25f); // 1-5 pixels offset

        for (float x = 0; x < width; x += stripWidth * 2) {
            // Red strip (shifted slightly)
            canvas.drawRect(x - offset, 0, x + stripWidth - offset, height, redPaint);

            // Cyan strip (shifted opposite direction)
            canvas.drawRect(x + stripWidth + offset, 0, x + stripWidth * 2 + offset, height, cyanPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        redPaint.setAlpha(alpha);
        cyanPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        redPaint.setColorFilter(colorFilter);
        cyanPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
