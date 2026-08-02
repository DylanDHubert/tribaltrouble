package com.oddlabs.tt.gui;

import com.oddlabs.tt.render.GUIRenderer;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;

public final class ColorPreviewBox extends GUIObject {
    private final Vector4f color = new Vector4f(com.oddlabs.util.Color.WHITE);

    public ColorPreviewBox(int size) {
        setDim(size, size);
    }

    @Override
    protected void renderGeometry(@NonNull GUIRenderer renderer) {
        renderer.drawColoredQuad(0, 0, getWidth(), getHeight(), color);
        renderer.drawColoredLine(0, 0, getWidth(), 0, 1f, com.oddlabs.util.Color.BLACK);
        renderer.drawColoredLine(0, getHeight(), getWidth(), getHeight(), 1f, com.oddlabs.util.Color.BLACK);
        renderer.drawColoredLine(0, 0, 0, getHeight(), 1f, com.oddlabs.util.Color.BLACK);
        renderer.drawColoredLine(getWidth(), 0, getWidth(), getHeight(), 1f, com.oddlabs.util.Color.BLACK);
    }

    public void setColor(@NonNull Vector4fc c) {
        this.color.set(c);
    }
}
