package com.oddlabs.tt.gui;

import com.oddlabs.tt.camera.CameraState;
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

/**
 * A collapsible minimap panel that shows a height-colored terrain map with prebaked isolines
 * and the local player's units and buildings. Anchored to the bottom-left of the screen.
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

    // UNWALKABLE LAND TINT (CLIFFS / STEEP SLOPES — NOT WATER)
    private static final Vector4fc UNWALKABLE_COLOR = new Vector4f(0.85f, 0.18f, 0.12f, 1f);
    private static final float UNWALKABLE_BLEND = 0.38f;

    // HEADER TOGGLE FOR UNWALKABLE TINT
    private static final int TOGGLE_PAD = 2;
    private static final int TOGGLE_SIZE = 11;
    private static final Vector4fc TOGGLE_ON_COLOR = new Vector4f(0.9f, 0.2f, 0.15f, 1f);
    private static final Vector4fc TOGGLE_OFF_COLOR = new Vector4f(0.35f, 0.35f, 0.35f, 1f);

    // Dot sizes // DYLAN: SLIGHTLY REDUCED SIZE FOR AESTHETICS.
    private static final float UNIT_DOT_SIZE = 1f;
    private static final float BUILDING_DOT_SIZE = 2f;

    // Viewport indicator
    private static final float VIEWPORT_LINE_THICKNESS = 1f;

    private final @NonNull WorldViewer viewer;
    private final int metersPerWorld;
    private @Nullable Texture terrainPlain;
    private @Nullable Texture terrainUnwalkable;

    // Visibility control from SelectionDelegate
    private boolean mapModeActive = false;

    public MinimapPanel(@NonNull WorldViewer viewer) {
        this.viewer = viewer;
        HeightMap heightMap = viewer.getWorld().getHeightMap();
        this.metersPerWorld = heightMap.getMetersPerWorld();

        // Enable picking for mouse clicks
        setCanFocus(true);

        // BAKE BOTH TERRAIN VARIANTS ONCE; TOGGLE ONLY SWAPS WHICH IS DRAWN
        bakeTerrainTextures(heightMap);

        // Set initial dimensions based on expanded state
        updateDimensions();
    }

    /**
     * Prebake height-colored terrain with and without unwalkable tint.
     * Toggle swaps textures; no rebake on click.
     */
    private void bakeTerrainTextures(@NonNull HeightMap heightMap) {
        int gridSize = heightMap.getGridUnitsPerWorld();
        float seaLevel = heightMap.getSeaLevelMeters();
        float[][] heights = sampleHeights(heightMap, gridSize);
        boolean[][] accessGrid = heightMap.getAccessGrid();
        float maxHeight = maxHeight(heights, seaLevel);
        float contourInterval = landContourInterval(seaLevel, maxHeight);

        terrainPlain = bakeTerrainTexture(heights, accessGrid, seaLevel, maxHeight, contourInterval, false);
        terrainUnwalkable = bakeTerrainTexture(heights, accessGrid, seaLevel, maxHeight, contourInterval, true);
    }

    private static @NonNull Texture bakeTerrainTexture(
            float @NonNull [] @NonNull [] heights,
            boolean @NonNull [] @NonNull [] accessGrid,
            float seaLevel,
            float maxHeight,
            float contourInterval,
            boolean showUnwalkable) {
        GLIntImage image = new GLIntImage(heights.length, heights.length, GL11.GL_RGBA);
        fillHeightColors(image, heights, accessGrid, seaLevel, maxHeight, showUnwalkable);
        bakeIsolines(image, heights, accessGrid, seaLevel, maxHeight, contourInterval, showUnwalkable);
        return new Texture(
                new GLIntImage[]{image},
                GL11.GL_RGBA,
                GL11.GL_NEAREST,
                GL11.GL_NEAREST,
                GL12.GL_CLAMP_TO_EDGE,
                GL12.GL_CLAMP_TO_EDGE
        );
    }

    private @Nullable Texture activeTerrainTexture() {
        return Settings.getSettings().minimap_show_unwalkable ? terrainUnwalkable : terrainPlain;
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
            boolean @NonNull [] @NonNull [] accessGrid,
            float seaLevel,
            float maxHeight,
            boolean showUnwalkable) {
        int gridSize = heights.length;
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                float h = heights[y][x];
                boolean walkable = isWalkableCell(accessGrid, x, y);
                image.putPixel(x, y, packABGR(terrainColor(h, seaLevel, maxHeight, walkable, showUnwalkable)));
            }
        }
    }

    /**
     * Soft-shade pixels near coastline and land contours (anti-aliased via local slope).
     */
    private static void bakeIsolines(
            @NonNull GLIntImage image,
            float @NonNull [] @NonNull [] heights,
            boolean @NonNull [] @NonNull [] accessGrid,
            float seaLevel,
            float maxHeight,
            float contourInterval,
            boolean showUnwalkable) {
        int gridSize = heights.length;
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                float h = heights[y][x];
                float strength = isolineStrength(h, localGradient(heights, x, y), seaLevel, contourInterval);
                if (strength <= 0f) {
                    continue;
                }
                boolean walkable = isWalkableCell(accessGrid, x, y);
                Vector4f shaded = lerpColor(
                        terrainColor(h, seaLevel, maxHeight, walkable, showUnwalkable),
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
     * Height colormap, with an optional red tint on unwalkable land (steep / blocked cells).
     * Water stays on the blue ramp even when access_grid is false.
     */
    static @NonNull Vector4f terrainColor(
            float height,
            float seaLevel,
            float maxHeight,
            boolean walkable,
            boolean showUnwalkable) {
        Vector4f color = heightToColor(height, seaLevel, maxHeight);
        if (showUnwalkable && height > seaLevel && !walkable) {
            return lerpColor(color, UNWALKABLE_COLOR, UNWALKABLE_BLEND);
        }
        return color;
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
     * True when the segment between two heights crosses the coastline or a land contour.
     */
    static boolean crossesContour(float h0, float h1, float seaLevel, float contourInterval) {
        boolean land0 = h0 > seaLevel;
        boolean land1 = h1 > seaLevel;
        if (land0 != land1) {
            return true;
        }
        if (!land0) {
            return false;
        }
        return landContourBand(h0, seaLevel, contourInterval)
                != landContourBand(h1, seaLevel, contourInterval);
    }

    static int landContourBand(float height, float seaLevel, float contourInterval) {
        return (int) Math.floor((height - seaLevel) / contourInterval);
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
     * Position the minimap in the bottom-left corner.
     */
    public void updatePosition(int screenWidth, int screenHeight) {
        int x = MARGIN_LEFT;
        int y = MARGIN_BOTTOM;
        setPos(x, y);
    }

    /**
     * Render the minimap at the specified screen position.
     * This method is called from SelectionDelegate.render2D() to ensure the minimap
     * remains visible even when other delegates (like TargetDelegate) are pushed on top.
     * 
     * @param renderer The GUI renderer
     * @param screenWidth Current screen width for positioning
     * @param screenHeight Current screen height for positioning
     */
    public void renderAtPosition(@NonNull GUIRenderer renderer, int screenWidth, int screenHeight) {
        // Don't render if disabled in settings or map mode is active
        if (!Settings.getSettings().show_minimap || mapModeActive) {
            return;
        }

        // Calculate position (bottom-left)
        updateDimensions();
        int x = MARGIN_LEFT;
        int y = MARGIN_BOTTOM;

        // Save current position, render, then restore if needed
        renderer.flush();
        
        // Translate to minimap position and render
        if (Settings.getSettings().minimap_expanded) {
            renderExpandedAt(renderer, x, y);
        } else {
            renderCollapsedAt(renderer, x, y);
        }
    }

    private void renderExpandedAt(@NonNull GUIRenderer renderer, int posX, int posY) {
        int w = MAP_SIZE + 2 * BORDER_WIDTH;
        int h = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;

        // Background
        renderer.drawColoredQuad(posX, posY, w, h, BG_COLOR);

        // Border
        renderer.drawColoredQuad(posX, posY, w, BORDER_WIDTH, BORDER_COLOR);                    // bottom
        renderer.drawColoredQuad(posX, posY + h - BORDER_WIDTH, w, BORDER_WIDTH, BORDER_COLOR); // top
        renderer.drawColoredQuad(posX, posY, BORDER_WIDTH, h, BORDER_COLOR);                    // left
        renderer.drawColoredQuad(posX + w - BORDER_WIDTH, posY, BORDER_WIDTH, h, BORDER_COLOR); // right

        // Header bar
        int headerY = posY + h - HEADER_HEIGHT - BORDER_WIDTH;
        renderer.drawColoredQuad(posX + BORDER_WIDTH, headerY, w - 2 * BORDER_WIDTH, HEADER_HEIGHT, HEADER_COLOR);
        drawUnwalkableToggle(renderer, posX, posY, h);

        // Collapse indicator
        float indicatorX = posX + w / 2f - 4;
        float indicatorY = headerY + HEADER_HEIGHT / 2f - 2;
        renderer.drawColoredQuad(indicatorX, indicatorY, 8, 4, BORDER_COLOR);

        // Map area
        float mapX = posX + BORDER_WIDTH;
        float mapY = posY + BORDER_WIDTH;
        float mapW = w - 2 * BORDER_WIDTH;
        float mapH = h - HEADER_HEIGHT - 2 * BORDER_WIDTH;

        // Terrain texture
        Texture terrain = activeTerrainTexture();
        if (terrain != null) {
            renderer.drawTexture(terrain, mapX, mapY, mapW, mapH,
                    0f, 0f, 1f, 1f, Color.WHITE);
        }

        renderer.flush();

        // Entity dots
        renderEntitiesAt(renderer, mapX, mapY, mapW, mapH);
        
        // Viewport rectangle
        renderViewportAt(renderer, mapX, mapY, mapW, mapH);
    }

    private void renderCollapsedAt(@NonNull GUIRenderer renderer, int posX, int posY) {
        // Simple collapsed indicator
        renderer.drawColoredQuad(posX, posY, COLLAPSED_SIZE, COLLAPSED_SIZE, BG_COLOR);
        renderer.drawColoredQuad(posX, posY, COLLAPSED_SIZE, 2, BORDER_COLOR);
        renderer.drawColoredQuad(posX, posY + COLLAPSED_SIZE - 2, COLLAPSED_SIZE, 2, BORDER_COLOR);
        renderer.drawColoredQuad(posX, posY, 2, COLLAPSED_SIZE, BORDER_COLOR);
        renderer.drawColoredQuad(posX + COLLAPSED_SIZE - 2, posY, 2, COLLAPSED_SIZE, BORDER_COLOR);

        // Expand indicator (plus sign)
        float cx = posX + COLLAPSED_SIZE / 2f;
        float cy = posY + COLLAPSED_SIZE / 2f;
        renderer.drawColoredQuad(cx - 6, cy - 1, 12, 2, BORDER_COLOR);
        renderer.drawColoredQuad(cx - 1, cy - 6, 2, 12, BORDER_COLOR);
    }

    private void renderEntitiesAt(@NonNull GUIRenderer renderer, float mapX, float mapY, float mapW, float mapH) {
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
     * Render a white rectangle showing the current camera viewport on the minimap.
     */
    private void renderViewportAt(@NonNull GUIRenderer renderer, float mapX, float mapY, float mapW, float mapH) {
        CameraState state = viewer.getCamera().getState();
        float camX = state.getTargetX();
        float camY = state.getTargetY();
        float camZ = state.getTargetZ();

        // Estimate visible area - roughly proportional to camera height
        float viewRadius = camZ * 1.5f;

        // Convert camera position and view size to normalized coordinates
        float normCamX = camX / metersPerWorld;
        float normCamY = camY / metersPerWorld;
        float normRadiusX = viewRadius / metersPerWorld;
        float normRadiusY = viewRadius / metersPerWorld;

        // Convert to minimap pixel coordinates
        float rectCenterX = mapX + normCamX * mapW;
        float rectCenterY = mapY + normCamY * mapH;
        float rectHalfW = normRadiusX * mapW;
        float rectHalfH = normRadiusY * mapH;

        // Calculate rectangle bounds
        float left = rectCenterX - rectHalfW;
        float right = rectCenterX + rectHalfW;
        float bottom = rectCenterY - rectHalfH;
        float top = rectCenterY + rectHalfH;

        // Clamp to map area
        left = Math.max(left, mapX);
        right = Math.min(right, mapX + mapW);
        bottom = Math.max(bottom, mapY);
        top = Math.min(top, mapY + mapH);

        float width = right - left;
        float height = top - bottom;

        // Draw rectangle outline (4 lines)
        // Bottom edge
        renderer.drawColoredQuad(left, bottom, width, VIEWPORT_LINE_THICKNESS, VIEWPORT_COLOR);
        // Top edge
        renderer.drawColoredQuad(left, top - VIEWPORT_LINE_THICKNESS, width, VIEWPORT_LINE_THICKNESS, VIEWPORT_COLOR);
        // Left edge
        renderer.drawColoredQuad(left, bottom, VIEWPORT_LINE_THICKNESS, height, VIEWPORT_COLOR);
        // Right edge
        renderer.drawColoredQuad(right - VIEWPORT_LINE_THICKNESS, bottom, VIEWPORT_LINE_THICKNESS, height, VIEWPORT_COLOR);
    }

    @Override
    protected void renderGeometry(@NonNull GUIRenderer renderer) {
        // Don't render if disabled in settings or map mode is active
        if (!Settings.getSettings().show_minimap || mapModeActive) {
            return;
        }

        if (Settings.getSettings().minimap_expanded) {
            renderExpanded(renderer);
        } else {
            renderCollapsed(renderer);
        }
    }

    private void renderExpanded(@NonNull GUIRenderer renderer) {
        int w = getWidth();
        int h = getHeight();

        // Background
        renderer.drawColoredQuad(0, 0, w, h, BG_COLOR);

        // Border
        renderer.drawColoredQuad(0, 0, w, BORDER_WIDTH, BORDER_COLOR);                    // bottom
        renderer.drawColoredQuad(0, h - BORDER_WIDTH, w, BORDER_WIDTH, BORDER_COLOR);     // top
        renderer.drawColoredQuad(0, 0, BORDER_WIDTH, h, BORDER_COLOR);                    // left
        renderer.drawColoredQuad(w - BORDER_WIDTH, 0, BORDER_WIDTH, h, BORDER_COLOR);     // right

        // Header bar (clickable area to collapse)
        int headerY = h - HEADER_HEIGHT - BORDER_WIDTH;
        renderer.drawColoredQuad(BORDER_WIDTH, headerY, w - 2 * BORDER_WIDTH, HEADER_HEIGHT, HEADER_COLOR);
        drawUnwalkableToggle(renderer, 0, 0, h);

        // Draw collapse indicator (small triangle or minus)
        float indicatorX = w / 2f - 4;
        float indicatorY = headerY + HEADER_HEIGHT / 2f - 2;
        renderer.drawColoredQuad(indicatorX, indicatorY, 8, 4, BORDER_COLOR);

        // Map area dimensions
        float mapX = BORDER_WIDTH;
        float mapY = BORDER_WIDTH;
        float mapW = w - 2 * BORDER_WIDTH;
        float mapH = h - HEADER_HEIGHT - 2 * BORDER_WIDTH;

        // Terrain texture
        Texture terrain = activeTerrainTexture();
        if (terrain != null) {
            renderer.drawTexture(terrain, mapX, mapY, mapW, mapH,
                    0f, 0f, 1f, 1f, Color.WHITE);
        }

        // Flush to ensure terrain is rendered before entity dots
        renderer.flush();

        // Draw entity dots on top
        renderEntities(renderer, mapX, mapY, mapW, mapH);
        
        // Viewport rectangle
        renderViewport(renderer, mapX, mapY, mapW, mapH);
    }

    private void renderCollapsed(@NonNull GUIRenderer renderer) {
        // Simple collapsed indicator
        renderer.drawColoredQuad(0, 0, COLLAPSED_SIZE, COLLAPSED_SIZE, BG_COLOR);
        renderer.drawColoredQuad(0, 0, COLLAPSED_SIZE, 2, BORDER_COLOR);
        renderer.drawColoredQuad(0, COLLAPSED_SIZE - 2, COLLAPSED_SIZE, 2, BORDER_COLOR);
        renderer.drawColoredQuad(0, 0, 2, COLLAPSED_SIZE, BORDER_COLOR);
        renderer.drawColoredQuad(COLLAPSED_SIZE - 2, 0, 2, COLLAPSED_SIZE, BORDER_COLOR);

        // Small expand indicator (plus sign or arrow)
        float cx = COLLAPSED_SIZE / 2f;
        float cy = COLLAPSED_SIZE / 2f;
        renderer.drawColoredQuad(cx - 6, cy - 1, 12, 2, BORDER_COLOR);  // horizontal
        renderer.drawColoredQuad(cx - 1, cy - 6, 2, 12, BORDER_COLOR);  // vertical
    }

    private void renderEntities(@NonNull GUIRenderer renderer, float mapX, float mapY, float mapW, float mapH) {
        var localPlayer = viewer.getLocalPlayer();
        var entities = localPlayer.getUnits().getSet();

        for (Selectable<?> entity : entities) {
            if (entity.isDead()) {
                continue;
            }

            // Convert world position to minimap position
            float worldX = entity.getPositionX();
            float worldY = entity.getPositionY();

            float normX = worldX / metersPerWorld;
            float normY = worldY / metersPerWorld;

            float dotX = mapX + normX * mapW;
            float dotY = mapY + normY * mapH;

            if (entity instanceof Building building) {
                if (building.isPlaced()) {
                    // Center the dot
                    float halfSize = BUILDING_DOT_SIZE / 2f;
                    renderer.drawColoredQuad(dotX - halfSize, dotY - halfSize,
                            BUILDING_DOT_SIZE, BUILDING_DOT_SIZE, BUILDING_COLOR);
                }
            } else if (entity instanceof Unit) {
                // Center the dot
                float halfSize = UNIT_DOT_SIZE / 2f;
                renderer.drawColoredQuad(dotX - halfSize, dotY - halfSize,
                        UNIT_DOT_SIZE, UNIT_DOT_SIZE, UNIT_COLOR);
            }
        }
    }

    /**
     * Render a white rectangle showing the current camera viewport on the minimap.
     * Uses local coordinates (relative to this GUIObject).
     */
    private void renderViewport(@NonNull GUIRenderer renderer, float mapX, float mapY, float mapW, float mapH) {
        CameraState state = viewer.getCamera().getState();
        float camX = state.getTargetX();
        float camY = state.getTargetY();
        float camZ = state.getTargetZ();

        // Estimate visible area - roughly proportional to camera height
        float viewRadius = camZ * 1.5f;

        // Convert camera position and view size to normalized coordinates
        float normCamX = camX / metersPerWorld;
        float normCamY = camY / metersPerWorld;
        float normRadiusX = viewRadius / metersPerWorld;
        float normRadiusY = viewRadius / metersPerWorld;

        // Convert to minimap pixel coordinates
        float rectCenterX = mapX + normCamX * mapW;
        float rectCenterY = mapY + normCamY * mapH;
        float rectHalfW = normRadiusX * mapW;
        float rectHalfH = normRadiusY * mapH;

        // Calculate rectangle bounds
        float left = rectCenterX - rectHalfW;
        float right = rectCenterX + rectHalfW;
        float bottom = rectCenterY - rectHalfH;
        float top = rectCenterY + rectHalfH;

        // Clamp to map area
        left = Math.max(left, mapX);
        right = Math.min(right, mapX + mapW);
        bottom = Math.max(bottom, mapY);
        top = Math.min(top, mapY + mapH);

        float width = right - left;
        float height = top - bottom;

        // Draw rectangle outline (4 lines)
        // Bottom edge
        renderer.drawColoredQuad(left, bottom, width, VIEWPORT_LINE_THICKNESS, VIEWPORT_COLOR);
        // Top edge
        renderer.drawColoredQuad(left, top - VIEWPORT_LINE_THICKNESS, width, VIEWPORT_LINE_THICKNESS, VIEWPORT_COLOR);
        // Left edge
        renderer.drawColoredQuad(left, bottom, VIEWPORT_LINE_THICKNESS, height, VIEWPORT_COLOR);
        // Right edge
        renderer.drawColoredQuad(right - VIEWPORT_LINE_THICKNESS, bottom, VIEWPORT_LINE_THICKNESS, height, VIEWPORT_COLOR);
    }

    @Override
    protected void mousePressed(@NonNull MouseButton button, int x, int y) {
        if (button == MouseButton.LEFT) {
            if (Settings.getSettings().minimap_expanded) {
                if (hitUnwalkableToggle(x, y)) {
                    toggleUnwalkableTint();
                    return;
                }

                int headerY = getHeight() - HEADER_HEIGHT - BORDER_WIDTH;

                if (y >= headerY) {
                    // Header click - toggle expand/collapse
                    toggleExpanded();
                } else if (y >= BORDER_WIDTH && x >= BORDER_WIDTH &&
                           x < getWidth() - BORDER_WIDTH && y < headerY) {
                    // Map area click - move camera to clicked location
                    float mapW = getWidth() - 2 * BORDER_WIDTH;
                    float mapH = getHeight() - HEADER_HEIGHT - 2 * BORDER_WIDTH;

                    float normX = (x - BORDER_WIDTH) / mapW;
                    float normY = (y - BORDER_WIDTH) / mapH;

                    float worldX = normX * metersPerWorld;
                    float worldY = normY * metersPerWorld;

                    moveCameraTo(worldX, worldY);
                }
            } else {
                // In collapsed mode, the whole thing is clickable to expand
                toggleExpanded();
            }
        }
    }

    private void drawUnwalkableToggle(
            @NonNull GUIRenderer renderer,
            int posX,
            int posY,
            int panelH) {
        int btnX = posX + unwalkableToggleLocalX();
        int btnY = posY + unwalkableToggleLocalY(panelH);
        boolean on = Settings.getSettings().minimap_show_unwalkable;
        renderer.drawColoredQuad(btnX, btnY, TOGGLE_SIZE, TOGGLE_SIZE, on ? TOGGLE_ON_COLOR : TOGGLE_OFF_COLOR);
        renderer.drawColoredQuad(btnX, btnY, TOGGLE_SIZE, 1, BORDER_COLOR);
        renderer.drawColoredQuad(btnX, btnY + TOGGLE_SIZE - 1, TOGGLE_SIZE, 1, BORDER_COLOR);
        renderer.drawColoredQuad(btnX, btnY, 1, TOGGLE_SIZE, BORDER_COLOR);
        renderer.drawColoredQuad(btnX + TOGGLE_SIZE - 1, btnY, 1, TOGGLE_SIZE, BORDER_COLOR);
    }

    private static int unwalkableToggleLocalX() {
        return BORDER_WIDTH + TOGGLE_PAD;
    }

    private static int unwalkableToggleLocalY(int panelH) {
        int headerY = panelH - HEADER_HEIGHT - BORDER_WIDTH;
        return headerY + (HEADER_HEIGHT - TOGGLE_SIZE) / 2;
    }

    static boolean hitUnwalkableToggle(int localX, int localY, int panelH) {
        int btnX = unwalkableToggleLocalX();
        int btnY = unwalkableToggleLocalY(panelH);
        return localX >= btnX && localX < btnX + TOGGLE_SIZE
                && localY >= btnY && localY < btnY + TOGGLE_SIZE;
    }

    private boolean hitUnwalkableToggle(int localX, int localY) {
        return hitUnwalkableToggle(localX, localY, getHeight());
    }

    private void toggleUnwalkableTint() {
        Settings settings = Settings.getSettings();
        settings.minimap_show_unwalkable = !settings.minimap_show_unwalkable;
    }

    private void toggleExpanded() {
        Settings.getSettings().minimap_expanded = !Settings.getSettings().minimap_expanded;
        updateDimensions();

        // Request parent to reposition us
        GUIRoot root = getParentGUIRoot();
        if (root != null) {
            updatePosition(root.getWidth(), root.getHeight());
        }
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
     * Used by InGameDelegate to determine if a click should be forwarded to the minimap.
     * 
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate  
     * @param screenWidth Current screen width
     * @param screenHeight Current screen height
     * @return true if the point is within the minimap area
     */
    public boolean containsScreenPoint(int screenX, int screenY, int screenWidth, int screenHeight) {
        if (!Settings.getSettings().show_minimap || mapModeActive) {
            return false;
        }
        
        updateDimensions();
        int minimapX = MARGIN_LEFT;
        int minimapY = MARGIN_BOTTOM;
        int minimapW = getWidth();
        int minimapH = getHeight();
        
        return screenX >= minimapX && screenX < minimapX + minimapW &&
               screenY >= minimapY && screenY < minimapY + minimapH;
    }

    /**
     * Handle a mouse click at screen coordinates. Called by InGameDelegate when
     * the click is within the minimap bounds.
     * 
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param screenWidth Current screen width
     * @param screenHeight Current screen height
     * @return true if the click was handled
     */
    public boolean handleScreenClick(int screenX, int screenY, int screenWidth, int screenHeight) {
        if (!Settings.getSettings().show_minimap || mapModeActive) {
            return false;
        }
        
        updateDimensions();
        int minimapX = MARGIN_LEFT;
        int minimapY = MARGIN_BOTTOM;
        
        // Convert screen coordinates to local minimap coordinates
        int localX = screenX - minimapX;
        int localY = screenY - minimapY;
        
        if (Settings.getSettings().minimap_expanded) {
            if (hitUnwalkableToggle(localX, localY)) {
                toggleUnwalkableTint();
                return true;
            }

            int headerY = getHeight() - HEADER_HEIGHT - BORDER_WIDTH;

            if (localY >= headerY) {
                // Header click - toggle expand/collapse
                toggleExpanded();
                return true;
            } else if (localY >= BORDER_WIDTH && localX >= BORDER_WIDTH &&
                       localX < getWidth() - BORDER_WIDTH && localY < headerY) {
                // Map area click - move camera to clicked location
                float mapW = getWidth() - 2 * BORDER_WIDTH;
                float mapH = getHeight() - HEADER_HEIGHT - 2 * BORDER_WIDTH;

                float normX = (localX - BORDER_WIDTH) / mapW;
                float normY = (localY - BORDER_WIDTH) / mapH;

                float worldX = normX * metersPerWorld;
                float worldY = normY * metersPerWorld;

                moveCameraTo(worldX, worldY);
                return true;
            }
        } else {
            // In collapsed mode, the whole thing is clickable to expand
            toggleExpanded();
            return true;
        }
        
        return false;
    }

    @Override
    protected void displayChangedNotify(int width, int height) {
        // Parent will handle repositioning via SelectionDelegate
    }

    @Override
    protected void doRemove() {
        // Do NOT destroy the texture here - the minimap may be temporarily removed
        // from the tree when delegates are pushed/popped, and we need the texture
        // to persist. Use dispose() for final cleanup.
        super.doRemove();
    }

    /**
     * Clean up GPU resources. Call this only when the minimap is being permanently
     * destroyed (e.g., game over, returning to menu), not during temporary delegate changes.
     */
    public void dispose() {
        if (terrainPlain != null) {
            terrainPlain.close();
            terrainPlain = null;
        }
        if (terrainUnwalkable != null) {
            terrainUnwalkable.close();
            terrainUnwalkable = null;
        }
    }
}
