package com.mmunki.plugin251428.core;

/**
 * Turns a screen frame into one gesture. The plan is intentionally short-lived:
 * take a frame, decide, act, wait for the reaction time, repeat. That keeps the
 * bot responsive when the ball moves and avoids long scripted sequences that
 * break as soon as the game does something unexpected.
 */
public final class SoccerBrain {
    public static final class Decision {
        public final GamePlan plan;
        public final SoccerVision.Blob ball;

        Decision(GamePlan plan, SoccerVision.Blob ball) {
            this.plan = plan;
            this.ball = ball;
        }
    }

    private SoccerBrain() {
    }

    public static Decision think(VisionFrame frame, SoccerVision.Blob previousBall,
                                 String goalSide, int aggressiveness) {
        if (!SoccerVision.looksLikeField(frame)) {
            return new Decision(GamePlan.none("not a soccer field"), null);
        }
        int aggr = Math.max(0, Math.min(100, aggressiveness));
        SoccerVision.Blob ball = SoccerVision.findBall(frame, previousBall);
        if (ball == null) {
            int[] goal = SoccerVision.goalPoint(frame, goalSide);
            int sx = Math.round(frame.width * 0.50f);
            int sy = Math.round(frame.height * 0.76f);
            int ex = sx + (goal[0] - sx) / 3;
            int ey = sy + (goal[1] - sy) / 3;
            return new Decision(GamePlan.swipe(sx, sy, ex, ey, 180,
                    "ball not visible, sweep toward goal", 0.25f), null);
        }

        SoccerVision.Aim aim = SoccerVision.chooseAim(frame, ball, goalSide);
        float dx = aim.px - ball.px;
        float dy = aim.py - ball.py;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float diagonal = (float) Math.sqrt(frame.width * (double) frame.width
                + frame.height * (double) frame.height);
        float range = distance / diagonal;
        float shotRange = 0.30f + (100 - aggr) / 100f * 0.20f;
        boolean shoot = range <= shotRange;
        float power = shoot ? 1.0f : 0.52f + aggr / 260f;
        int ex = Math.round(ball.px + dx * power);
        int ey = Math.round(ball.py + dy * power);
        ex = clamp(ex, 2, frame.width - 3);
        ey = clamp(ey, 2, frame.height - 3);
        int duration = shoot ? 200 : 150;
        String reason = shoot ? "shot at " + aim.note : "dribble at " + aim.note;
        float confidence = Math.min(0.99f, ball.confidence * 0.6f
                + (aim.penalty < 0.02f ? 0.35f : 0.18f));
        return new Decision(GamePlan.swipe(ball.px, ball.py, ex, ey, duration,
                reason, confidence), ball);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
