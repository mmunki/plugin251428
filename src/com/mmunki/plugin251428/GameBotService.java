package com.mmunki.plugin251428;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.mmunki.plugin251428.core.BotLog;
import com.mmunki.plugin251428.core.DoublePressDetector;
import com.mmunki.plugin251428.core.GameConfig;
import com.mmunki.plugin251428.core.GamePlan;
import com.mmunki.plugin251428.core.InputController;
import com.mmunki.plugin251428.core.RootShell;
import com.mmunki.plugin251428.core.ScreenSource;
import com.mmunki.plugin251428.core.SoccerBrain;
import com.mmunki.plugin251428.core.SoccerVision;
import com.mmunki.plugin251428.core.VisionFrame;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * The armed foreground service. It owns the loop, the root key fallback and
 * the wake lock. The accessibility service only turns key presses and UI
 * observations into start/stop intents.
 */
public class GameBotService extends Service {
    public static final String ACTION_ARM = "com.mmunki.plugin251428.ARM";
    public static final String ACTION_START = "com.mmunki.plugin251428.START";
    public static final String ACTION_STOP = "com.mmunki.plugin251428.STOP";
    public static final String ACTION_DISARM = "com.mmunki.plugin251428.DISARM";

    private static final String CHANNEL = "game_automation";
    private static final int NOTIFICATION_ID = 251428;

    private static volatile GameBotService instance;

    private final DoublePressDetector rootUp = new DoublePressDetector(650, 45);
    private final DoublePressDetector rootDown = new DoublePressDetector(650, 45);

    private HandlerThread workerThread;
    private Handler worker;
    private Handler main;
    private volatile boolean armed;
    private volatile boolean playing;
    private boolean foregroundStarted;
    private GameConfig config = new GameConfig();
    private ScreenSource screen;
    private InputController input;
    private SoccerVision.Blob lastBall;
    private PowerManager.WakeLock wakeLock;
    private Thread keyThread;
    private volatile boolean keyRunning;
    private volatile Process keyProcess;
    private long lastSkipMs;
    private long lastActionLogMs;
    private long lastFrameLogMs;
    private String lastPackage = "";

    public static boolean isPlaying() {
        GameBotService service = instance;
        return service != null && service.playing;
    }

    public static boolean isArmed() {
        GameBotService service = instance;
        return service != null && service.armed;
    }

    public static void arm(Context context) {
        sendStart(context, ACTION_ARM);
    }

    public static void startBot(Context context) {
        sendStart(context, ACTION_START);
    }

    public static void stopPlaying(Context context) {
        if (instance == null) {
            return;
        }
        try {
            context.startService(new Intent(context, GameBotService.class).setAction(ACTION_STOP));
        } catch (Throwable t) {
            BotLog.post("Could not reach the bot service");
        }
    }

    public static void disarm(Context context) {
        if (instance == null) {
            return;
        }
        try {
            context.startService(new Intent(context, GameBotService.class).setAction(ACTION_DISARM));
        } catch (Throwable t) {
            BotLog.post("Could not disarm the bot service");
        }
    }

