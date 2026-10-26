package com.mmunki.plugin251428.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Small, dependency-free vision layer for top-down / side-view soccer games.
 *
 * It does not try to be a neural network. It finds the ball as a compact bright
 * blob, finds coloured (kit) blobs between the ball and the goal, and scores a
 * set of aim points by how much of the lane is free. That is fast enough to run
 * several times per second on a phone and is the part that can be tuned per
 * game with the goal-side setting.
 */
public final class SoccerVision {
    public static final class Blob {
        public final int px;
        public final int py;
        public final int area;
        public final float confidence;

        Blob(int px, int py, int area, float confidence) {
            this.px = px;
            this.py = py;
            this.area = area;
            this.confidence = confidence;
        }
    }

    public static final class Aim {
        public final int px;
        public final int py;
        public final float penalty;
        public final String note;

        Aim(int px, int py, float penalty, String note) {
            this.px = px;
            this.py = py;
            this.penalty = penalty;
            this.note = note;
        }
    }

    private SoccerVision() {
    }

    /** Rejects menus, launchers and other screens before any gesture is sent. */
    public static boolean looksLikeField(VisionFrame frame) {
        int step = Math.max(4, Math.round(frame.width / 120f));
        int green = 0;
        int total = 0;
        for (int y = Math.round(frame.height * 0.04f); y < frame.height * 0.98f; y += step) {
            for (int x = 0; x < frame.width; x += step) {
                int r = frame.red(x, y);
                int g = frame.green(x, y);
                int b = frame.blue(x, y);
                if (g > 48 && g > r * 1.12f && g > b * 1.08f) {
                    green++;
                }
                total++;
            }
        }
        return total > 0 && green / (float) total >= 0.15f;
    }

    public static Blob findBall(VisionFrame frame, Blob previous) {
        int step = Math.max(2, Math.round(frame.width / 180f));
        int gw = Math.max(1, frame.width / step);
        int gh = Math.max(1, frame.height / step);
        boolean[] mask = new boolean[gw * gh];
        for (int gy = 0; gy < gh; gy++) {
            int y = Math.min(frame.height - 1, gy * step + step / 2);
            for (int gx = 0; gx < gw; gx++) {
                int x = Math.min(frame.width - 1, gx * step + step / 2);
                int r = frame.red(x, y);
                int g = frame.green(x, y);
                int b = frame.blue(x, y);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                boolean playArea = gy > gh * 0.08f && gy < gh * 0.94f;
                mask[gy * gw + gx] = playArea && min > 158 && (max - min) < 62;
            }
        }

        int total = gw * gh;
        int maxArea = Math.max(24, total / 14);
        boolean[] seen = new boolean[total];
        int[] stack = new int[total];
        Blob best = null;
        float bestScore = 0f;

        for (int start = 0; start < total; start++) {
            if (!mask[start] || seen[start]) {
                continue;
            }
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            int area = 0;
            int minX = gw;
            int maxX = -1;
            int minY = gh;
            int maxY = -1;
            int sumX = 0;
            int sumY = 0;
            while (top > 0) {
                int cell = stack[--top];
                int cx = cell % gw;
                int cy = cell / gw;
                area++;
                sumX += cx;
                sumY += cy;
                if (cx < minX) minX = cx;
                if (cx > maxX) maxX = cx;
                if (cy < minY) minY = cy;
                if (cy > maxY) maxY = cy;
                if (area > maxArea) {
                    break;
                }
                top = push(cell - 1, mask, seen, stack, top, gw);
                top = push(cell + 1, mask, seen, stack, top, gw);
                top = push(cell - gw, mask, seen, stack, top, gw);
                top = push(cell + gw, mask, seen, stack, top, gw);
            }
            if (area < 2 || area > maxArea) {
                continue;
            }
            int bw = maxX - minX + 1;
            int bh = maxY - minY + 1;
            float aspect = bw / (float) bh;
            if (aspect < 0.30f || aspect > 3.3f) {
                continue;
            }
            if (minX == 0 || minY == 0 || maxX == gw - 1 || maxY == gh - 1) {
                continue;
            }
            float fill = area / (float) (bw * bh);
            if (fill < 0.38f) {
                continue;
            }
            float cx = (sumX / (float) area) * step + step * 0.5f;
            float cy = (sumY / (float) area) * step + step * 0.5f;
            float nx = cx / frame.width;
            float ny = cy / frame.height;
            float shape = Math.min(1f, fill / 0.62f);
            float size = Math.min(1f, area / 30f);
            float score = shape * (0.55f + 0.45f * size);
            score *= 1f + 0.18f * ny;
            if (previous != null) {
                float dx = nx - previous.px / (float) frame.width;
                float dy = ny - previous.py / (float) frame.height;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < 0.22f) {
                    score *= 1.45f;
                } else if (dist > 0.55f) {
                    score *= 0.82f;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = new Blob(Math.round(cx), Math.round(cy), area,
                        Math.min(1f, score / 1.5f));
            }
        }
        return bestScore >= 0.24f ? best : null;
    }

