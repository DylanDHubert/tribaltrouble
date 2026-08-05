package com.oddlabs.tt.vault;

import com.oddlabs.tt.camera.Camera;
import com.oddlabs.tt.camera.CameraState;
import org.jspecify.annotations.NonNull;

/**
 * ORBIT CAMERA FOR THE VAULT VIEWPORT — UPDATES FROM CONTROLLER EACH TICK.
 */
public final class VaultCamera extends Camera {
    private final @NonNull VaultController controller;

    public VaultCamera(@NonNull VaultController controller) {
        super(null, new CameraState());
        this.controller = controller;
        controller.applyCamera(getState());
    }

    @Override
    public void doAnimate(float t) {
        controller.advanceAnim(t);
        controller.applyCamera(getState());
    }
}