    private static void sendStart(Context context, String action) {
        Intent intent = new Intent(context, GameBotService.class).setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable t) {
            BotLog.post("Android refused to start the service: "
                    + t.getClass().getSimpleName());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
        workerThread = new HandlerThread("plugin251428-loop");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        main = new Handler(getMainLooper());
        screen = new ScreenSource(new ScreenSource.AccessibilityFrames() {
            @Override
            public VisionFrame screenshotFrame() {
                BotAccessibilityService service = BotAccessibilityService.get();
                return service == null ? null : service.screenshotFrame();
            }

            @Override
            public boolean available() {
                return BotAccessibilityService.get() != null
                        && BotAccessibilityService.canUseScreenshots();
            }
        });
        input = new InputController(new InputController.Gestures() {
            @Override
            public boolean tap(int x, int y) {
                BotAccessibilityService service = BotAccessibilityService.get();
                return service != null && service.tap(x, y);
            }

            @Override
            public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
                BotAccessibilityService service = BotAccessibilityService.get();
                return service != null && service.swipe(x1, y1, x2, y2, durationMs);
            }

            @Override
            public boolean available() {
                return BotAccessibilityService.get() != null;
            }
        });
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "plugin251428:game-bot");
            wakeLock.setReferenceCounted(false);
        }
        BotLog.post("Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        Log.i("Plugin251428", "service action " + action);
        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        switch (action) {
            case ACTION_ARM:
                armed = true;
                ensureForeground();
                startKeyMonitor();
                BotLog.post("Armed: double volume-up starts");
                break;
            case ACTION_START:
                armed = true;
                ensureForeground();
                startKeyMonitor();
                beginPlay();
                break;
            case ACTION_STOP:
                stopPlay();
                break;
            case ACTION_DISARM:
                stopPlay();
                armed = false;
                stopKeyMonitor();
                foregroundStarted = false;
                stopForeground(true);
                stopSelf();
                BotLog.post("Disarmed");
                return START_NOT_STICKY;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    private void ensureForeground() {
        Notification notification = buildNotification();
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification);
            foregroundStarted = true;
        } else {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private void beginPlay() {
        if (playing) {
            return;
        }
        config = ConfigStore.load(this);
        playing = true;
        lastBall = null;
        lastSkipMs = 0;
        lastPackage = "";
        if (wakeLock != null) {
            wakeLock.acquire(3 * 60 * 60 * 1000L);
        }
        ensureForeground();
        BotLog.post("Playing (" + config.mode + ", reaction " + config.reactionMs + " ms)");
        if (Boolean.FALSE.equals(RootShell.cachedRoot())
                && !BotAccessibilityService.isConnected()) {
            BotLog.post("No root and no accessibility: taps cannot be sent");
        }
        if (config.gamePackage.isEmpty()) {
            BotLog.post("No game package set: only auto-skip will run");
        }
        worker.removeCallbacks(tick);
        worker.post(tick);
    }

    private void stopPlay() {
        if (!playing) {
            return;
        }
        playing = false;
        worker.removeCallbacks(tick);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        lastBall = null;
        if (armed) {
            ensureForeground();
        }
        BotLog.post("Stopped");
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!playing) {
                return;
            }
            long started = SystemClock.uptimeMillis();
            try {
                config = ConfigStore.load(GameBotService.this);
                step();
            } catch (Throwable t) {
                BotLog.post("Loop error: " + t.getClass().getSimpleName());
            }
            long elapsed = SystemClock.uptimeMillis() - started;
            long delay = Math.max(80, config.reactionMs - elapsed);
            if (playing) {
                worker.postDelayed(this, delay);
            }
        }
    };

    private void step() {
        long now = System.currentTimeMillis();
        BotAccessibilityService accessibility = BotAccessibilityService.get();
        String pkg = accessibility == null ? null : accessibility.foregroundPackage();
        if (!config.gamePackage.isEmpty()) {
            if (pkg == null) {
                logFrameIssue(now, "Foreground package is unknown, waiting");
                return;
            }
            if (!config.gamePackage.equals(pkg)) {
                if (!config.gamePackage.equals(lastPackage)) {
                    lastPackage = config.gamePackage;
                    BotLog.post("Waiting for " + config.gamePackage + " (now " + pkg + ")");
                }
                return;
            }
        }

        if (accessibility != null) {
            BotAccessibilityService.SkipTarget skip = accessibility.findSkipTarget();
            if (skip != null && now - lastSkipMs > 650) {
                if (input.tap(skip.x, skip.y)) {
                    lastSkipMs = now;
                    Log.i("Plugin251428", "skip " + skip.label + " at "
                            + skip.x + "," + skip.y);
                    BotLog.post("Tapped " + skip.label + " at " + skip.x + "," + skip.y);
                    return;
                }
            }
        }
        if (!config.isSoccer()) {
            return;
        }
        if (config.gamePackage.isEmpty()) {
            logFrameIssue(now, "Set a game package to enable soccer vision");
            return;
        }

        VisionFrame frame = screen.capture();
        if (frame == null) {
            logFrameIssue(now, "No frame: enable accessibility or grant root");
            return;
        }
        SoccerBrain.Decision decision = SoccerBrain.think(frame, lastBall,
                config.goalSide, config.aggressiveness);
        lastBall = decision.ball;
        GamePlan plan = decision.plan;
        if (!plan.isAction()) {
            return;
        }
        boolean sent;
        if (GamePlan.KIND_TAP.equals(plan.kind)) {
            sent = input.tap(plan.x1, plan.y1);
        } else {
            sent = input.swipe(plan.x1, plan.y1, plan.x2, plan.y2, plan.durationMs);
        }
        if (!sent) {
            logFrameIssue(now, "Input was rejected by root and accessibility");
        } else if (now - lastActionLogMs > 700) {
            lastActionLogMs = now;
            int confidence = Math.round(plan.confidence * 100);
            Log.i("Plugin251428", "action " + plan + " (" + confidence + "%) frame "
                    + frame.width + "x" + frame.height);
            BotLog.post(plan + " (" + confidence + "%)");
        }
    }

    private void logFrameIssue(long now, String message) {
        if (now - lastFrameLogMs > 4000) {
            lastFrameLogMs = now;
            BotLog.post(message);
        }
    }

    private void startKeyMonitor() {
        if (BotAccessibilityService.isConnected()) {
            return;
        }
        if (keyThread != null) {
            return;
        }
        keyRunning = true;
        keyThread = new Thread(() -> {
            BotLog.post("Root volume trigger listening");
            try {
                Process process = new ProcessBuilder("su", "-c", "getevent -l")
                        .redirectErrorStream(true)
                        .start();
                keyProcess = process;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                String line;
                while (keyRunning && (line = reader.readLine()) != null) {
                    if (BotAccessibilityService.isConnected()) {
                        continue;
                    }
                    String upper = line.toUpperCase(Locale.US).trim();
                    if (!upper.endsWith("DOWN")) {
                        continue;
                    }
                    if (upper.contains("KEY_VOLUMEUP") || upper.contains("VOLUME_UP")) {
                        if (rootUp.onPress(System.currentTimeMillis())) {
                            main.post(() -> {
                                beginPlay();
                                BotLog.post("Double volume-up (root): start");
                            });
                        }
                    } else if (upper.contains("KEY_VOLUMEDOWN")
                            || upper.contains("VOLUME_DOWN")) {
                        if (rootDown.onPress(System.currentTimeMillis())) {
                            main.post(() -> {
                                stopPlay();
                                BotLog.post("Double volume-down (root): stop");
                            });
                        }
                    }
                }
                if (keyRunning) {
                    BotLog.post("Root key listener ended (getevent unavailable?)");
                }
            } catch (Throwable t) {
                if (keyRunning) {
                    BotLog.post("Root key listener failed: "
                            + t.getClass().getSimpleName()
                            + " - enable accessibility instead");
                }
            }
        }, "plugin251428-root-keys");
        keyThread.setDaemon(true);
        keyThread.start();
    }

    private void stopKeyMonitor() {
        keyRunning = false;
        Process process = keyProcess;
        keyProcess = null;
        if (process != null) {
            try {
                process.destroy();
            } catch (Throwable ignored) {
                // already gone
            }
        }
        if (keyThread != null) {
            keyThread.interrupt();
            keyThread = null;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Volume trigger and game automation status");
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent disarmIntent = new Intent(this, GameBotService.class).setAction(ACTION_DISARM);
        PendingIntent stop = PendingIntent.getService(this, 1, disarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = playing
                ? getString(R.string.notification_playing)
                : getString(R.string.notification_armed);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(content)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop);
        return builder.build();
    }

    @Override
    public void onDestroy() {
        stopPlay();
        stopKeyMonitor();
        if (workerThread != null) {
            workerThread.quitSafely();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        instance = null;
        BotLog.post("Service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
