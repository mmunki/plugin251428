package com.mmunki.plugin251428;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.mmunki.plugin251428.core.BotLog;
import com.mmunki.plugin251428.core.GameConfig;
import com.mmunki.plugin251428.core.RootShell;

import java.util.List;

public class MainActivity extends Activity implements BotLog.Listener {
    private static final String[] MODE_LABELS = {"Soccer vision", "Auto-skip only"};
    private static final String[] MODE_VALUES = {GameConfig.MODE_SOCCER, GameConfig.MODE_AUTOSKIP};
    private static final String[] SIDE_LABELS = {"Auto", "Left", "Right", "Top", "Bottom"};
    private static final String[] SIDE_VALUES = {
            GameConfig.SIDE_AUTO, GameConfig.SIDE_LEFT, GameConfig.SIDE_RIGHT,
            GameConfig.SIDE_TOP, GameConfig.SIDE_BOTTOM
    };

    private final Handler handler = new Handler();
    private TextView status;
    private TextView logView;
    private ScrollView logScroll;
    private EditText packageField;
    private Spinner modeSpinner;
    private Spinner sideSpinner;
    private SeekBar reactionBar;
    private SeekBar aggressionBar;
    private TextView reactionLabel;
    private TextView aggressionLabel;
    private Button armButton;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 700);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BotLog.addListener(this);
        setContentView(buildUi());
        loadIntoUi();
        handler.post(refresh);
        refreshLog();
        new Thread(() -> {
            RootShell.isRooted();
            runOnUiThread(this::refreshStatus);
        }, "plugin251428-root-check").start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Plugin251428");
        title.setTextSize(26);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Root or accessibility game automation\n"
                + "Double volume-up starts, double volume-down stops.");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(dp(10), dp(10), dp(10), dp(10));
        status.setBackgroundColor(0xffeef2f5);
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(12), 0, dp(8));
        armButton = new Button(this);
        armButton.setText("Arm bot");
        armButton.setOnClickListener(v -> {
            saveFromUi();
            requestNotificationPermission();
            GameBotService.arm(this);
            refreshStatus();
        });
        Button disarm = new Button(this);
        disarm.setText("Disarm");
        disarm.setOnClickListener(v -> {
            GameBotService.disarm(this);
            refreshStatus();
        });
        buttons.addView(armButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(disarm, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(buttons);

        Button accessibility = new Button(this);
        accessibility.setText("Open accessibility settings");
        accessibility.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable t) {
                Toast.makeText(this, "No accessibility settings on this device",
                        Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(accessibility);

        Button battery = new Button(this);
        battery.setText("Ignore battery optimization");
        battery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Throwable ignored) {
                    Toast.makeText(this, "Battery settings are not available",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(battery);

        root.addView(label("Game package (required for soccer vision)"));
        packageField = new EditText(this);
        packageField.setSingleLine(true);
        packageField.setHint("com.example.game");
        root.addView(packageField);

        root.addView(label("Mode"));
        modeSpinner = new Spinner(this);
        modeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, MODE_LABELS));
        root.addView(modeSpinner);

        root.addView(label("Goal side"));
        sideSpinner = new Spinner(this);
        sideSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, SIDE_LABELS));
        root.addView(sideSpinner);

        reactionLabel = label("");
        root.addView(reactionLabel);
        reactionBar = new SeekBar(this);
        reactionBar.setMax(900);
        root.addView(reactionBar);

        aggressionLabel = label("");
        root.addView(aggressionLabel);
        aggressionBar = new SeekBar(this);
        aggressionBar.setMax(100);
        root.addView(aggressionBar);

        reactionBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                reactionLabel.setText("Reaction: " + reactionValue() + " ms");
            }
        });
        aggressionBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                aggressionLabel.setText("Aggression: " + aggressionValue() + " %");
            }
        });

        Button save = new Button(this);
        save.setText("Save settings");
        save.setOnClickListener(v -> {
            saveFromUi();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        Button rootCheck = new Button(this);
        rootCheck.setText("Test root");
        rootCheck.setOnClickListener(v -> {
            RootShell.refreshRoot();
            new Thread(() -> {
                boolean rooted = RootShell.isRooted();
                runOnUiThread(() -> {
                    Toast.makeText(this, rooted ? "Root works (uid=0)"
                            : "Root is not available", Toast.LENGTH_SHORT).show();
                    refreshStatus();
                });
            }).start();
        });
        root.addView(rootCheck);

        TextView logTitle = label("Live log");
        logTitle.setPadding(0, dp(14), 0, dp(4));
        root.addView(logTitle);
        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(0xff101418);
        logView = new TextView(this);
        logView.setTextColor(0xffd7ffd7);
        logView.setTextSize(12);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(dp(8), dp(8), dp(8), dp(8));
        logScroll.addView(logView);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240));
        root.addView(logScroll, logParams);
        return scroll;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setPadding(0, dp(10), 0, dp(2));
        return view;
    }

    private int reactionValue() {
        return 150 + reactionBar.getProgress();
    }

    private int aggressionValue() {
        return aggressionBar.getProgress();
    }

    private void loadIntoUi() {
        GameConfig config = ConfigStore.load(this);
        packageField.setText(config.gamePackage);
        modeSpinner.setSelection(indexOf(MODE_VALUES, config.mode));
        sideSpinner.setSelection(indexOf(SIDE_VALUES, config.goalSide));
        reactionBar.setProgress(Math.max(0, Math.min(900, config.reactionMs - 150)));
        aggressionBar.setProgress(config.aggressiveness);
        reactionLabel.setText("Reaction: " + config.reactionMs + " ms");
        aggressionLabel.setText("Aggression: " + config.aggressiveness + " %");
    }

    private void saveFromUi() {
        GameConfig config = new GameConfig();
        config.gamePackage = packageField.getText().toString().trim();
        config.mode = MODE_VALUES[Math.max(0, modeSpinner.getSelectedItemPosition())];
        config.goalSide = SIDE_VALUES[Math.max(0, sideSpinner.getSelectedItemPosition())];
        config.reactionMs = reactionValue();
        config.aggressiveness = aggressionValue();
        ConfigStore.save(this, config);
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private void refreshStatus() {
        Boolean root = RootShell.cachedRoot();
        boolean enabled = BotAccessibilityService.isEnabled(this);
        boolean connected = BotAccessibilityService.isConnected();
        GameConfig config = ConfigStore.load(this);
        String state = GameBotService.isPlaying() ? "playing"
                : GameBotService.isArmed() ? "armed" : "idle";
        status.setText("Root: " + (root == null ? "checking" : root ? "yes" : "no")
                + "    Accessibility: " + (connected ? "connected"
                : enabled ? "enabled, not connected" : "off")
                + "\nService: " + state
                + "    Mode: " + config.mode
                + "    Goal: " + config.goalSide);
        armButton.setText(GameBotService.isArmed() ? "Re-arm bot" : "Arm bot");
    }

    private void refreshLog() {
        List<String> lines = BotLog.snapshot();
        if (lines.isEmpty()) {
            logView.setText("No events yet.");
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        logView.setText(builder.toString());
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 20);
            } catch (Throwable ignored) {
                // the notification is still created, it may just be hidden
            }
        }
    }

    @Override
    public void onLogChanged() {
        runOnUiThread(this::refreshLog);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshLog();
    }

    @Override
    protected void onDestroy() {
        BotLog.removeListener(this);
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
