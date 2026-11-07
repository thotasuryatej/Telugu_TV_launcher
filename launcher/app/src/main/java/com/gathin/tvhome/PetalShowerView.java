package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * A transparent full-screen overlay that plays a one-off shower of marigold petals. Each petal drops in from
 * just above the top edge, gathers speed as it falls, sways from side to side, spins, flips over (showing its
 * darker back) and fades out over the last quarter of its fall. Everything finishes within the given duration,
 * then the callback runs on the main thread. The view takes no focus, clicks or accessibility, uses a hardware
 * layer only while playing, and allocates nothing per frame.
 */
public final class PetalShowerView extends View {

    private static final int[] COLORS = {0xFFFF8C1A, 0xFFFFB627, 0xFFF26A0D, 0xFFFFC93C, 0xFFE85D04};
    private static final int EDGE = 0xFF7A2800;
    private static final double TAU = 2 * Math.PI;

    private final float density;
    private final Random rnd = new Random();
    private final Path[] shapes = new Path[3];
    private final Path[] veins = new Path[3];
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vein = new Paint(Paint.ANTI_ALIAS_FLAG);

    // per-petal state, grown only when a bigger shower is asked for
    private int count;
    private float[] xFrac = new float[0], delay = new float[0], life = new float[0], drop = new float[0],
            size = new float[0], swayAmp = new float[0], swayFreq = new float[0], swayPhase = new float[0],
            rot0 = new float[0], spin = new float[0], flipFreq = new float[0], flipPhase = new float[0];
    private int[] color = new int[0], shape = new int[0];

    private boolean playing;
    private long start, duration;
    private Runnable onDone;
    private final Runnable finish = this::finish;

    public PetalShowerView(Context context) {
        this(context, null);
    }

