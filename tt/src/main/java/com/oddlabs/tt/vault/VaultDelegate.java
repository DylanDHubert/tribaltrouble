package com.oddlabs.tt.vault;

import com.oddlabs.tt.delegate.CameraDelegate;
import com.oddlabs.tt.gui.GUIRoot;
import com.oddlabs.tt.gui.MouseButton;
import com.oddlabs.tt.input.GameAction;
import com.oddlabs.tt.input.InputEvent;
import com.oddlabs.tt.input.InputPhase;
import com.oddlabs.tt.render.Renderer;
import org.jspecify.annotations.NonNull;

/**
 * MOUSE DRAG ORBIT / SCROLL ZOOM / HOTKEYS FOR VAULT NAVIGATION.
 */
public final class VaultDelegate extends CameraDelegate<VaultCamera> {
    private final @NonNull VaultController controller;

    public VaultDelegate(@NonNull GUIRoot gui_root, @NonNull VaultController controller) {
        super(gui_root, new VaultCamera(controller));
        this.controller = controller;
    }

    @Override
    public void handleInput(@NonNull InputEvent event) {
        if (event.getPhase() == InputPhase.PRESSED || event.getPhase() == InputPhase.REPEAT) {
            if (event.consumeAction(GameAction.UI_CANCEL)) {
                Renderer.shutdown();
                event.consume();
                return;
            }
        }
        super.handleInput(event);
    }

    @Override
    public void mouseDragged(@NonNull MouseButton button, int x, int y, int relative_x, int relative_y, int absolute_x,
            int absolute_y) {
        if (button == MouseButton.LEFT) {
            controller.addOrbit(relative_x * 0.01f, -relative_y * 0.01f);
        } else if (button == MouseButton.RIGHT) {
            controller.addModelYaw(relative_x * 0.01f);
        }
    }

    @Override
    public void mouseScrolled(int amount) {
        controller.addZoom(amount);
    }

    @Override
    public void mouseMoved(int x, int y) {
    }

    @Override
    public void mousePressed(@NonNull MouseButton button, int x, int y) {
    }

    @Override
    public void mouseReleased(@NonNull MouseButton button, int x, int y) {
    }

    @Override
    public boolean renderCursor() {
        return true;
    }
}
