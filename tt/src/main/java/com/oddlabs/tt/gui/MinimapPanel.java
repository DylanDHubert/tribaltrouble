package com.oddlabs.tt.gui;

import com.oddlabs.tt.camera.GameCamera;
import com.oddlabs.tt.delegate.JumpDelegate;
import com.oddlabs.tt.global.Settings;
import com.oddlabs.tt.landscape.HeightMap;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.render.GUIRenderer;
import com.oddlabs.tt.render.Texture;
import com.oddlabs.tt.resource.GLIntImage;
import com.oddlabs.tt.viewer.WorldViewer;
import com.oddlabs.util.Color;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A collapsible minimap panel that shows terrain (height or satellite), overlays, and local units.
 * Anchored to the bottom-left of the screen. Drawn via {@link #renderAtPosition} (not as a GUI-tree
 * child) so it stays visible across delegates.
 *
 * <p>Two base textures are baked once at map load (height colormap and landscape diffuse
 * "satellite"), both with isolines. Header toggles only swap which base is drawn / which overlays
 * are visible.
 */
public final class MinimapPanel extends GUIObject {

    // Layout constants
    private static final int MAP_SIZE = 250;
    private static final int HEADER_HEIGHT = 15;
    private static final int COLLAPSED_SIZE = 24;
    private static final int BORDER_WIDTH = 2;
    private static final int MARGIN_LEFT = 15;
    private static final int MARGIN_BOTTOM = 15;

    // Colors
    private static final Vector4fc BG_COLOR = new Vector4f(0f, 0f, 0f, 0.5f);
    private static final Vector4fc BORDER_COLOR = new Vector4f(0.4f, 0.4f, 0.4f, 0.8f);
    private static final Vector4fc HEADER_COLOR = new Vector4f(0.2f, 0.2f, 0.2f, 0.7f);
    private static final Vector4fc UNIT_COLOR = new Vector4f(0.3f, 1f, 0.3f, 1f);           // green
    private static final Vector4fc BUILDING_COLOR = new Vector4f(0.3f, 1f, 0.3f, 1f);       // green
    private static final Vector4fc VIEWPORT_COLOR = new Vector4f(1f, 1f, 1f, 1f);           // white

    // HEIGHTMAP COLOR STOPS (DEEP WATER -> PEAK)
    private static final Vector4fc DEEP_WATER_COLOR = new Vector4f(0.05f, 0.15f, 0.35f, 1f);
    private static final Vector4fc SHALLOW_WATER_COLOR = new Vector4f(0.2f, 0.45f, 0.75f, 1f);
    private static final Vector4fc BEACH_COLOR = new Vector4f(0.76f, 0.68f, 0.42f, 1f);
    private static final Vector4fc LOWLAND_COLOR = new Vector4f(0.35f, 0.52f, 0.22f, 1f);
    private static final Vector4fc HIGHLAND_COLOR = new Vector4f(0.55f, 0.42f, 0.28f, 1f);
    private static final Vector4fc PEAK_COLOR = new Vector4f(0.85f, 0.85f, 0.82f, 1f);

    // PREBAKED ISOLINES
    private static final int LAND_CONTOUR_COUNT = 12;
    private static final Vector4fc ISOLINE_COLOR = new Vector4f(0.08f, 0.08f, 0.08f, 1f);
    private static final float ISOLINE_BLEND = 0.55f;
    private static final float ISOLINE_SOFT_PIXELS = 1.25f;

    // UNWALKABLE OVERLAY (CLIFFS / STEEP SLOPES — NOT WATER); ALPHA = BLEND OVER BASE
    private static final Vector4fc UNWALKABLE_COLOR = new Vector4f(0.85f, 0.18f, 0.12f, 1f);
    private static final float UNWALKABLE_BLEND = 0.38f;
    private static final Vector4fc TRANSPARENT = new Vector4f(0f, 0f, 0f, 0f);

