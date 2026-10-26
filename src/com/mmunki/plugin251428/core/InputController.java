package com.mmunki.plugin251428.core;

/** Sends taps and swipes through root, falling back to accessibility gestures. */
public final class InputController {
    public interface Gestures {
        boolean tap(int x, int y);

        boolean swipe(int x1, int y1, int x2, int y2, int durationMs);

        boolean available();
    }

    private final Gestures gestures;

    public InputController(Gestures gestures) {
        this.gestures = gestures;
    }

    public boolean tap(int x, int y) {
        if (RootShell.isRooted()) {
            try {
                RootShell.Result result = RootShell.exec("input tap " + x + " " + y, 4000);
                if (result.ok()) {
                    return true;
                }
            } catch (Throwable ignored) {
                // fall through to accessibility
            }
        }
        if (gestures != null && gestures.available()) {
            return gestures.tap(x, y);
        }
        try {
            RootShell.Result result = RootShell.exec("input tap " + x + " " + y, 4000);
            return result.ok();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        if (RootShell.isRooted()) {
            try {
                RootShell.Result result = RootShell.exec("input swipe " + x1 + " " + y1
                        + " " + x2 + " " + y2 + " " + Math.max(30, durationMs), 5000);
                if (result.ok()) {
                    return true;
                }
            } catch (Throwable ignored) {
                // fall through to accessibility
            }
        }
        if (gestures != null && gestures.available()) {
            return gestures.swipe(x1, y1, x2, y2, durationMs);
        }
        try {
            RootShell.Result result = RootShell.exec("input swipe " + x1 + " " + y1
                    + " " + x2 + " " + y2 + " " + Math.max(30, durationMs), 5000);
            return result.ok();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
