package com.mmunki.plugin251428;

import android.content.Context;
import android.content.SharedPreferences;

import com.mmunki.plugin251428.core.GameConfig;

/** SharedPreferences backed settings, loaded on every bot loop. */
final class ConfigStore {
    private static final String FILE = "plugin251428";
    private static final String KEY_PACKAGE = "game_package";
    private static final String KEY_MODE = "mode";
    private static final String KEY_SIDE = "goal_side";
    private static final String KEY_REACTION = "reaction_ms";
    private static final String KEY_AGGRESSION = "aggressiveness";

    private ConfigStore() {
    }

    static GameConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        GameConfig config = new GameConfig();
        config.gamePackage = prefs.getString(KEY_PACKAGE, "");
        config.mode = GameConfig.normaliseMode(prefs.getString(KEY_MODE, GameConfig.MODE_SOCCER));
        config.goalSide = GameConfig.normaliseSide(
                prefs.getString(KEY_SIDE, GameConfig.SIDE_AUTO));
        config.reactionMs = Math.max(120, Math.min(1200,
                prefs.getInt(KEY_REACTION, 300)));
        config.aggressiveness = Math.max(0, Math.min(100,
                prefs.getInt(KEY_AGGRESSION, 55)));
        return config;
    }

    static void save(Context context, GameConfig config) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(KEY_PACKAGE, config.gamePackage == null ? "" : config.gamePackage.trim())
                .putString(KEY_MODE, GameConfig.normaliseMode(config.mode))
                .putString(KEY_SIDE, GameConfig.normaliseSide(config.goalSide))
                .putInt(KEY_REACTION, Math.max(120, Math.min(1200, config.reactionMs)))
                .putInt(KEY_AGGRESSION, Math.max(0, Math.min(100, config.aggressiveness)))
                .apply();
    }
}
