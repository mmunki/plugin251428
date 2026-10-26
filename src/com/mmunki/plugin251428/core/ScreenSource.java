package com.mmunki.plugin251428.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Captures the display through accessibility first, then through root. */
public final class ScreenSource {
    public interface AccessibilityFrames {
        VisionFrame screenshotFrame();

        boolean available();
    }

    private final AccessibilityFrames accessibility;
    private long lastRootFailure;

    public ScreenSource(AccessibilityFrames accessibility) {
        this.accessibility = accessibility;
    }

    public VisionFrame capture() {
        if (accessibility != null && accessibility.available()) {
            VisionFrame frame = accessibility.screenshotFrame();
            if (frame != null) {
                return frame;
            }
        }
        if (!RootShell.isRooted()) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - lastRootFailure < 5000) {
            return null;
        }
        try {
            byte[] png = RootShell.execBytes("screencap -p", 7000);
            if (png == null || png.length < 32) {
                lastRootFailure = now;
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
            if (bitmap == null) {
                lastRootFailure = now;
                return null;
            }
            try {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                return new VisionFrame(width, height, pixels);
            } finally {
                bitmap.recycle();
            }
        } catch (Throwable ignored) {
            lastRootFailure = now;
            return null;
        }
    }
}
