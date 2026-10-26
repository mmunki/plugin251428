package com.mmunki.plugin251428.core;

/** Immutable ARGB frame used by the vision code. */
public final class VisionFrame {
    public final int width;
    public final int height;
    public final int[] argb;

    public VisionFrame(int width, int height, int[] argb) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("empty frame");
        }
        if (argb == null || argb.length < width * height) {
            throw new IllegalArgumentException("pixel buffer is too small");
        }
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public int pixel(int x, int y) {
        return argb[y * width + x];
    }

    public int red(int x, int y) {
        return (pixel(x, y) >> 16) & 0xff;
    }

    public int green(int x, int y) {
        return (pixel(x, y) >> 8) & 0xff;
    }

    public int blue(int x, int y) {
        return pixel(x, y) & 0xff;
    }
}
