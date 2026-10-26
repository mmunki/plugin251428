package com.mmunki.plugin251428;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.mmunki.plugin251428.core.BotLog;
import com.mmunki.plugin251428.core.DoublePressDetector;
import com.mmunki.plugin251428.core.VisionFrame;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Observes the volume keys, reads the foreground UI to find skip/continue
 * buttons, and provides gesture and screenshot fallbacks when root is absent.
 */
public class BotAccessibilityService extends AccessibilityService {
    public static final class SkipTarget {
        public final int x;
        public final int y;
        public final String label;
        public final int score;

        SkipTarget(int x, int y, String label, int score) {
            this.x = x;
            this.y = y;
            this.label = label;
            this.score = score;
        }
    }

    private static volatile BotAccessibilityService instance;

    private final DoublePressDetector volumeUp = new DoublePressDetector(650, 45);
    private final DoublePressDetector volumeDown = new DoublePressDetector(650, 45);
    private volatile String lastPackage = "";

    public static BotAccessibilityService get() {
        return instance;
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public static boolean isEnabled(Context context) {
        try {
            String enabled = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) {
                return false;
            }
            ComponentName component = new ComponentName(context, BotAccessibilityService.class);
            String flat = component.flattenToString().toLowerCase(Locale.US);
            String shortFlat = component.flattenToShortString().toLowerCase(Locale.US);
            String lower = enabled.toLowerCase(Locale.US);
            return lower.contains(flat) || lower.contains(shortFlat);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i("Plugin251428", "accessibility onServiceConnected");
        BotLog.post("Accessibility service connected");
    }

    @Override
    public void onDestroy() {
        instance = null;
        Log.i("Plugin251428", "accessibility onDestroy");
        BotLog.post("Accessibility service disconnected");
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
        // no continuous speech feedback is used
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (!TextUtils.isEmpty(pkg)) {
            lastPackage = pkg.toString();
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN
                || event.getRepeatCount() > 0) {
            return false;
        }
        int code = event.getKeyCode();
        Log.i("Plugin251428", "key event " + code);
        if (code == KeyEvent.KEYCODE_VOLUME_UP) {
            if (volumeUp.onPress(System.currentTimeMillis())) {
                BotLog.post("Double volume-up: start");
                GameBotService.startBot(this);
                return true;
            }
        } else if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (volumeDown.onPress(System.currentTimeMillis())) {
                BotLog.post("Double volume-down: stop");
                GameBotService.stopPlaying(this);
                return true;
            }
        }
        return false;
    }