    private static int push(int cell, boolean[] mask, boolean[] seen, int[] stack, int top, int gw) {
        if (cell < 0 || cell >= mask.length || !mask[cell] || seen[cell]) {
            return top;
        }
        int x = cell % gw;
        int y = cell / gw;
        if (x < 0 || y < 0 || x >= gw || y >= mask.length / gw) {
            return top;
        }
        seen[cell] = true;
        stack[top++] = cell;
        return top;
    }

    /** Picks a goal point. With "auto" the brightest of the four bands wins. */
    public static int[] goalPoint(VisionFrame frame, String side) {
        String resolved = side;
        if (GameConfig.SIDE_AUTO.equals(side) || side == null) {
            resolved = detectGoalBand(frame);
        }
        switch (resolved) {
            case GameConfig.SIDE_LEFT:
                return new int[]{Math.round(frame.width * 0.055f), Math.round(frame.height * 0.44f)};
            case GameConfig.SIDE_TOP:
                return new int[]{Math.round(frame.width * 0.50f), Math.round(frame.height * 0.075f)};
            case GameConfig.SIDE_BOTTOM:
                return new int[]{Math.round(frame.width * 0.50f), Math.round(frame.height * 0.925f)};
            case GameConfig.SIDE_RIGHT:
            default:
                return new int[]{Math.round(frame.width * 0.945f), Math.round(frame.height * 0.44f)};
        }
    }

    public static String detectGoalBand(VisionFrame frame) {
        int step = Math.max(4, frame.width / 180);
        int[] counts = new int[4]; // left, right, top, bottom
        int[] totals = new int[4];
        for (int y = 0; y < frame.height; y += step) {
            for (int x = 0; x < frame.width; x += step) {
                int r = frame.red(x, y);
                int g = frame.green(x, y);
                int b = frame.blue(x, y);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                boolean white = min > 150 && max - min < 55;
                if (!white) {
                    continue;
                }
                float nx = x / (float) frame.width;
                float ny = y / (float) frame.height;
                if (ny > 0.18f && ny < 0.82f) {
                    if (nx < 0.10f) {
                        counts[0]++;
                    } else if (nx > 0.90f) {
                        counts[1]++;
                    }
                }
                if (nx > 0.18f && nx < 0.82f) {
                    if (ny < 0.10f) {
                        counts[2]++;
                    } else if (ny > 0.90f) {
                        counts[3]++;
                    }
                }
            }
        }
        totals[0] = totals[1] = Math.max(1, (int) (frame.height / (float) step) / 5);
        totals[2] = totals[3] = Math.max(1, (int) (frame.width / (float) step) / 5);
        int best = 1;
        float bestDensity = -1f;
        for (int i = 0; i < 4; i++) {
            float density = counts[i] / (float) totals[i];
            if (density > bestDensity) {
                bestDensity = density;
                best = i;
            }
        }
        if (bestDensity < 0.02f) {
            return GameConfig.SIDE_TOP;
        }
        switch (best) {
            case 0:
                return GameConfig.SIDE_LEFT;
            case 1:
                return GameConfig.SIDE_RIGHT;
            case 3:
                return GameConfig.SIDE_BOTTOM;
            case 2:
            default:
                return GameConfig.SIDE_TOP;
        }
    }

