package com.mmunki.plugin251428.core;

/** Settings shared by the service and the activity. Pure data, easy to test. */
public final class GameConfig {
    public static final String MODE_SOCCER = "soccer";
    public static final String MODE_AUTOSKIP = "autoskip";

    public static final String SIDE_AUTO = "auto";
    public static final String SIDE_LEFT = "left";
    public static final String SIDE_RIGHT = "right";
    public static final String SIDE_TOP = "top";
    public static final String SIDE_BOTTOM = "bottom";

    public String gamePackage = "";
    public String mode = MODE_SOCCER;
    public String goalSide = SIDE_AUTO;
    public int reactionMs = 300;
    public int aggressiveness = 55;

    public boolean isSoccer() {
        return MODE_SOCCER.equals(mode);
    }

    public static String normaliseMode(String value) {
        return MODE_AUTOSKIP.equals(value) ? MODE_AUTOSKIP : MODE_SOCCER;
    }

    public static String normaliseSide(String value) {
        if (SIDE_LEFT.equals(value) || SIDE_RIGHT.equals(value)
                || SIDE_TOP.equals(value) || SIDE_BOTTOM.equals(value)
                || SIDE_AUTO.equals(value)) {
            return value;
        }
        return SIDE_AUTO;
    }

    public GameConfig copy() {
        GameConfig other = new GameConfig();
        other.gamePackage = gamePackage;
        other.mode = mode;
        other.goalSide = goalSide;
        other.reactionMs = reactionMs;
        other.aggressiveness = aggressiveness;
        return other;
    }
}
