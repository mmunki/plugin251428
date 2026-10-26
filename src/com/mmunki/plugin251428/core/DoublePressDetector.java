package com.mmunki.plugin251428.core;

/**
 * Recognises a fast double press of the same key without depending on Android.
 * A press that arrives inside the minimum gap is treated as key repeat and is
 * ignored; a detected double press resets the sequence so a third press starts
 * a new one.
 */
public final class DoublePressDetector {
    private final long windowMs;
    private final long minGapMs;
    private long lastPressMs = Long.MIN_VALUE;

    public DoublePressDetector(long windowMs, long minGapMs) {
        if (windowMs <= 0 || minGapMs < 0 || minGapMs >= windowMs) {
            throw new IllegalArgumentException("invalid timing window");
        }
        this.windowMs = windowMs;
        this.minGapMs = minGapMs;
    }

    public synchronized boolean onPress(long nowMs) {
        long delta = nowMs - lastPressMs;
        if (lastPressMs != Long.MIN_VALUE && delta >= 0 && delta < minGapMs) {
            return false;
        }
        lastPressMs = nowMs;
        if (delta >= minGapMs && delta <= windowMs) {
            lastPressMs = Long.MIN_VALUE;
            return true;
        }
        return false;
    }

    public synchronized void reset() {
        lastPressMs = Long.MIN_VALUE;
    }
}
