import com.mmunki.plugin251428.core.DoublePressDetector;
import com.mmunki.plugin251428.core.GameConfig;
import com.mmunki.plugin251428.core.GamePlan;
import com.mmunki.plugin251428.core.SoccerBrain;
import com.mmunki.plugin251428.core.SoccerVision;
import com.mmunki.plugin251428.core.VisionFrame;

public final class CoreTests {
    private static int passed;

    public static void main(String[] args) {
        doublePress();
        noBall();
        nonFieldDoesNothing();
        ballAndGoal();
        aimAvoidsBlocker();
        brainProducesSwipe();
        System.out.println("OK " + passed + " assertions");
    }

    private static void doublePress() {
        DoublePressDetector detector = new DoublePressDetector(650, 45);
        check(!detector.onPress(1000), "single press is not a double");
        check(detector.onPress(1200), "two fast presses trigger");
        check(!detector.onPress(1400), "third press does not retrigger");
        check(detector.onPress(1700), "next sequence triggers");
        check(!detector.onPress(3000), "new sequence first press");
        check(!detector.onPress(3020), "too-fast press is ignored");
        check(detector.onPress(3100), "press after the ignored bounce triggers");
    }

    private static void noBall() {
        VisionFrame frame = field(360, 640);
        check(SoccerVision.findBall(frame, null) == null,
                "empty field has no ball");
    }

    private static void nonFieldDoesNothing() {
        int[] pixels = new int[160 * 240];
        java.util.Arrays.fill(pixels, 0xffffffff);
        VisionFrame white = new VisionFrame(160, 240, pixels);
        check(!SoccerVision.looksLikeField(white), "white screen is not a field");
        SoccerBrain.Decision decision = SoccerBrain.think(white, null,
                GameConfig.SIDE_AUTO, 80);
        check(!decision.plan.isAction(), "no gesture is sent on a non-field screen");
    }

    private static void ballAndGoal() {
        VisionFrame frame = field(360, 640);
        disc(frame, 90, 480, 7, 0xfff6f6f6);
        disc(frame, 45, 300, 5, 0xfff0f0f0);
        check("right".equals(SoccerVision.detectGoalBand(frame)),
                "bright right band is detected as the goal");
        SoccerVision.Blob ball = SoccerVision.findBall(frame, null);
        check(ball != null, "ball is found");
        check(Math.abs(ball.px - 90) < 18 && Math.abs(ball.py - 480) < 18,
                "ball position is close, was " + ball.px + "," + ball.py);
        SoccerVision.Blob tracked = SoccerVision.findBall(frame, ball);
        check(tracked != null && Math.abs(tracked.px - ball.px) < 18,
                "ball can be tracked between frames");
    }

    private static void aimAvoidsBlocker() {
        VisionFrame frame = field(360, 640);
        disc(frame, 90, 480, 7, 0xfff6f6f6);
        disc(frame, 320, 281, 4, 0xffffffff);
        disc(frame, 280, 330, 24, 0xffcc2222);
        SoccerVision.Blob ball = SoccerVision.findBall(frame, null);
        check(ball != null, "blocked frame still finds the ball");
        SoccerVision.Aim aim = SoccerVision.chooseAim(frame, ball,
                GameConfig.SIDE_RIGHT);
        check(aim != null, "an aim point is produced");
        check(Math.abs(aim.py - 281) > 40,
                "aim moves off the blocked centre lane, y=" + aim.py);
    }

    private static void brainProducesSwipe() {
        VisionFrame frame = field(360, 640);
        disc(frame, 90, 480, 7, 0xfff6f6f6);
        disc(frame, 320, 281, 4, 0xffffffff);
        SoccerBrain.Decision decision = SoccerBrain.think(frame, null,
                GameConfig.SIDE_RIGHT, 70);
        check(decision.ball != null, "brain keeps the ball");
        check(GamePlan.KIND_SWIPE.equals(decision.plan.kind),
                "brain produces a swipe");
        check(decision.plan.x1 == decision.ball.px,
                "swipe starts on the ball");
    }

    private static VisionFrame field(int width, int height) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int shade = ((x / 24) + (y / 24)) % 2 == 0 ? 0 : 6;
                pixels[y * width + x] = 0xff000000
                        | (28 << 16) | ((118 + shade) << 8) | 38;
            }
        }
        VisionFrame frame = new VisionFrame(width, height, pixels);
        rect(frame, 330, 190, width - 1, 450, 0xfff4f4f4);
        return frame;
    }

    private static void rect(VisionFrame frame, int left, int top, int right, int bottom,
                             int color) {
        for (int y = Math.max(0, top); y <= Math.min(frame.height - 1, bottom); y++) {
            for (int x = Math.max(0, left); x <= Math.min(frame.width - 1, right); x++) {
                frame.argb[y * frame.width + x] = color;
            }
        }
    }

    private static void disc(VisionFrame frame, int cx, int cy, int radius, int color) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (x < 0 || y < 0 || x >= frame.width || y >= frame.height) {
                    continue;
                }
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius) {
                    frame.argb[y * frame.width + x] = color;
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        passed++;
    }
}
