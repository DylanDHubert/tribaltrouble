package com.oddlabs.tt.vault;

import com.oddlabs.tt.camera.CameraState;
import com.oddlabs.tt.global.Globals;
import com.oddlabs.tt.render.SpriteList;
import com.oddlabs.tt.resource.SpriteFile;
import com.oddlabs.tt.util.BoundingBox;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HOLDS VAULT SELECTION STATE, LOADS SPRITES, AND DRIVES CAMERA FRAMING.
 */
public final class VaultController {
    private static final Logger logger = Logger.getLogger(VaultController.class.getName());
    private static final float MIN_DISTANCE = 2f;
    private static final float MAX_DISTANCE = 80f;
    private static final float DEFAULT_VERT = -0.45f;

    private final @NonNull VaultCatalog catalog;
    private @NonNull Runnable onChanged;

    private int entryIndex;
    private int animIndex;
    private int lodIndex;
    private int texIndex;
    private float animTicks;
    private boolean playing = true;
    private float modelYaw;
    private float orbitYaw = (float) (Math.PI / 2);
    private float vertAngle = DEFAULT_VERT;
    private float distance = 12f;
    private @Nullable SpriteList spriteList;
    private @Nullable VaultEntry currentEntry;
    private @Nullable String statusMessage;

    public VaultController(@NonNull VaultCatalog catalog) {
        this(catalog, () -> {
        });
    }

    public VaultController(@NonNull VaultCatalog catalog, @NonNull Runnable onChanged) {
        this.catalog = catalog;
        this.onChanged = onChanged;
        if (catalog.size() == 0)
            throw new IllegalStateException("Vault catalog is empty");
        loadEntry(0);
    }

