package com.mmunki.plugin251428.core;

/** One action the bot can perform. Coordinates are screen pixels. */
public final class GamePlan {
    public static final String KIND_NONE = "none";
    public static final String KIND_TAP = "tap";
    public static final String KIND_SWIPE = "swipe";

    public final String kind;
    public final int x1;
    public final int y1;
    public final int x2;
    public final int y2;
    public final int durationMs;
    public final String reason;
    public final float confidence;

    private GamePlan(String kind, int x1, int y1, int x2, int y2,
                     int durationMs, String reason, float confidence) {
        this.kind = kind;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.durationMs = durationMs;
        this.reason = reason;
        this.confidence = confidence;
    }

    public static GamePlan none(String reason) {
        return new GamePlan(KIND_NONE, 0, 0, 0, 0, 0, reason, 0f);
    }

    public static GamePlan tap(int x, int y, String reason, float confidence) {
        return new GamePlan(KIND_TAP, x, y, x, y, 0, reason, confidence);
    }

    public static GamePlan swipe(int x1, int y1, int x2, int y2, int durationMs,
                                 String reason, float confidence) {
        return new GamePlan(KIND_SWIPE, x1, y1, x2, y2, durationMs, reason, confidence);
    }

    public boolean isAction() {
        return !KIND_NONE.equals(kind);
    }

    @Override
    public String toString() {
        if (KIND_SWIPE.equals(kind)) {
            return "swipe (" + x1 + "," + y1 + ")->(" + x2 + "," + y2 + ") "
                    + durationMs + "ms " + reason;
        }
        if (KIND_TAP.equals(kind)) {
            return "tap (" + x1 + "," + y1 + ") " + reason;
        }
        return "none " + reason;
    }
}