    public PetalShowerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PetalShowerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);

        Random seeded = new Random(0x9E7A1L);
        for (int i = 0; i < shapes.length; i++) {
            shapes[i] = petalShape(seeded);
            veins[i] = veinPath(seeded);
        }
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(0.045f);
        edge.setStrokeJoin(Paint.Join.ROUND);
        vein.setStyle(Paint.Style.STROKE);
        vein.setStrokeWidth(0.035f);
        vein.setStrokeCap(Paint.Cap.ROUND);
        vein.setColor(EDGE);
    }

    private int[] palette = COLORS;
    private boolean rises;

    /** A FestivalCalendar effect: the shapes and colours that go with that festival. */
    void setEffect(int effect) {
        Path shape = null;
        switch (effect) {
            case FestivalCalendar.SPARKS:
                palette = new int[] {0xFFFFD36B, 0xFFFFB23C, 0xFFFFF1B8, 0xFFFF8C1A};
                shape = circle();
                rises = true;
                break;
            case FestivalCalendar.LEAVES:
                palette = new int[] {0xFF4CAF50, 0xFF7BC96F, 0xFF2E8B57, 0xFF9CCC65};
                shape = leaf();
                break;
            case FestivalCalendar.KITES:
                palette = new int[] {0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047, 0xFF8E24AA};
                shape = kite();
                break;
            case FestivalCalendar.FLOWERS:
                palette = new int[] {0xFFF06DB0, 0xFFFFD54F, 0xFFBA68C8, 0xFFFF8A65, 0xFF81C784};
                break;
            case FestivalCalendar.BALLOONS:
                palette = new int[] {0xFFE53935, 0xFFFFB300, 0xFF1E88E5, 0xFF43A047, 0xFFD81B60, 0xFF8E24AA};
                shape = balloon();
                rises = true;
                break;
            case FestivalCalendar.HEARTS:
                palette = new int[] {0xFFFF4D6D, 0xFFFF758F, 0xFFC9184A, 0xFFFFB3C1};
                shape = heart();
                rises = true;
                break;
            case FestivalCalendar.CONFETTI:
                palette = new int[] {0xFFFFD36B, 0xFF4FC3F7, 0xFFFF5F8F, 0xFF81C784, 0xFFBA68C8, 0xFFFFFFFF};
                shape = confetti();
                break;
            case FestivalCalendar.COLOURS:
                palette = new int[] {0xFFFF4081, 0xFF00E5FF, 0xFFFFEA00, 0xFF76FF03, 0xFFE040FB, 0xFFFF9100};
                shape = circle();
                break;
            default:
                break;
        }
        if (shape != null) {
            Path none = new Path();
            for (int i = 0; i < shapes.length; i++) {
                shapes[i] = shape;
                veins[i] = none;
            }
        }
    }

    private static Path balloon() {
        Path p = new Path();
        p.addOval(new android.graphics.RectF(-0.42f, -0.65f, 0.42f, 0.35f), Path.Direction.CW);
        p.moveTo(-0.07f, 0.34f);
        p.lineTo(0.07f, 0.34f);
        p.lineTo(0f, 0.46f);
        p.close();
        return p;
    }

    private static Path heart() {
        Path p = new Path();
        p.moveTo(0f, 0.55f);
        p.cubicTo(-0.75f, 0f, -0.45f, -0.6f, 0f, -0.22f);
        p.cubicTo(0.45f, -0.6f, 0.75f, 0f, 0f, 0.55f);
        p.close();
        return p;
    }

    private static Path confetti() {
        Path p = new Path();
        p.addRect(-0.3f, -0.5f, 0.3f, 0.5f, Path.Direction.CW);
        return p;
    }

    private static Path circle() {
        Path p = new Path();
        p.addCircle(0f, 0f, 0.42f, Path.Direction.CW);
        return p;
    }

    private static Path leaf() {
        Path p = new Path();
        p.moveTo(0f, 0.6f);
        p.cubicTo(-0.34f, 0.2f, -0.3f, -0.3f, 0f, -0.6f);
        p.cubicTo(0.3f, -0.3f, 0.34f, 0.2f, 0f, 0.6f);
        p.close();
        return p;
    }

    private static Path kite() {
        Path p = new Path();
        p.moveTo(0f, -0.65f);
        p.lineTo(0.48f, -0.05f);
        p.lineTo(0f, 0.65f);
        p.lineTo(-0.48f, -0.05f);
        p.close();
        return p;
    }

    /**
     * Plays a shower of {@code petals} petals lasting {@code durationMs}, then runs {@code done} on the main
     * thread. Playing again before the last shower ends finishes that one first (its callback runs).
     */
    public void play(int petals, long durationMs, Runnable done) {
        if (playing) {
            removeCallbacks(finish);
            finish();
        }
        int n = Math.max(0, petals);
        ensureCapacity(n);
        count = n;
        duration = Math.max(1, durationMs);
        onDone = done;
        for (int i = 0; i < n; i++) {
            xFrac[i] = (i + rnd.nextFloat()) / n;          // spread evenly across the width
            delay[i] = rnd.nextFloat() * 0.32f;             // fractions of the duration
            life[i] = 0.6f + rnd.nextFloat() * (1f - delay[i] - 0.6f);
            drop[i] = 0.8f + rnd.nextFloat() * 0.35f;       // fraction of the height fallen over its life
            size[i] = 9f + rnd.nextFloat() * 7f;            // dp
            swayAmp[i] = 10f + rnd.nextFloat() * 24f;       // dp
            swayFreq[i] = 0.5f + rnd.nextFloat() * 0.6f;    // Hz
            swayPhase[i] = rnd.nextFloat() * (float) TAU;
            rot0[i] = rnd.nextFloat() * 360f;
            spin[i] = (40f + rnd.nextFloat() * 120f) * (rnd.nextBoolean() ? 1f : -1f); // degrees per second
            flipFreq[i] = 0.4f + rnd.nextFloat();           // Hz
            flipPhase[i] = rnd.nextFloat() * (float) TAU;
            color[i] = rnd.nextInt(palette.length);
            shape[i] = rnd.nextInt(shapes.length);
        }
        playing = true;
        start = SystemClock.uptimeMillis();
        postDelayed(finish, duration);
        postInvalidateOnAnimation();
    }

    private void finish() {
        if (!playing) return;
        playing = false;
        invalidate();
        Runnable done = onDone;
        onDone = null;
        if (done != null) done.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (playing) {
            removeCallbacks(finish);
            finish();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!playing) return;
        long now = SystemClock.uptimeMillis();
        float elapsed = (now - start) / (float) duration;
        float secs = (now - start) / 1000f;
        float w = getWidth(), h = getHeight();
        for (int i = 0; i < count; i++) {
            float p = (elapsed - delay[i]) / life[i];
            if (p <= 0f || p >= 1f) continue;
            float len = size[i] * density;
            float fall = (p + p * p) * 0.5f;               // gathers speed, like a petal against the air
            float y = -len * 1.5f + (h * drop[i] + len * 1.5f) * fall;
            if (rises) y = h - y;                          // sparks go up, like lamp light and sparklers
            double sway = TAU * swayFreq[i] * secs + swayPhase[i];
            float x = xFrac[i] * w + swayAmp[i] * density * (float) Math.sin(sway);
            float angle = rot0[i] + spin[i] * secs + 22f * (float) Math.cos(sway); // tilts into the sway
            float flip = (float) Math.cos(TAU * flipFreq[i] * secs + flipPhase[i]);
            float sx = Math.abs(flip) < 0.08f ? (flip < 0f ? -0.08f : 0.08f) : flip;
            int a = p > 0.75f ? (int) (255 * (1f - (p - 0.75f) / 0.25f)) : 255;

            // turned edge-on it looks darker; the back is darker still
            float shade = (flip < 0f ? 0.78f : 1f) * (0.8f + 0.2f * Math.abs(flip));
            int base = palette[color[i]];
            fill.setColor(scale(base, shade));
            fill.setAlpha(a);
            edge.setColor(scale(base, 0.55f));
            edge.setAlpha(a * 3 / 4);
            vein.setAlpha(a / 4);

            c.save();
            c.translate(x, y);
            c.rotate(angle);
            c.scale(len * sx, len);
            c.drawPath(shapes[shape[i]], fill);
            c.drawPath(shapes[shape[i]], edge);
            c.drawPath(veins[shape[i]], vein);
            c.restore();
        }
        postInvalidateOnAnimation();
    }

    private void ensureCapacity(int n) {
        if (xFrac.length >= n) return;
        xFrac = new float[n];
        delay = new float[n];
        life = new float[n];
        drop = new float[n];
        size = new float[n];
        swayAmp = new float[n];
        swayFreq = new float[n];
        swayPhase = new float[n];
        rot0 = new float[n];
        spin = new float[n];
        flipFreq = new float[n];
        flipPhase = new float[n];
        color = new int[n];
        shape = new int[n];
    }

    /** One marigold petal, 1 unit tall around the origin: a narrow base fanning out to a frilled top. */
    private static Path petalShape(Random r) {
        Path p = new Path();
        float wTop = 0.36f + 0.08f * r.nextFloat();
        p.moveTo(0f, 0.5f);
        p.cubicTo(-0.16f, 0.36f, -wTop - 0.04f, 0.02f, -wTop, -0.3f);
        int scallops = 4 + r.nextInt(2);
        for (int k = 0; k < scallops; k++) {
            float tx = -wTop + 2 * wTop * (k + 0.5f) / scallops;
            float nx = -wTop + 2 * wTop * (k + 1f) / scallops;
            float tipY = -0.5f + 0.2f * sq(tx / wTop) - 0.06f - 0.05f * r.nextFloat();
            float notchY = k == scallops - 1 ? -0.3f : -0.5f + 0.2f * sq(nx / wTop) + 0.05f + 0.03f * r.nextFloat();
            p.quadTo(tx, tipY, nx, notchY);
        }
        p.cubicTo(wTop + 0.04f, 0.02f, 0.16f, 0.36f, 0f, 0.5f);
        p.close();
        return p;
    }

    private static Path veinPath(Random r) {
        Path p = new Path();
        float bend = (r.nextFloat() - 0.5f) * 0.06f;
        p.moveTo(0f, 0.42f);
        p.quadTo(bend, 0f, 0f, -0.36f);
        p.moveTo(0f, 0.3f);
        p.quadTo(-0.12f, -0.05f, -0.2f, -0.3f);
        p.moveTo(0f, 0.3f);
        p.quadTo(0.12f, -0.05f, 0.2f, -0.3f);
        return p;
    }

    private static float sq(float v) {
        return v * v;
    }

    private static int scale(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((c >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((c & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
