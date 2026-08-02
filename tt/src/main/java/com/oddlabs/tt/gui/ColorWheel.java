package com.oddlabs.tt.gui;

import com.oddlabs.tt.guievent.MouseButtonListener;
import com.oddlabs.tt.guievent.MouseMotionListener;
import com.oddlabs.tt.render.GUIRenderer;
import com.oddlabs.tt.render.Texture;
import com.oddlabs.tt.resource.GLImage;
import com.oddlabs.tt.resource.GLIntImage;
import com.oddlabs.util.Color;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Standard HSV color wheel: hue and saturation from click/drag on the disc, brightness via {@link #setBrightness}.
 */
public final class ColorWheel extends GUIObject {
    private static final int TEXTURE_SIZE = 128;
    private static final int MARKER_SIZE = 6;

    private final @NonNull Texture wheel_texture;
    private final float radius;
    private final Set<@NonNull Runnable> color_change_listeners = new CopyOnWriteArraySet<>();

    private float hue;
    private float saturation;
    private float brightness = 1f;
    private float marker_x;
    private float marker_y;

    public ColorWheel(int size) {
        setDim(size, size);
        setCanFocus(true);
        radius = size / 2f - 2f;
        wheel_texture = createWheelTexture(TEXTURE_SIZE);
        updateMarkerPosition();

        WheelListener listener = new WheelListener();
        addMouseMotionListener(listener);
        addMouseButtonListener(listener);
    }

    private static @NonNull Texture createWheelTexture(int size) {
        GLIntImage image = new GLIntImage(size, size, GL11.GL_RGBA);
        float center = size / 2f;
        float max_radius = center - 1f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center + 0.5f;
                float dy = y - center + 0.5f;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > max_radius) {
                    image.putPixel(x, y, 0);
                    continue;
                }
                float angle = (float) Math.atan2(dy, dx);
                float h = (angle / (2f * (float) Math.PI) + 1f) % 1f;
                float s = dist / max_radius;
                int rgb = java.awt.Color.HSBtoRGB(h, s, 1f);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                image.putPixel(x, y, (255 << 24) | (b << 16) | (g << 8) | r);
            }
        }
        GLImage wheel_image = image;
        return new Texture(new GLImage[]{wheel_image}, GL11.GL_RGBA8, GL11.GL_LINEAR, GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE, GL12.GL_CLAMP_TO_EDGE);
    }

    public void addColorChangeListener(@NonNull Runnable listener) {
        color_change_listeners.add(listener);
    }

    public @NonNull Vector4f getColor() {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return Color.argb4v((0xFF << 24) | (rgb & 0xFFFFFF));
    }

    public void setColor(@NonNull Vector4fc color) {
        float[] hsb = java.awt.Color.RGBtoHSB((int) (color.x() * 255), (int) (color.y() * 255),
                (int) (color.z() * 255), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        updateMarkerPosition();
    }

    public void setBrightness(float brightness) {
        this.brightness = Math.clamp(brightness, 0f, 1f);
        notifyColorChanged();
    }

    public float getBrightness() {
        return brightness;
    }

    private void pickColor(int x, int y) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dx = x - cx;
        float dy = y - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > radius) {
            dx *= radius / dist;
            dy *= radius / dist;
            dist = radius;
        }
        float angle = (float) Math.atan2(dy, dx);
        hue = (angle / (2f * (float) Math.PI) + 1f) % 1f;
        saturation = dist / radius;
        marker_x = cx + dx;
        marker_y = cy + dy;
        notifyColorChanged();
    }

    private void updateMarkerPosition() {
        float angle = hue * 2f * (float) Math.PI;
        float dist = saturation * radius;
        marker_x = getWidth() / 2f + (float) Math.cos(angle) * dist;
        marker_y = getHeight() / 2f + (float) Math.sin(angle) * dist;
    }

    private void notifyColorChanged() {
        for (Runnable listener : color_change_listeners) {
            listener.run();
        }
    }

    @Override
    protected void renderGeometry(@NonNull GUIRenderer renderer) {
        renderer.drawTexture(wheel_texture, 0, 0, getWidth(), getHeight(), 0f, 0f, 1f, 1f, Color.WHITE);
        float half = MARKER_SIZE / 2f;
        Vector4fc marker = brightness > 0.5f ? Color.BLACK : Color.WHITE;
        renderer.drawColoredLine(marker_x - half, marker_y, marker_x + half, marker_y, 2f, marker);
        renderer.drawColoredLine(marker_x, marker_y - half, marker_x, marker_y + half, 2f, marker);
    }

    private final class WheelListener implements MouseMotionListener, MouseButtonListener {
        @Override
        public void mousePressed(@NonNull MouseButton button, int x, int y) {
            if (!isDisabled()) {
                pickColor(x, y);
                setFocus();
            }
        }

        @Override
        public void mouseDragged(@NonNull MouseButton button, int x, int y, int rel_x, int rel_y, int abs_x,
                int abs_y) {
            if (!isDisabled()) {
                pickColor(x, y);
            }
        }

        @Override
        public void mouseReleased(@NonNull MouseButton button, int x, int y) {
        }

        @Override
        public void mouseMoved(int x, int y) {
        }

        @Override
        public void mouseEntered() {
        }

        @Override
        public void mouseExited() {
        }

        @Override
        public void mouseHeld(@NonNull MouseButton button, int x, int y) {
        }

        @Override
        public void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) {
        }
    }
}
