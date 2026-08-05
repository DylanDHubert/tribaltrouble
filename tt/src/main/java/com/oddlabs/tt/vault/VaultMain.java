package com.oddlabs.tt.vault;

import com.oddlabs.tt.Main;
import com.oddlabs.tt.render.Renderer;

import java.util.logging.Logger;

/**
 * ENTRY POINT FOR ./gradlew tt:vault — SPRITE BROWSER IN GAME UI STYLE.
 */
public final class VaultMain {
    private static final Logger logger = Logger.getLogger(VaultMain.class.getName());

    private VaultMain() {
    }

    public static void main(String @org.jspecify.annotations.NonNull [] args) {
        int status = 1;
        try {
            logger.info("Starting Vault....");
            Renderer.getRenderer().runVault(args);
            status = 0;
        } catch (Throwable t) {
            Main.fail(t);
        } finally {
            Renderer.getRenderer().close();
            logger.info("Vault exiting");
            System.exit(status);
        }
    }
}