    public void setOnChanged(@NonNull Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public @NonNull VaultCatalog getCatalog() {
        return catalog;
    }

    public int getEntryIndex() {
        return entryIndex;
    }

    public @Nullable VaultEntry getCurrentEntry() {
        return currentEntry;
    }

    public @Nullable SpriteList getSpriteList() {
        return spriteList;
    }

    public int getAnimIndex() {
        return animIndex;
    }

    public int getLodIndex() {
        return lodIndex;
    }

    public int getTexIndex() {
        return texIndex;
    }

    public float getAnimTicks() {
        return animTicks;
    }

    public boolean isPlaying() {
        return playing;
    }

    public float getModelYaw() {
        return modelYaw;
    }

    public @Nullable String getStatusMessage() {
        return statusMessage;
    }

    public void nextSprite() {
        loadEntry((entryIndex + 1) % catalog.size());
    }

    public void prevSprite() {
        loadEntry((entryIndex - 1 + catalog.size()) % catalog.size());
    }

    public void loadEntry(int index) {
        entryIndex = Math.floorMod(index, catalog.size());
        VaultEntry entry = catalog.get(entryIndex);
        closeSprite();
        currentEntry = entry;
        animIndex = 0;
        lodIndex = 0;
        texIndex = 0;
        animTicks = 0f;
        statusMessage = null;
        try {
            boolean modulate = entry.modulateColor();
            SpriteFile file = new SpriteFile(entry.binspritePath(), Globals.NO_MIPMAP_CUTOFF,
                    true, true, true, modulate);
            spriteList = file.get();
            frameToBounds();
            logger.info("Vault loaded " + entry.displayName() + " (" + entry.binspritePath() + ")");
        } catch (Exception e) {
            spriteList = null;
            statusMessage = "Failed: " + entry.binspritePath();
            logger.log(Level.WARNING, "Failed to load " + entry.binspritePath(), e);
        }
        onChanged.run();
    }

    public void setAnimIndex(int index) {
        if (spriteList == null) return;
        String[] names = spriteList.getAnimationNames();
        if (names.length == 0) return;
        animIndex = Math.floorMod(index, names.length);
        animTicks = 0f;
        frameToBounds();
        onChanged.run();
    }

    public void nextAnim() {
        if (spriteList == null) return;
        setAnimIndex(animIndex + 1);
    }

    public void prevAnim() {
        if (spriteList == null) return;
        setAnimIndex(animIndex - 1);
    }

    public void cycleLod() {
        if (spriteList == null || spriteList.getNumSprites() <= 1) return;
        lodIndex = (lodIndex + 1) % spriteList.getNumSprites();
        onChanged.run();
    }

    public void cycleTexture() {
        if (spriteList == null) return;
        int n = spriteList.getSprite(Math.min(lodIndex, spriteList.getNumSprites() - 1)).getNumTextures();
        if (n <= 1) return;
        texIndex = (texIndex + 1) % n;
        onChanged.run();
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        onChanged.run();
    }

    public void setAnimTicks(float ticks) {
        this.animTicks = Math.max(0f, ticks);
        onChanged.run();
    }

    public void advanceAnim(float dt) {
        if (playing && spriteList != null) {
            // GAME PAIRS HIGH RUN wpc WITH MOVE SPEED (~mps). VAULT HAD SPEED 1, SO RUN LOOKED SLOW.
            // SCALE BY wpc SO PREVIEW CYCLE RATE MATCHES IDLE/ATTACK (~1 CYCLE / SEC).
            animTicks += dt * spriteList.getWPC(animIndex);
        }
    }

    public void addModelYaw(float delta) {
        modelYaw += delta;
    }

    public void addOrbit(float yawDelta, float pitchDelta) {
        orbitYaw += yawDelta;
        vertAngle = Math.max(-(float) Math.PI / 2f, Math.min(-0.0001f, vertAngle + pitchDelta));
    }

    public void addZoom(float amount) {
        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance * (amount < 0 ? 1.1f : 0.9f)));
    }

    public void resetView() {
        modelYaw = 0f;
        orbitYaw = (float) (Math.PI / 2);
        vertAngle = DEFAULT_VERT;
        frameToBounds();
        onChanged.run();
    }

    public void applyCamera(@NonNull CameraState state) {
        float cx = 0f;
        float cy = 0f;
        float cz = 0f;
        if (spriteList != null && spriteList.getBounds().length > 0) {
            BoundingBox box = spriteList.getBounds()[Math.min(animIndex, spriteList.getBounds().length - 1)];
            cx = box.getCX();
            cy = box.getCY();
            cz = box.getCZ();
        }
        float cosV = (float) Math.cos(vertAngle);
        float camX = cx + distance * cosV * (float) Math.cos(orbitYaw);
        float camY = cy + distance * cosV * (float) Math.sin(orbitYaw);
        float camZ = cz + distance * (float) Math.sin(-vertAngle);
        // LOOK TOWARD MODEL CENTER: HORIZ ANGLE POINTS CAMERA FORWARD AT TARGET.
        float lookHoriz = orbitYaw + (float) Math.PI;
        state.setCamera(camX, camY, camZ, vertAngle, lookHoriz);
    }

    public @NonNull String @NonNull [] getAnimationNames() {
        if (spriteList == null)
            return new String[0];
        return Arrays.copyOf(spriteList.getAnimationNames(), spriteList.getAnimationNames().length);
    }

    public int getNumLods() {
        return spriteList == null ? 0 : spriteList.getNumSprites();
    }

    public int getNumTextures() {
        if (spriteList == null) return 0;
        int lod = Math.min(lodIndex, Math.max(0, spriteList.getNumSprites() - 1));
        return spriteList.getSprite(lod).getNumTextures();
    }

    private void frameToBounds() {
        if (spriteList == null || spriteList.getBounds().length == 0) {
            distance = 12f;
            return;
        }
        BoundingBox box = spriteList.getBounds()[Math.min(animIndex, spriteList.getBounds().length - 1)];
        float dx = box.bmax_x - box.bmin_x;
        float dy = box.bmax_y - box.bmin_y;
        float dz = box.bmax_z - box.bmin_z;
        float radius = Math.max(dx, Math.max(dy, dz)) * 0.5f;
        float scale = currentEntry != null ? currentEntry.scale() : 1f;
        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, radius * 3.2f * scale + 1.5f));
    }

    private void closeSprite() {
        if (spriteList != null) {
            spriteList.close();
            spriteList = null;
        }
    }

    public void close() {
        closeSprite();
    }
}
