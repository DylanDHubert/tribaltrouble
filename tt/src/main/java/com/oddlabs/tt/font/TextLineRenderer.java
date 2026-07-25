package com.oddlabs.tt.font;

import com.oddlabs.tt.render.GUIRenderer;
import com.oddlabs.util.Quad;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;

public final class TextLineRenderer {

    private TextLineRenderer() {
        // private constructor for utility class
    }

    public static void render(@NonNull GUIRenderer renderer, @NonNull TextLayout layout, float x, float y,
            @NonNull Vector4fc color) {
        render(renderer, layout, x, y, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, color);
    }

    public static void render(@NonNull GUIRenderer renderer, @NonNull TextLayout layout, float x, float y,
            float clipLeft, float clipRight, @NonNull Vector4fc color) {
        float currentY = y;
        for (TextLayout.Line line : layout.getLines()) {
            render(renderer, layout.getFont(), line.content(), x, currentY, clipLeft, clipRight, color);
            currentY -= layout.getFont().getHeight();
        }
    }

    /**
     * Render a single line of text with the provided renderer using the provided font, location and color. The text
     * will be clipped to the specified left and right bounds.
     */
    public static float render(@NonNull GUIRenderer renderer, @NonNull Font font, @NonNull CharSequence text,
            float x, float y, float clipLeft, float clipRight,
            @NonNull Vector4fc color) {
        return (float) text.codePoints().asDoubleStream().reduce(x, (currentX, codePointAsDouble) -> {
            int codePoint = (int) codePointAsDouble;

            if (codePoint == '\n') {
                // This renderer doesn't handle newlines, that's the layout's job
                return currentX;
            }

            Quad quad = font.getQuad(codePoint);
            if (quad != null) {
                float quadWidth = quad.getWidth();
                float charAdvance = quadWidth - font.getXBorder();

                // Check if the character is completely outside the clipping region
                if (currentX + quadWidth < clipLeft || currentX > clipRight) {
                    return currentX + charAdvance;
                }

                // By this point, we know at least part of the character is visible.
                // We need to calculate the visible portion and adjust texture coordinates.

                float renderX = (float) currentX;
                float renderWidth = quadWidth;
                float u1 = quad.getU1();
                float u2 = quad.getU2();
                float textureUWidth = u2 - u1;

                // Handle left clipping
                if (renderX < clipLeft) {
                    float leftClipPixels = clipLeft - renderX;
                    float leftClipRatio = leftClipPixels / quadWidth;
                    u1 += textureUWidth * leftClipRatio;
                    renderWidth -= leftClipPixels;
                    renderX = clipLeft;
                }

                // Handle right clipping
                if (renderX + renderWidth > clipRight) {
                    float rightClipPixels = (renderX + renderWidth) - clipRight;
                    float rightClipRatio = rightClipPixels / quadWidth;
                    u2 -= textureUWidth * rightClipRatio;
                    renderWidth -= rightClipPixels;
                }

                if (renderWidth > 0) {
                    renderer.drawTexture(font.getTexture(), renderX, y, renderWidth, quad.getHeight(), u1, quad.getV1(),
                            u2, quad.getV2(), color);
                }
                return currentX + charAdvance;
            }
            return currentX;
        });
    }

    /**
     * Render one line at a uniform scale. Useful for compact overlays that share an existing
     * bitmap font rather than loading a near-duplicate font asset.
     */
    public static float renderScaled(
            @NonNull GUIRenderer renderer,
            @NonNull Font font,
            @NonNull CharSequence text,
            float x,
            float y,
            float clipLeft,
            float clipRight,
            float scale,
            @NonNull Vector4fc color) {
        float currentX = x;
        for (int codePoint : text.codePoints().toArray()) {
            if (codePoint == '\n') {
                continue;
            }

            Quad quad = font.getQuad(codePoint);
            if (quad == null) {
                continue;
            }

            float quadWidth = quad.getWidth() * scale;
            float charAdvance = (quad.getWidth() - font.getXBorder()) * scale;
            float renderX = currentX;
            float renderWidth = quadWidth;
            float u1 = quad.getU1();
            float u2 = quad.getU2();
            float textureUWidth = u2 - u1;

            if (renderX < clipLeft) {
                float leftClip = clipLeft - renderX;
                u1 += textureUWidth * (leftClip / quadWidth);
                renderWidth -= leftClip;
                renderX = clipLeft;
            }
            if (renderX + renderWidth > clipRight) {
                float rightClip = renderX + renderWidth - clipRight;
                u2 -= textureUWidth * (rightClip / quadWidth);
                renderWidth -= rightClip;
            }

            if (renderWidth > 0f && renderX <= clipRight && renderX + renderWidth >= clipLeft) {
                renderer.drawTexture(
                        font.getTexture(),
                        renderX,
                        y,
                        renderWidth,
                        quad.getHeight() * scale,
                        u1,
                        quad.getV1(),
                        u2,
                        quad.getV2(),
                        color);
            }
            currentX += charAdvance;
        }
        return currentX;
    }
}