    public static Aim chooseAim(VisionFrame frame, Blob ball, String side) {
        int[] goal = goalPoint(frame, side);
        List<Blob> kit = findKitBlobs(frame);
        boolean verticalGoal = goal[1] < frame.height * 0.18f
                || goal[1] > frame.height * 0.82f;
        float spread = 0.24f;
        Aim best = null;
        for (int i = -2; i <= 2; i++) {
            int tx = goal[0];
            int ty = goal[1];
            if (verticalGoal) {
                tx = clamp(Math.round(goal[0] + i * spread / 2f * frame.width),
                        Math.round(frame.width * 0.10f), Math.round(frame.width * 0.90f));
            } else {
                ty = clamp(Math.round(goal[1] + i * spread / 2f * frame.height),
                        Math.round(frame.height * 0.12f), Math.round(frame.height * 0.88f));
            }
            float penalty = Math.abs(i) * 0.06f + lanePenalty(ball, tx, ty, frame, kit);
            if (best == null || penalty < best.penalty) {
                best = new Aim(tx, ty, penalty,
                        penalty < 0.02f ? "open lane" : "avoiding players");
            }
        }
        if (best == null) {
            return new Aim(goal[0], goal[1], 0f, "goal centre");
        }
        return best;
    }

    private static float lanePenalty(Blob ball, int tx, int ty, VisionFrame frame, List<Blob> kit) {
        float bx = ball.px / (float) frame.width;
        float by = ball.py / (float) frame.height;
        float ex = tx / (float) frame.width;
        float ey = ty / (float) frame.height;
        float penalty = 0f;
        for (Blob blob : kit) {
            float kx = blob.px / (float) frame.width;
            float ky = blob.py / (float) frame.height;
            float radius = Math.max(0.018f, (float) Math.sqrt(blob.area) * 2.1f
                    / Math.max(frame.width, frame.height));
            for (int s = 2; s <= 9; s++) {
                float t = s / 10f;
                float lx = bx + (ex - bx) * t;
                float ly = by + (ey - by) * t;
                float dx = lx - kx;
                float dy = ly - ky;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float corridor = radius + 0.026f;
                if (dist < corridor) {
                    penalty += (corridor - dist) / corridor * 0.20f;
                }
            }
        }
        return penalty;
    }

    public static List<Blob> findKitBlobs(VisionFrame frame) {
        int step = Math.max(3, Math.round(frame.width / 140f));
        int gw = Math.max(1, frame.width / step);
        int gh = Math.max(1, frame.height / step);
        boolean[] mask = new boolean[gw * gh];
        for (int gy = 0; gy < gh; gy++) {
            int y = Math.min(frame.height - 1, gy * step + step / 2);
            for (int gx = 0; gx < gw; gx++) {
                int x = Math.min(frame.width - 1, gx * step + step / 2);
                int r = frame.red(x, y);
                int g = frame.green(x, y);
                int b = frame.blue(x, y);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                boolean greenField = g > r * 1.14f && g > b * 1.10f && g > 55;
                mask[gy * gw + gx] = !greenField && max > 72 && (max - min) > 58
                        && y > frame.height * 0.12f && y < frame.height * 0.96f;
            }
        }
        int total = gw * gh;
        int maxArea = Math.max(30, total / 16);
        boolean[] seen = new boolean[total];
        int[] stack = new int[total];
        List<Blob> blobs = new ArrayList<>();
        for (int start = 0; start < total; start++) {
            if (!mask[start] || seen[start]) {
                continue;
            }
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            int area = 0;
            int sumX = 0;
            int sumY = 0;
            int minX = gw;
            int maxX = -1;
            int minY = gh;
            int maxY = -1;
            while (top > 0) {
                int cell = stack[--top];
                int cx = cell % gw;
                int cy = cell / gw;
                area++;
                sumX += cx;
                sumY += cy;
                if (cx < minX) minX = cx;
                if (cx > maxX) maxX = cx;
                if (cy < minY) minY = cy;
                if (cy > maxY) maxY = cy;
                if (area > maxArea) {
                    break;
                }
                top = push(cell - 1, mask, seen, stack, top, gw);
                top = push(cell + 1, mask, seen, stack, top, gw);
                top = push(cell - gw, mask, seen, stack, top, gw);
                top = push(cell + gw, mask, seen, stack, top, gw);
            }
            if (area < 4 || area > maxArea) {
                continue;
            }
            int bw = maxX - minX + 1;
            int bh = maxY - minY + 1;
            float aspect = bw / (float) bh;
            if (aspect < 0.22f || aspect > 4.5f || bw >= gw - 2 || bh >= gh - 2) {
                continue;
            }
            int px = Math.round((sumX / (float) area) * step + step * 0.5f);
            int py = Math.round((sumY / (float) area) * step + step * 0.5f);
            blobs.add(new Blob(px, py, area, 0.5f));
        }
        blobs.sort((a, b) -> Integer.compare(b.area, a.area));
        if (blobs.size() > 18) {
            return new ArrayList<>(blobs.subList(0, 18));
        }
        return blobs;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
