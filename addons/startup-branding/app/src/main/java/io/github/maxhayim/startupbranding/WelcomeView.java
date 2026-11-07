package io.github.maxhayim.startupbranding;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * Draws the welcome screen at any size: full screen, or small as the live preview. A 16:9 frame holds the
 * background, the picture and the text; on other screen shapes the bars around it are black.
 */
final class WelcomeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dest = new Rect();
    private Look look = new Look();
    private Bitmap bitmap;
    /** When the text animation began, 0 until the first draw; a change of animation plays it again. */
    private long animationStart;
    private static final float NEON_MS = 2200f, DECODE_MS = 700f, BOUNCE_MS = 800f, ASSEMBLE_MS = 1800f,
            SHIMMER_MS = 2400f, WAVE_MS = 1600f;
    private static final String SCRAMBLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*+=?";
    private static final float ENTER_MS = 1200f, LETTER_MS = 70f, LETTER_FADE_MS = 350f, PULSE_MS = 1800f;

    WelcomeView(Context c) {
        super(c);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        text.setTextAlign(Paint.Align.CENTER);
    }

    void show(Look l, Bitmap b) {
        if (l.animation != look.animation) animationStart = 0;
        look = l;
        text.setTypeface(Typeface.create(Look.FONT_FAMILIES[l.font], Typeface.NORMAL));
        bitmap = b;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF000000);
        int[] f = Fit.frame(getWidth(), getHeight());
        if (look.mode == Look.Mode.BLACK) return;
        paint.setColor(look.background());
        c.drawRect(f[0], f[1], f[2], f[3], paint);
        if (look.mode == Look.Mode.OFF) return;
        int fw = f[2] - f[0], fh = f[3] - f[1];
        c.save();
        c.clipRect(f[0], f[1], f[2], f[3]);
        if (bitmap != null) {
            // Original size is relative to a 1080p screen, so the preview pane shows it to scale.
            float density = fh / 1080f;
            int[] r = Fit.image(bitmap.getWidth(), bitmap.getHeight(), f, look.scale, look.align, density);
            dest.set(r[0], r[1], r[2], r[3]);
            c.drawBitmap(bitmap, null, dest, paint);
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (animationStart == 0) animationStart = now;
        float elapsed = now - animationStart;
        String line1 = look.text, line2 = look.nickname;
        if (!line1.isEmpty() || !line2.isEmpty()) {
            float big = fh * 0.075f, small = fh * 0.04f;
            float block = (line1.isEmpty() ? 0 : big * 1.2f) + (line2.isEmpty() ? 0 : small * 1.4f);
            float top;
            switch (look.place) {
                case TOP: top = f[1] + fh * 0.1f; break;
                case CENTER: top = f[1] + (fh - block) / 2f; break;
                default: top = f[3] - fh * 0.1f - block;
            }
            text.setColor(look.textColor());
            // A soft shadow keeps the words readable over a busy picture.
            int shadow = look.textColor() == 0xFFFFFFFF ? 0x99000000 : 0x66FFFFFF;
            text.setShadowLayer(fh * 0.01f, 0, fh * 0.003f, shadow);
            float y = top;
            if (!line1.isEmpty()) {
                text.setTextSize(big);
                y += big;
                drawLine(c, TextUtils.ellipsize(line1, text, fw * 0.9f, TextUtils.TruncateAt.END).toString(),
                        f[0] + fw / 2f, y, fh, elapsed, shadow);
                y += big * 0.2f;
            }
            if (!line2.isEmpty()) {
                text.setTextSize(small);
                y += small * 1.2f;
                drawLine(c, TextUtils.ellipsize(line2, text, fw * 0.9f, TextUtils.TruncateAt.END).toString(),
                        f[0] + fw / 2f, y, fh, elapsed, shadow);
            }
        }
        c.restore();
        if (running(line1.length(), elapsed)) postInvalidateOnAnimation();
    }

    /** Whether another frame changes anything. Glow Pulse keeps going for as long as the screen is up. */
    private boolean running(int letters, float elapsed) {
        switch (look.animation) {
            case NONE: return false;
            case GLOW_PULSE:
            case GOLD_SHIMMER:
            case WAVE: return true;
            case NEON_FLICKER: return elapsed < NEON_MS;
            case DECODE: return elapsed < letters * LETTER_MS + DECODE_MS;
            case BOUNCE_DROP: return elapsed < letters * LETTER_MS + BOUNCE_MS;
            case ASSEMBLE: return elapsed < ASSEMBLE_MS;
            case TYPEWRITER:
            case LETTER_BY_LETTER: return elapsed < letters * LETTER_MS + LETTER_FADE_MS;
            default: return elapsed < ENTER_MS;
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Eases out: quick at first, settling gently. */
    private static float ease(float p) {
        float rest = 1f - p;
        return 1f - rest * rest * rest;
    }

    private void drawLine(Canvas c, String line, float x, float y, int frameHeight, float elapsed, int shadow) {
        int color = look.textColor();
        float p = ease(clamp(elapsed / ENTER_MS));
        switch (look.animation) {
            case FADE_IN:
                text.setAlpha(Math.round(255 * p));
                c.drawText(line, x, y, text);
                break;
            case SLIDE_UP:
                text.setAlpha(Math.round(255 * p));
                c.drawText(line, x, y + (1f - p) * frameHeight * 0.08f, text);
                break;
            case ZOOM_IN:
                text.setAlpha(Math.round(255 * p));
                c.save();
                float scale = 0.7f + 0.3f * p;
                c.scale(scale, scale, x, y - text.getTextSize() / 3f);
                c.drawText(line, x, y, text);
                c.restore();
                break;
            case TYPEWRITER:
            case LETTER_BY_LETTER:
                drawLetters(c, line, x, y, frameHeight, elapsed);
                break;
            case GLOW_PULSE:
                float wave = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * elapsed / PULSE_MS);
                text.setShadowLayer(frameHeight * (0.01f + 0.035f * wave), 0, 0, color | 0xFF000000);
                text.setAlpha(Math.round(255 * p));
                c.drawText(line, x, y, text);
                text.setShadowLayer(frameHeight * 0.01f, 0, frameHeight * 0.003f, shadow);
                break;
            case DECODE:
                drawDecode(c, line, x, y, elapsed);
                break;
            case NEON_FLICKER:
                drawNeon(c, line, x, y, frameHeight, elapsed, shadow);
                break;
            case GOLD_SHIMMER:
                drawShimmer(c, line, x, y, elapsed);
                break;
            case WAVE:
            case BOUNCE_DROP:
            case ASSEMBLE:
                drawMoving(c, line, x, y, frameHeight, elapsed);
                break;
            case CURTAIN_REVEAL:
                c.save();
                float half = text.measureText(line) * 0.55f * p;
                c.clipRect(x - half, y - frameHeight * 0.2f, x + half, y + frameHeight * 0.1f);
                c.drawText(line, x, y, text);
                c.restore();
                break;
            default:
                c.drawText(line, x, y, text);
        }
        text.setColor(color);
    }

    /** Each letter cycles through random characters, then locks in, left to right. */
    private void drawDecode(Canvas c, String line, float x, float y, float elapsed) {
        float left = x - text.measureText(line) / 2f;
        int tick = (int) (elapsed / 60f);
        text.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < line.length(); i++) {
            float letterX = left + text.measureText(line, 0, i);
            boolean locked = elapsed >= i * LETTER_MS + DECODE_MS || line.charAt(i) == ' ';
            String shown = locked ? line.substring(i, i + 1)
                    : String.valueOf(SCRAMBLE.charAt(Math.abs((i * 31 + tick * 17) % SCRAMBLE.length())));
            text.setAlpha(locked ? 255 : 170);
            c.drawText(shown, letterX, y, text);
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** Stutters on like a neon tube, then holds with a steady glow. */
    private void drawNeon(Canvas c, String line, float x, float y, int frameHeight, float elapsed, int shadow) {
        float t = elapsed / NEON_MS;
        boolean lit = t >= 0.7f || (t > 0.08f && t < 0.14f) || (t > 0.26f && t < 0.3f)
                || (t > 0.42f && t < 0.56f) || (t > 0.62f && t < 0.66f);
        int color = look.textColor();
        text.setShadowLayer(frameHeight * (lit ? 0.03f : 0.002f), 0, 0, 0xFF36E2FF);
        text.setColor(lit ? color : 0xFF2A3138);
        c.drawText(line, x, y, text);
        text.setShadowLayer(frameHeight * 0.01f, 0, frameHeight * 0.003f, shadow);
    }

    /** Gold letters with a band of light sweeping across them, again and again. */
    private void drawShimmer(Canvas c, String line, float x, float y, float elapsed) {
        float width = text.measureText(line), band = width * 0.18f;
        float phase = (elapsed % SHIMMER_MS) / SHIMMER_MS;
        float at = x - width / 2f - band + phase * (width + 2 * band);
        text.setShader(new android.graphics.LinearGradient(at - band, 0, at + band, 0,
                new int[] {0xFFD4A437, 0xFFFFF6D6, 0xFFD4A437}, new float[] {0f, 0.5f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        text.setAlpha(255);
        c.drawText(line, x, y, text);
        text.setShader(null);
    }

    /** Letters that each move on their own: a travelling wave, a bouncing drop, or flying in to their places. */
    private void drawMoving(Canvas c, String line, float x, float y, int frameHeight, float elapsed) {
        float left = x - text.measureText(line) / 2f;
        text.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < line.length(); i++) {
            float homeX = left + text.measureText(line, 0, i), dx = 0f, dy = 0f;
            int alpha = 255;
            switch (look.animation) {
                case WAVE:
                    dy = (float) Math.sin(2 * Math.PI * (elapsed / WAVE_MS - i * 0.07f)) * frameHeight * 0.018f;
                    break;
                case BOUNCE_DROP:
                    float start = i * LETTER_MS;
                    if (elapsed < start) continue;
                    float p = clamp((elapsed - start) / BOUNCE_MS);
                    dy = -bounceLeft(p) * frameHeight * 0.35f;
                    break;
                default:   // ASSEMBLE: from a spot worked out from the letter, so each run looks the same
                    float q = ease(clamp(elapsed / ASSEMBLE_MS));
                    double angle = (i * 2.399963) % (2 * Math.PI);
                    float reach = frameHeight * (0.5f + 0.5f * ((i * 7) % 5) / 4f);
                    dx = (1f - q) * (float) Math.cos(angle) * reach;
                    dy = (1f - q) * (float) Math.sin(angle) * reach;
                    alpha = Math.round(255 * clamp(elapsed / (ASSEMBLE_MS * 0.5f)));
            }
            text.setAlpha(alpha);
            c.drawText(line, i, i + 1, homeX + dx, y + dy, text);
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** How far above its place a dropped letter is: 1 at the start, then it lands and bounces twice. */
    private static float bounceLeft(float p) {
        if (p < 0.5f) {
            float t = p / 0.5f;
            return 1f - t * t;
        }
        if (p < 0.8f) {
            float t = (p - 0.65f) / 0.15f;
            return 0.18f * (1f - t * t);
        }
        float t = (p - 0.9f) / 0.1f;
        return 0.05f * (1f - t * t);
    }

    /** One letter at a time from a fixed left edge, so the line does not shift as it grows. */
    private void drawLetters(Canvas c, String line, float x, float y, int frameHeight, float elapsed) {
        boolean typed = look.animation == Look.Animation.TYPEWRITER;
        float left = x - text.measureText(line) / 2f;
        text.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < line.length(); i++) {
            float start = i * LETTER_MS;
            if (elapsed < start) break;
            float letterX = left + text.measureText(line, 0, i);
            float p = typed ? 1f : ease(clamp((elapsed - start) / LETTER_FADE_MS));
            text.setAlpha(Math.round(255 * p));
            c.drawText(line, i, i + 1, letterX, y + (1f - p) * frameHeight * 0.03f, text);
        }
        text.setTextAlign(Paint.Align.CENTER);
    }
}