    public String foregroundPackage() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                if (!TextUtils.isEmpty(pkg)) {
                    return pkg.toString();
                }
            }
        } catch (Throwable ignored) {
            // fall through to the last event package
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
        return lastPackage;
    }

    /**
     * Walks the interactive window and returns the highest-priority visible
     * skip / continue / claim / close button.
     */
    public SkipTarget findSkipTarget() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) {
                return null;
            }
            String own = getPackageName();
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            int visited = 0;
            SkipTarget best = null;
            long bestArea = Long.MAX_VALUE;
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            while (!queue.isEmpty() && visited < 700) {
                AccessibilityNodeInfo node = queue.removeFirst();
                visited++;
                try {
                    CharSequence pkg = node.getPackageName();
                    if (pkg != null && own.equals(pkg.toString())) {
                        continue;
                    }
                    String text = lower(node.getText()) + " " + lower(node.getContentDescription());
                    String id = lower(node.getViewIdResourceName());
                    int score = score(text, id);
                    if (score > 0 && node.isVisibleToUser()) {
                        AccessibilityNodeInfo clickable = node;
                        boolean found = clickable.isClickable();
                        for (int i = 0; i < 4 && !found; i++) {
                            AccessibilityNodeInfo parent = clickable.getParent();
                            if (parent == null) {
                                break;
                            }
                            clickable = parent;
                            found = clickable.isClickable();
                        }
                        if (!found) {
                            continue;
                        }
                        Rect bounds = new Rect();
                        clickable.getBoundsInScreen(bounds);
                        long screenArea = (long) metrics.widthPixels * metrics.heightPixels;
                        long area = (long) bounds.width() * bounds.height();
                        if (bounds.width() > 0 && bounds.height() > 0
                                && area < screenArea / 2
                                && bounds.left < metrics.widthPixels
                                && bounds.top < metrics.heightPixels
                                && bounds.right > 0 && bounds.bottom > 0) {
                            int cx = bounds.centerX();
                            int cy = bounds.centerY();
                            String label = text.trim();
                            if (label.isEmpty()) {
                                label = id.isEmpty() ? "button" : id;
                            }
                            if (best == null || score > best.score
                                    || (score == best.score && area < bestArea)) {
                                best = new SkipTarget(cx, cy, label, score);
                                bestArea = area;
                            }
                        }
                    }
                    for (int i = 0; i < node.getChildCount(); i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) {
                            queue.addLast(child);
                        }
                    }
                } catch (Throwable ignored) {
                    // a node can disappear while it is being read
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
    }

    private static String lower(CharSequence value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.US).trim();
    }

    private static int score(String text, String id) {
        if (hasWord(text, "skip") || hasWord(id, "skip")
                || text.contains("跳过") || text.contains("跳過")) {
            return 100;
        }
        if (hasWord(text, "continue") || hasWord(id, "continue")
                || text.contains("继续") || text.contains("繼續")) {
            return 95;
        }
        if (hasWord(text, "next") || hasWord(id, "next") || text.contains("下一步")) {
            return 85;
        }
        if (hasWord(text, "claim") || hasWord(text, "collect")
                || hasWord(text, "reward") || hasWord(id, "claim")
                || text.contains("领取") || text.contains("領取")) {
            return 75;
        }
        if (hasWord(text, "watch") || text.contains("观看") || text.contains("觀看")) {
            return 65;
        }
        if (text.equals("x") || text.equals("×") || text.equals("✕")
                || text.equals("close") || hasWord(id, "close")
                || hasWord(id, "dismiss")) {
            return 60;
        }
        if (text.equals("ok") || text.equals("got it") || text.equals("确定")
                || text.equals("確定")) {
            return 45;
        }
        if (text.equals("play") || text.equals("开始") || text.equals("開始")) {
            return 40;
        }
        return 0;
    }

    private static boolean hasWord(String source, String word) {
        if (source == null || word == null || word.isEmpty()) {
            return false;
        }
        int from = 0;
        while (true) {
            int at = source.indexOf(word, from);
            if (at < 0) {
                return false;
            }
            int end = at + word.length();
            boolean left = at == 0 || !Character.isLetterOrDigit(source.charAt(at - 1));
            boolean right = end == source.length()
                    || !Character.isLetterOrDigit(source.charAt(end));
            if (left && right) {
                return true;
            }
            from = at + 1;
        }
    }

    public boolean tap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 45))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0,
                        Math.max(30, durationMs)))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    /** Screenshot through the accessibility API (Android 11+). */
    public Bitmap screenshot() {
        if (Build.VERSION.SDK_INT < 30) {
            return null;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Bitmap> result = new AtomicReference<>();
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult screenshot) {
                            HardwareBuffer buffer = screenshot.getHardwareBuffer();
                            try {
                                ColorSpace colorSpace = screenshot.getColorSpace();
                                Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace);
                                if (bitmap != null) {
                                    result.set(bitmap.copy(Bitmap.Config.ARGB_8888, false));
                                }
                            } catch (Throwable ignored) {
                                // the callback still has to release the latch
                            } finally {
                                try {
                                    buffer.close();
                                } catch (Throwable ignored) {
                                    // already closed
                                }
                                latch.countDown();
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            latch.countDown();
                        }
                    });
            latch.await(2500, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
            return null;
        }
        return result.get();
    }

    public VisionFrame screenshotFrame() {
        Bitmap bitmap = screenshot();
        if (bitmap == null) {
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
    }

    public static boolean canUseScreenshots() {
        return Build.VERSION.SDK_INT >= 30;
    }

}