    // HEADER TOGGLES (UNWALKABLE + SATELLITE)
    private static final int TOGGLE_PAD = 2;
    private static final int TOGGLE_SIZE = 11;
    private static final Vector4fc TOGGLE_ON_COLOR = new Vector4f(0.9f, 0.2f, 0.15f, 1f);
    private static final Vector4fc TOGGLE_OFF_COLOR = new Vector4f(0.35f, 0.35f, 0.35f, 1f);
    private static final Vector4fc SATELLITE_TOGGLE_ON_COLOR = new Vector4f(0.4f, 0.75f, 0.35f, 1f);

    // Dot sizes // DYLAN: SLIGHTLY REDUCED SIZE FOR AESTHETICS.
    private static final float UNIT_DOT_SIZE = 1f;
    private static final float BUILDING_DOT_SIZE = 2f;

    // Viewport indicator
    private static final float VIEWPORT_LINE_THICKNESS = 1f;
    private final float[] viewportWorldXY = new float[8];
    private final float[] viewportMapXY = new float[8];

    /**
     * A prebaked overlay drawn on top of the base terrain when {@link #visible} is true.
     * Add more at load time for future layers (resources, fog, ownership, …).
     */
    private static final class OverlayLayer implements AutoCloseable {
        private final @NonNull Texture texture;
        private final @NonNull BooleanSupplier visible;

        OverlayLayer(@NonNull Texture texture, @NonNull BooleanSupplier visible) {
            this.texture = texture;
            this.visible = visible;
        }

        boolean isVisible() {
            return visible.getAsBoolean();
        }

        @NonNull Texture texture() {
            return texture;
        }

        @Override
        public void close() {
            texture.close();
        }
    }

    private final @NonNull WorldViewer viewer;
    private final int metersPerWorld;
    private @Nullable Texture terrainHeightBase;
    private @Nullable Texture terrainSatelliteBase;
    private final @NonNull List<OverlayLayer> overlays = new ArrayList<>();

    // Visibility control from SelectionDelegate
    private boolean mapModeActive = false;

    public MinimapPanel(@NonNull WorldViewer viewer) {
        this.viewer = viewer;
        HeightMap heightMap = viewer.getWorld().getHeightMap();
        this.metersPerWorld = heightMap.getMetersPerWorld();

        // BAKE BOTH BASES + OVERLAYS ONCE AT MAP LOAD; TOGGLES ONLY CHANGE DRAW VISIBILITY
        bakeLayers(heightMap);

        updateDimensions();
    }

    /**
     * Sample heights once, then bake height + satellite bases (with isolines) and overlays once each.
     */
    private void bakeLayers(@NonNull HeightMap heightMap) {
        int gridSize = heightMap.getGridUnitsPerWorld();
        float seaLevel = heightMap.getSeaLevelMeters();
        float[][] heights = sampleHeights(heightMap, gridSize);
        boolean[][] accessGrid = heightMap.getAccessGrid();
        float maxHeight = maxHeight(heights, seaLevel);
        float contourInterval = landContourInterval(seaLevel, maxHeight);

        terrainHeightBase = bakeHeightBase(heights, seaLevel, maxHeight, contourInterval);
        terrainSatelliteBase = bakeSatelliteBase(
                viewer.getLandscapeRenderer().getDiffuseMap(),
                heights,
                seaLevel,
                contourInterval);
        overlays.add(new OverlayLayer(
                bakeUnwalkableOverlay(heights, accessGrid, seaLevel),
                () -> Settings.getSettings().minimap_show_unwalkable));
    }

    private static @NonNull Texture bakeHeightBase(
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            float maxHeight,
            float contourInterval) {
        GLIntImage image = new GLIntImage(heights.length, heights.length, GL11.GL_RGBA);
        fillHeightColors(image, heights, seaLevel, maxHeight);
        bakeIsolines(image, heights, seaLevel, contourInterval);
        return toNearestTexture(image);
    }

    /**
     * Downsample the landscape diffuse colormap (true ground color, no trees) and stamp isolines.
     * Underwater cells use the height water ramp — diffuse seabottom is intentionally purple and
     * is covered by the ocean mesh in-game, so it looks wrong as "surface water" on the minimap.
     */
    private static @NonNull Texture bakeSatelliteBase(
            @NonNull Texture diffuseMap,
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            float contourInterval) {
        GLIntImage diffusePixels = readTexturePixels(diffuseMap);
        GLIntImage image = new GLIntImage(heights.length, heights.length, GL11.GL_RGBA);
        fillSatelliteColors(image, diffusePixels, heights, seaLevel);
        bakeIsolines(image, heights, seaLevel, contourInterval);
        return toNearestTexture(image);
    }

    private static @NonNull GLIntImage readTexturePixels(@NonNull Texture texture) {
        GLIntImage image = new GLIntImage(texture.getWidth(), texture.getHeight(), GL11.GL_RGBA);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getHandle());
        try {
            GL11.glGetTexImage(
                    GL11.GL_TEXTURE_2D,
                    0,
                    image.getGLFormat(),
                    image.getGLType(),
                    image.getPixels());
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
        return image;
    }

    private static void fillSatelliteColors(
            @NonNull GLIntImage dest,
            @NonNull GLIntImage diffuse,
            float @NonNull [] @NonNull [] heights,
            float seaLevel) {
        int gridSize = dest.getWidth();
        int srcW = diffuse.getWidth();
        int srcH = diffuse.getHeight();
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                float h = heights[y][x];
                if (h <= seaLevel) {
                    // SURFACE WATER LOOK — NOT THE PURPLE SEABOTTOM UNDER THE OCEAN MESH
                    dest.putPixel(x, y, packABGR(heightToColor(h, seaLevel, seaLevel + 1f)));
                    continue;
                }
                int sx = Math.min(srcW - 1, (x * srcW + srcW / 2) / gridSize);
                int sy = Math.min(srcH - 1, (y * srcH + srcH / 2) / gridSize);
                // FORCE OPAQUE — DIFFUSE MAY CARRY UNUSED ALPHA
                dest.putPixel(x, y, diffuse.getPixel(sx, sy) | 0xFF000000);
            }
        }
    }

    private @Nullable Texture activeTerrainBase() {
        return Settings.getSettings().minimap_satellite ? terrainSatelliteBase : terrainHeightBase;
    }

    private static @NonNull Texture bakeUnwalkableOverlay(
            float @NonNull [] @NonNull [] heights,
            boolean @NonNull [] @NonNull [] accessGrid,
            float seaLevel) {
        GLIntImage image = new GLIntImage(heights.length, heights.length, GL11.GL_RGBA);
        int gridSize = heights.length;
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                image.putPixel(x, y, packABGR(unwalkableOverlayPixel(
                        heights[y][x], seaLevel, isWalkableCell(accessGrid, x, y))));
            }
        }
        return toNearestTexture(image);
    }

    private static @NonNull Texture toNearestTexture(@NonNull GLIntImage image) {
        return new Texture(
                new GLIntImage[]{image},
                GL11.GL_RGBA,
                GL11.GL_NEAREST,
                GL11.GL_NEAREST,
                GL12.GL_CLAMP_TO_EDGE,
                GL12.GL_CLAMP_TO_EDGE
        );
    }

    private static float @NonNull [] @NonNull [] sampleHeights(@NonNull HeightMap heightMap, int gridSize) {
        float[][] heights = new float[gridSize][gridSize];
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                heights[y][x] = heightMap.getHeight(x, y);
            }
        }
        return heights;
    }

    private static float maxHeight(float @NonNull [] @NonNull [] heights, float seaLevel) {
        float max = seaLevel;
        for (float[] row : heights) {
            for (float height : row) {
                if (height > max) {
                    max = height;
                }
            }
        }
        // AVOID DIVIDE-BY-ZERO ON FLAT WATER WORLDS
        return max > seaLevel ? max : seaLevel + 1f;
    }

    static float landContourInterval(float seaLevel, float maxHeight) {
        return Math.max((maxHeight - seaLevel) / LAND_CONTOUR_COUNT, 0.001f);
    }

    private static void fillHeightColors(
            @NonNull GLIntImage image,
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            float maxHeight) {
        int gridSize = heights.length;
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                image.putPixel(x, y, packABGR(heightToColor(heights[y][x], seaLevel, maxHeight)));
            }
        }
    }

    /**
     * Soft-shade pixels near coastline and land contours (anti-aliased via local slope).
     * Reads the already-filled pixel so height and satellite bases share one isoline pass.
     */
    private static void bakeIsolines(
            @NonNull GLIntImage image,
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            float contourInterval) {
        int gridSize = heights.length;
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                float h = heights[y][x];
                float strength = isolineStrength(h, localGradient(heights, x, y), seaLevel, contourInterval);
                if (strength <= 0f) {
                    continue;
                }
                Vector4f shaded = lerpColor(
                        unpackABGR(image.getPixel(x, y)),
                        ISOLINE_COLOR,
                        ISOLINE_BLEND * strength);
                image.putPixel(x, y, packABGR(shaded));
            }
        }
    }

    private static boolean isWalkableCell(boolean @NonNull [] @NonNull [] accessGrid, int x, int y) {
        return y < accessGrid.length && x < accessGrid[y].length && accessGrid[y][x];
    }

    /**
     * Overlay pixel for unwalkable land. Transparent on water and walkable cells.
     * Alpha equals {@link #UNWALKABLE_BLEND} so SRC_ALPHA blending matches the old in-bake lerp.
     */
    static @NonNull Vector4f unwalkableOverlayPixel(float height, float seaLevel, boolean walkable) {
        if (height > seaLevel && !walkable) {
            return new Vector4f(UNWALKABLE_COLOR.x(), UNWALKABLE_COLOR.y(), UNWALKABLE_COLOR.z(), UNWALKABLE_BLEND);
        }
        return new Vector4f(TRANSPARENT);
    }

    private static float localGradient(float @NonNull [] @NonNull [] heights, int x, int y) {
        int gridSize = heights.length;
        float h = heights[y][x];
        float grad = 0f;
        if (x + 1 < gridSize) {
            grad = Math.max(grad, Math.abs(heights[y][x + 1] - h));
        }
        if (x > 0) {
            grad = Math.max(grad, Math.abs(heights[y][x - 1] - h));
        }
        if (y + 1 < gridSize) {
            grad = Math.max(grad, Math.abs(heights[y + 1][x] - h));
        }
        if (y > 0) {
            grad = Math.max(grad, Math.abs(heights[y - 1][x] - h));
        }
        return grad;
    }

    /**
     * Soft coverage for the nearest contour, in ~pixel units using local height gradient.
     */
    static float isolineStrength(float height, float gradient, float seaLevel, float contourInterval) {
        float heightDist = distanceToNearestContour(height, seaLevel, contourInterval);
        float pixelDist = heightDist / Math.max(gradient, 1e-4f);
        return clamp01(1f - pixelDist / ISOLINE_SOFT_PIXELS);
    }

    static float distanceToNearestContour(float height, float seaLevel, float contourInterval) {
        float coastDist = Math.abs(height - seaLevel);
        if (height <= seaLevel) {
            return coastDist;
        }
        float phase = (height - seaLevel) / contourInterval;
        float frac = phase - (float) Math.floor(phase);
        float landDist = Math.min(frac, 1f - frac) * contourInterval;
        return Math.min(coastDist, landDist);
    }

    /**
     * Map a world height to a hypsometric color.
     * Below sea level: deep water → shallow water.
     * Above sea level: beach → lowland → highland → peak.
     */
    static @NonNull Vector4f heightToColor(float height, float seaLevel, float maxHeight) {
        if (height <= seaLevel) {
            // 0 AT DEEPEST (SEA*0), 1 AT SEA LEVEL — ASSUME FLOOR NEAR 0
            float t = seaLevel > 0f ? clamp01(height / seaLevel) : 0f;
            return lerpColor(DEEP_WATER_COLOR, SHALLOW_WATER_COLOR, t);
        }

        float landT = clamp01((height - seaLevel) / (maxHeight - seaLevel));
        if (landT < 0.15f) {
            return lerpColor(BEACH_COLOR, LOWLAND_COLOR, landT / 0.15f);
        } else if (landT < 0.55f) {
            return lerpColor(LOWLAND_COLOR, HIGHLAND_COLOR, (landT - 0.15f) / 0.40f);
        } else {
            return lerpColor(HIGHLAND_COLOR, PEAK_COLOR, (landT - 0.55f) / 0.45f);
        }
    }

    private static @NonNull Vector4f lerpColor(@NonNull Vector4fc a, @NonNull Vector4fc b, float t) {
        t = clamp01(t);
        return new Vector4f(
                a.x() + (b.x() - a.x()) * t,
                a.y() + (b.y() - a.y()) * t,
                a.z() + (b.z() - a.z()) * t,
                a.w() + (b.w() - a.w()) * t
        );
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int packABGR(@NonNull Vector4fc color) {
        int a = (int) (color.w() * 255) & 0xFF;
        int b = (int) (color.z() * 255) & 0xFF;
        int g = (int) (color.y() * 255) & 0xFF;
        int r = (int) (color.x() * 255) & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    static @NonNull Vector4f unpackABGR(int packed) {
        float r = (packed & 0xFF) / 255f;
        float g = ((packed >> 8) & 0xFF) / 255f;
        float b = ((packed >> 16) & 0xFF) / 255f;
        float a = ((packed >> 24) & 0xFF) / 255f;
        return new Vector4f(r, g, b, a);
    }

    private void updateDimensions() {
        if (Settings.getSettings().minimap_expanded) {
            setDim(MAP_SIZE + 2 * BORDER_WIDTH, MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH);
        } else {
            setDim(COLLAPSED_SIZE, COLLAPSED_SIZE);
        }
    }

    /**
     * Called by SelectionDelegate when entering/exiting map mode.
     */
    public void setMapModeActive(boolean active) {
        this.mapModeActive = active;
    }

    /**
     * Render the minimap in screen space. Called from {@code InGameDelegate.render2D()} so it
     * stays visible when other delegates are pushed on top.
     */
    public void renderAtPosition(@NonNull GUIRenderer renderer) {
        if (!Settings.getSettings().show_minimap || mapModeActive) {
            return;
        }

        updateDimensions();
        int x = MARGIN_LEFT;
        int y = MARGIN_BOTTOM;

        renderer.flush();

        if (Settings.getSettings().minimap_expanded) {
            renderExpanded(renderer, x, y);
        } else {
            renderCollapsed(renderer, x, y);
        }
    }

    private void renderExpanded(@NonNull GUIRenderer renderer, int posX, int posY) {
        int w = MAP_SIZE + 2 * BORDER_WIDTH;
        int h = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;

        renderer.drawColoredQuad(posX, posY, w, h, BG_COLOR);

        renderer.drawColoredQuad(posX, posY, w, BORDER_WIDTH, BORDER_COLOR);
        renderer.drawColoredQuad(posX, posY + h - BORDER_WIDTH, w, BORDER_WIDTH, BORDER_COLOR);
        renderer.drawColoredQuad(posX, posY, BORDER_WIDTH, h, BORDER_COLOR);
        renderer.drawColoredQuad(posX + w - BORDER_WIDTH, posY, BORDER_WIDTH, h, BORDER_COLOR);

        int headerY = posY + h - HEADER_HEIGHT - BORDER_WIDTH;
        renderer.drawColoredQuad(posX + BORDER_WIDTH, headerY, w - 2 * BORDER_WIDTH, HEADER_HEIGHT, HEADER_COLOR);
        drawHeaderToggles(renderer, posX, posY, h);

        float indicatorX = posX + w / 2f - 4;
        float indicatorY = headerY + HEADER_HEIGHT / 2f - 2;
        renderer.drawColoredQuad(indicatorX, indicatorY, 8, 4, BORDER_COLOR);

        float mapX = posX + BORDER_WIDTH;
        float mapY = posY + BORDER_WIDTH;
        float mapW = w - 2 * BORDER_WIDTH;
        float mapH = h - HEADER_HEIGHT - 2 * BORDER_WIDTH;

        drawTerrainLayers(renderer, mapX, mapY, mapW, mapH);

        renderer.flush();

        renderEntities(renderer, mapX, mapY, mapW, mapH);
        renderViewport(renderer, mapX, mapY, mapW, mapH);
    }

    private void drawTerrainLayers(
            @NonNull GUIRenderer renderer,
            float mapX,
            float mapY,
            float mapW,
            float mapH) {
        Texture terrain = activeTerrainBase();
        if (terrain != null) {
            renderer.drawTexture(terrain, mapX, mapY, mapW, mapH, 0f, 0f, 1f, 1f, Color.WHITE);
        }
        for (OverlayLayer layer : overlays) {
            if (layer.isVisible()) {
                renderer.drawTexture(layer.texture(), mapX, mapY, mapW, mapH, 0f, 0f, 1f, 1f, Color.WHITE);
            }
        }
    }

    private void renderCollapsed(@NonNull GUIRenderer renderer, int posX, int posY) {
        renderer.drawColoredQuad(posX, posY, COLLAPSED_SIZE, COLLAPSED_SIZE, BG_COLOR);
        renderer.drawColoredQuad(posX, posY, COLLAPSED_SIZE, 2, BORDER_COLOR);
        renderer.drawColoredQuad(posX, posY + COLLAPSED_SIZE - 2, COLLAPSED_SIZE, 2, BORDER_COLOR);
        renderer.drawColoredQuad(posX, posY, 2, COLLAPSED_SIZE, BORDER_COLOR);
        renderer.drawColoredQuad(posX + COLLAPSED_SIZE - 2, posY, 2, COLLAPSED_SIZE, BORDER_COLOR);

        float cx = posX + COLLAPSED_SIZE / 2f;
        float cy = posY + COLLAPSED_SIZE / 2f;
        renderer.drawColoredQuad(cx - 6, cy - 1, 12, 2, BORDER_COLOR);
        renderer.drawColoredQuad(cx - 1, cy - 6, 2, 12, BORDER_COLOR);
    }

    private void renderEntities(@NonNull GUIRenderer renderer, float mapX, float mapY, float mapW, float mapH) {
        var localPlayer = viewer.getLocalPlayer();
        var entities = localPlayer.getUnits().getSet();

        for (Selectable<?> entity : entities) {
            if (entity.isDead()) {
                continue;
            }

            float worldX = entity.getPositionX();
            float worldY = entity.getPositionY();

            float normX = worldX / metersPerWorld;
            float normY = worldY / metersPerWorld;

            float dotX = mapX + normX * mapW;
            float dotY = mapY + normY * mapH;

            if (entity instanceof Building building) {
                if (building.isPlaced()) {
                    float halfSize = BUILDING_DOT_SIZE / 2f;
                    renderer.drawColoredQuad(dotX - halfSize, dotY - halfSize,
                            BUILDING_DOT_SIZE, BUILDING_DOT_SIZE, BUILDING_COLOR);
                }
            } else if (entity instanceof Unit) {
                float halfSize = UNIT_DOT_SIZE / 2f;
                renderer.drawColoredQuad(dotX - halfSize, dotY - halfSize,
                        UNIT_DOT_SIZE, UNIT_DOT_SIZE, UNIT_COLOR);
            }
        }
    }

    /**
     * Render the camera frustum as a quad from the four screen-corner landscape hits.
     */
    private void renderViewport(@NonNull GUIRenderer renderer, float mapX, float mapY, float mapW, float mapH) {
        // ALWAYS DRAW FROM CORNER HITS (SEA / FAR FALLBACK FILLS MISSES)
        viewer.getPicker().pickViewportCorners(viewer.getCamera().getState(), viewportWorldXY);

        for (int i = 0; i < 4; i++) {
            float worldX = viewportWorldXY[i * 2];
            float worldY = viewportWorldXY[i * 2 + 1];
            viewportMapXY[i * 2] = mapX + clamp01(worldX / metersPerWorld) * mapW;
            viewportMapXY[i * 2 + 1] = mapY + clamp01(worldY / metersPerWorld) * mapH;
        }

        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            renderer.drawColoredLine(
                    viewportMapXY[i * 2], viewportMapXY[i * 2 + 1],
                    viewportMapXY[next * 2], viewportMapXY[next * 2 + 1],
                    VIEWPORT_LINE_THICKNESS, VIEWPORT_COLOR);
        }
    }

    @Override
    protected void renderGeometry(@NonNull GUIRenderer renderer) {
        // NOT IN THE GUI TREE — DRAWING HAPPENS VIA renderAtPosition FROM InGameDelegate
    }

    private void drawHeaderToggles(
            @NonNull GUIRenderer renderer,
            int posX,
            int posY,
            int panelH) {
        drawToggleButton(
                renderer,
                posX + unwalkableToggleLocalX(),
                posY + headerToggleLocalY(panelH),
                Settings.getSettings().minimap_show_unwalkable,
                TOGGLE_ON_COLOR);
        drawToggleButton(
                renderer,
                posX + satelliteToggleLocalX(),
                posY + headerToggleLocalY(panelH),
                Settings.getSettings().minimap_satellite,
                SATELLITE_TOGGLE_ON_COLOR);
    }

    private void drawToggleButton(
            @NonNull GUIRenderer renderer,
            int btnX,
            int btnY,
            boolean on,
            @NonNull Vector4fc onColor) {
        renderer.drawColoredQuad(btnX, btnY, TOGGLE_SIZE, TOGGLE_SIZE, on ? onColor : TOGGLE_OFF_COLOR);
        renderer.drawColoredQuad(btnX, btnY, TOGGLE_SIZE, 1, BORDER_COLOR);
        renderer.drawColoredQuad(btnX, btnY + TOGGLE_SIZE - 1, TOGGLE_SIZE, 1, BORDER_COLOR);
        renderer.drawColoredQuad(btnX, btnY, 1, TOGGLE_SIZE, BORDER_COLOR);
        renderer.drawColoredQuad(btnX + TOGGLE_SIZE - 1, btnY, 1, TOGGLE_SIZE, BORDER_COLOR);
    }

    private static int unwalkableToggleLocalX() {
        return BORDER_WIDTH + TOGGLE_PAD;
    }

    private static int satelliteToggleLocalX() {
        return unwalkableToggleLocalX() + TOGGLE_SIZE + TOGGLE_PAD;
    }

    private static int headerToggleLocalY(int panelH) {
        int headerY = panelH - HEADER_HEIGHT - BORDER_WIDTH;
        return headerY + (HEADER_HEIGHT - TOGGLE_SIZE) / 2;
    }

    static boolean hitToggle(int localX, int localY, int btnX, int btnY) {
        return localX >= btnX && localX < btnX + TOGGLE_SIZE
                && localY >= btnY && localY < btnY + TOGGLE_SIZE;
    }

    static boolean hitUnwalkableToggle(int localX, int localY, int panelH) {
        return hitToggle(localX, localY, unwalkableToggleLocalX(), headerToggleLocalY(panelH));
    }

    static boolean hitSatelliteToggle(int localX, int localY, int panelH) {
        return hitToggle(localX, localY, satelliteToggleLocalX(), headerToggleLocalY(panelH));
    }

    private boolean hitUnwalkableToggle(int localX, int localY) {
        return hitUnwalkableToggle(localX, localY, getHeight());
    }

    private boolean hitSatelliteToggle(int localX, int localY) {
        return hitSatelliteToggle(localX, localY, getHeight());
    }

    private void toggleUnwalkableTint() {
        Settings settings = Settings.getSettings();
        settings.minimap_show_unwalkable = !settings.minimap_show_unwalkable;
    }

    private void toggleSatelliteView() {
        Settings settings = Settings.getSettings();
        settings.minimap_satellite = !settings.minimap_satellite;
    }

    private void toggleExpanded() {
        Settings.getSettings().minimap_expanded = !Settings.getSettings().minimap_expanded;
        updateDimensions();
    }

    /**
     * Move the camera to the specified world coordinates using a JumpDelegate.
     */
    private void moveCameraTo(float worldX, float worldY) {
        GUIRoot root = viewer.getGUIRoot();
        GameCamera camera = viewer.getCamera();
        root.pushDelegate(new JumpDelegate(viewer, camera, worldX, worldY));
    }

    /**
     * Check if a screen coordinate is within the minimap bounds.
     */
    public boolean containsScreenPoint(int screenX, int screenY) {
        if (!Settings.getSettings().show_minimap || mapModeActive) {
            return false;
        }

        updateDimensions();
        return screenX >= MARGIN_LEFT && screenX < MARGIN_LEFT + getWidth()
                && screenY >= MARGIN_BOTTOM && screenY < MARGIN_BOTTOM + getHeight();
    }

    /**
     * Handle a mouse click at screen coordinates. Called via {@code InGameDelegate.tryHandleMinimapClick}.
     *
     * @return true if the click was handled
     */
    public boolean handleScreenClick(int screenX, int screenY) {
        if (!Settings.getSettings().show_minimap || mapModeActive) {
            return false;
        }

        updateDimensions();
        return handleLocalClick(screenX - MARGIN_LEFT, screenY - MARGIN_BOTTOM);
    }

    private boolean handleLocalClick(int localX, int localY) {
        if (Settings.getSettings().minimap_expanded) {
            if (hitUnwalkableToggle(localX, localY)) {
                toggleUnwalkableTint();
                return true;
            }
            if (hitSatelliteToggle(localX, localY)) {
                toggleSatelliteView();
                return true;
            }

            int headerY = getHeight() - HEADER_HEIGHT - BORDER_WIDTH;

            if (localY >= headerY) {
                toggleExpanded();
                return true;
            } else if (localY >= BORDER_WIDTH && localX >= BORDER_WIDTH
                    && localX < getWidth() - BORDER_WIDTH && localY < headerY) {
                float mapW = getWidth() - 2 * BORDER_WIDTH;
                float mapH = getHeight() - HEADER_HEIGHT - 2 * BORDER_WIDTH;

                float normX = (localX - BORDER_WIDTH) / mapW;
                float normY = (localY - BORDER_WIDTH) / mapH;

                moveCameraTo(normX * metersPerWorld, normY * metersPerWorld);
                return true;
            }
        } else {
            toggleExpanded();
            return true;
        }

        return false;
    }

    @Override
    protected void doRemove() {
        // DO NOT DESTROY TEXTURES HERE — PANEL MAY BE REMOVED TEMPORARILY DURING DELEGATE CHANGES.
        // USE dispose() FOR FINAL CLEANUP.
        super.doRemove();
    }

    /**
     * Clean up GPU resources. Call only when the minimap is permanently destroyed
     * (e.g. game over / return to menu), not during temporary delegate changes.
     */
    public void dispose() {
        if (terrainHeightBase != null) {
            terrainHeightBase.close();
            terrainHeightBase = null;
        }
        if (terrainSatelliteBase != null) {
            terrainSatelliteBase.close();
            terrainSatelliteBase = null;
        }
        for (OverlayLayer layer : overlays) {
            layer.close();
        }
        overlays.clear();
    }
}
