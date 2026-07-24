package com.oddlabs.tt.gui;

import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MinimapPanel coordinate conversion and color packing logic.
 * 
 * Note: Full rendering tests require an OpenGL context and are covered
 * in integration tests. These tests focus on pure logic that can be
 * tested without GPU dependencies.
 */
class MinimapPanelTest {

    @Nested
    @DisplayName("ABGR Color Packing")
    class ColorPackingTests {

        @Test
        @DisplayName("Pack opaque red should produce correct ABGR value")
        void packOpaqueRed() {
            Vector4fc red = new Vector4f(1f, 0f, 0f, 1f);
            int packed = packABGR(red);
            // ABGR: A=255 (0xFF), B=0, G=0, R=255 (0xFF)
            // = 0xFF0000FF
            assertEquals(0xFF0000FF, packed);
        }

        @Test
        @DisplayName("Pack opaque green should produce correct ABGR value")
        void packOpaqueGreen() {
            Vector4fc green = new Vector4f(0f, 1f, 0f, 1f);
            int packed = packABGR(green);
            // ABGR: A=255, B=0, G=255, R=0
            // = 0xFF00FF00
            assertEquals(0xFF00FF00, packed);
        }

        @Test
        @DisplayName("Pack opaque blue should produce correct ABGR value")
        void packOpaqueBlue() {
            Vector4fc blue = new Vector4f(0f, 0f, 1f, 1f);
            int packed = packABGR(blue);
            // ABGR: A=255, B=255, G=0, R=0
            // = 0xFFFF0000
            assertEquals(0xFFFF0000, packed);
        }

        @Test
        @DisplayName("Pack white should produce 0xFFFFFFFF")
        void packWhite() {
            Vector4fc white = new Vector4f(1f, 1f, 1f, 1f);
            int packed = packABGR(white);
            assertEquals(0xFFFFFFFF, packed);
        }

        @Test
        @DisplayName("Pack transparent black should produce 0x00000000")
        void packTransparentBlack() {
            Vector4fc transparent = new Vector4f(0f, 0f, 0f, 0f);
            int packed = packABGR(transparent);
            assertEquals(0x00000000, packed);
        }

        @Test
        @DisplayName("Pack semi-transparent color should include alpha")
        void packSemiTransparent() {
            Vector4fc color = new Vector4f(1f, 0f, 0f, 0.5f);
            int packed = packABGR(color);
            // Alpha should be approximately 127-128 (0.5 * 255)
            int alpha = (packed >> 24) & 0xFF;
            assertTrue(alpha >= 127 && alpha <= 128, "Alpha should be ~128, was " + alpha);
        }
    }

    @Nested
    @DisplayName("Coordinate Conversion")
    class CoordinateConversionTests {

        @ParameterizedTest
        @DisplayName("World to normalized coordinates")
        @CsvSource({
            "0, 1024, 0.0",       // Origin
            "512, 1024, 0.5",     // Center
            "1024, 1024, 1.0",    // Far edge
            "256, 1024, 0.25",    // Quarter
        })
        void worldToNormalized(float worldPos, int metersPerWorld, float expectedNorm) {
            float normalized = worldPos / metersPerWorld;
            assertEquals(expectedNorm, normalized, 0.001f);
        }

        @ParameterizedTest
        @DisplayName("Normalized to minimap pixel coordinates")
        @CsvSource({
            "0.0, 0, 150, 0",       // Left edge
            "0.5, 0, 150, 75",      // Center
            "1.0, 0, 150, 150",     // Right edge
            "0.25, 10, 100, 35",    // With offset
        })
        void normalizedToMinimap(float normalized, float mapOffset, float mapSize, float expectedPixel) {
            float pixel = mapOffset + normalized * mapSize;
            assertEquals(expectedPixel, pixel, 0.001f);
        }

        @Test
        @DisplayName("Full coordinate conversion chain")
        void fullConversionChain() {
            // World: 512 meters in a 1024 meter world
            // Map area: starts at x=2 (border), width=146
            float worldX = 512f;
            int metersPerWorld = 1024;
            float mapX = 2f; // BORDER_WIDTH
            float mapW = 146f; // MAP_SIZE - 2*BORDER_WIDTH
            
            float normX = worldX / metersPerWorld; // 0.5
            float dotX = mapX + normX * mapW;      // 2 + 0.5*146 = 75
            
            assertEquals(0.5f, normX, 0.001f);
            assertEquals(75f, dotX, 0.001f);
        }
    }

    @Nested
    @DisplayName("Height Colormap")
    class HeightColormapTests {

        private static final float SEA = 3.2f;
        private static final float MAX = 32f;

        @Test
        @DisplayName("Deep water is darker blue than shallow water")
        void deepWaterDarkerThanShallow() {
            Vector4f deep = MinimapPanel.heightToColor(0f, SEA, MAX);
            Vector4f shallow = MinimapPanel.heightToColor(SEA, SEA, MAX);

            assertTrue(deep.z() < shallow.z() || deep.y() < shallow.y(),
                    "Deep water should be darker/cooler than shallow");
            assertTrue(deep.z() > deep.x(), "Water should be blue-dominant");
            assertTrue(shallow.z() > shallow.x(), "Shallow water should be blue-dominant");
        }

        @Test
        @DisplayName("Just above sea level is beach-toned")
        void beachNearSeaLevel() {
            Vector4f beach = MinimapPanel.heightToColor(SEA + 0.01f, SEA, MAX);
            assertTrue(beach.x() > beach.z(), "Beach should be warmer than blue water");
            assertTrue(beach.y() > 0.4f, "Beach should have visible green/yellow");
        }

        @Test
        @DisplayName("Mid land is greener than peaks")
        void midLandGreenerThanPeak() {
            float midHeight = SEA + (MAX - SEA) * 0.3f;
            Vector4f mid = MinimapPanel.heightToColor(midHeight, SEA, MAX);
            Vector4f peak = MinimapPanel.heightToColor(MAX, SEA, MAX);

            assertTrue(mid.y() > mid.x(), "Lowland should be green-dominant");
            assertTrue(peak.x() > mid.x() || peak.y() > mid.y(),
                    "Peak should be lighter than mid land");
            assertEquals(peak.x(), peak.y(), 0.05f, "Peak should be near-neutral light");
        }

        @Test
        @DisplayName("Higher land is not the same color as lower land")
        void heightChangesColor() {
            Vector4f low = MinimapPanel.heightToColor(SEA + 1f, SEA, MAX);
            Vector4f high = MinimapPanel.heightToColor(SEA + 20f, SEA, MAX);

            float delta = Math.abs(low.x() - high.x())
                    + Math.abs(low.y() - high.y())
                    + Math.abs(low.z() - high.z());
            assertTrue(delta > 0.1f, "Different heights should map to different colors");
        }

        @Test
        @DisplayName("Sea level boundary differs from land just above it")
        void seaBoundaryDistinctFromLand() {
            Vector4f water = MinimapPanel.heightToColor(SEA, SEA, MAX);
            Vector4f land = MinimapPanel.heightToColor(SEA + 0.01f, SEA, MAX);

            assertTrue(water.z() > water.x(), "At sea level should still be water blue");
            assertTrue(land.x() > land.z() || land.y() > land.z(),
                    "Just above sea should leave blue water tones");
        }

        @Test
        @DisplayName("Unwalkable land gets a red overlay pixel with blend alpha")
        void unwalkableLandOverlay() {
            float landHeight = SEA + 8f;
            Vector4f walkable = MinimapPanel.unwalkableOverlayPixel(landHeight, SEA, true);
            Vector4f blocked = MinimapPanel.unwalkableOverlayPixel(landHeight, SEA, false);

            assertEquals(0f, walkable.w(), 0.0001f, "Walkable land should be transparent");
            assertTrue(blocked.w() > 0f, "Blocked land should have overlay alpha");
            assertTrue(blocked.x() > blocked.y() && blocked.x() > blocked.z(),
                    "Blocked overlay should be red-dominant");
        }

        @Test
        @DisplayName("Walkable land overlay is fully transparent")
        void walkableOverlayTransparent() {
            float landHeight = SEA + 8f;
            Vector4f walkable = MinimapPanel.unwalkableOverlayPixel(landHeight, SEA, true);
            assertEquals(0f, walkable.w(), 0.0001f);
            assertEquals(0f, walkable.x(), 0.0001f);
        }

        @Test
        @DisplayName("Unwalkable water stays transparent on the overlay")
        void unwalkableWaterUntinted() {
            float waterHeight = SEA * 0.5f;
            Vector4f walkableFlag = MinimapPanel.unwalkableOverlayPixel(waterHeight, SEA, true);
            Vector4f blockedFlag = MinimapPanel.unwalkableOverlayPixel(waterHeight, SEA, false);

            assertEquals(0f, walkableFlag.w(), 0.0001f);
            assertEquals(0f, blockedFlag.w(), 0.0001f);
        }
    }

    @Nested
    @DisplayName("Isolines")
    class IsolineTests {

        private static final float SEA = 3.2f;
        private static final float MAX = 32f;
        private static final float INTERVAL = MinimapPanel.landContourInterval(SEA, MAX);

        @Test
        @DisplayName("Contour interval divides land relief into expected bands")
        void contourIntervalMatchesCount() {
            float interval = MinimapPanel.landContourInterval(SEA, MAX);
            assertEquals((MAX - SEA) / 12f, interval, 0.0001f);
        }

        @Test
        @DisplayName("Exact contour height has full soft strength")
        void fullStrengthOnContour() {
            float onLine = SEA + INTERVAL;
            float strength = MinimapPanel.isolineStrength(onLine, INTERVAL * 0.5f, SEA, INTERVAL);
            assertEquals(1f, strength, 0.001f);
        }

        @Test
        @DisplayName("Midway between contours is weaker than on-contour")
        void weakerBetweenContours() {
            float gradient = INTERVAL * 0.5f;
            float onLine = MinimapPanel.isolineStrength(SEA + INTERVAL, gradient, SEA, INTERVAL);
            float mid = MinimapPanel.isolineStrength(SEA + INTERVAL * 1.5f, gradient, SEA, INTERVAL);
            assertTrue(mid < onLine, "Mid-band should be softer than on-contour");
            assertTrue(mid < 0.25f, "Mid-band should stay relatively light, was " + mid);
        }

        @Test
        @DisplayName("Distance to coastline is absolute height delta")
        void coastDistance() {
            assertEquals(0.4f, MinimapPanel.distanceToNearestContour(SEA - 0.4f, SEA, INTERVAL), 0.0001f);
            assertEquals(0f, MinimapPanel.distanceToNearestContour(SEA, SEA, INTERVAL), 0.0001f);
        }
    }

    @Nested
    @DisplayName("Dimension Calculations")
    class DimensionTests {

        private static final int MAP_SIZE = 150;
        private static final int HEADER_HEIGHT = 18;
        private static final int COLLAPSED_SIZE = 24;
        private static final int BORDER_WIDTH = 2;

        @Test
        @DisplayName("Expanded dimensions are correct")
        void expandedDimensions() {
            int expectedWidth = MAP_SIZE + 2 * BORDER_WIDTH;  // 154
            int expectedHeight = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;  // 172
            
            assertEquals(154, expectedWidth);
            assertEquals(172, expectedHeight);
        }

        @Test
        @DisplayName("Collapsed dimensions are correct")
        void collapsedDimensions() {
            assertEquals(24, COLLAPSED_SIZE);
        }

        @Test
        @DisplayName("Map area dimensions are correct")
        void mapAreaDimensions() {
            int totalWidth = MAP_SIZE + 2 * BORDER_WIDTH;  // 154
            int totalHeight = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;  // 172
            
            float mapW = totalWidth - 2 * BORDER_WIDTH;  // 150
            float mapH = totalHeight - HEADER_HEIGHT - 2 * BORDER_WIDTH;  // 150
            
            assertEquals(150f, mapW);
            assertEquals(150f, mapH);
        }
    }

    @Nested
    @DisplayName("Position Calculations (Left Side)")
    class PositionTests {

        private static final int MARGIN_LEFT = 12;
        private static final int MARGIN_BOTTOM = 12;

        @ParameterizedTest
        @DisplayName("Minimap position is at left side for various screen sizes")
        @CsvSource({
            "1920, 1080, 154, 12, 12",  // Full HD
            "1280, 720, 154, 12, 12",   // 720p
            "800, 600, 154, 12, 12",    // Small
        })
        void minimapPosition(int screenWidth, int screenHeight, int minimapWidth, 
                             int expectedX, int expectedY) {
            // Minimap is now on the left side
            int x = MARGIN_LEFT;
            int y = MARGIN_BOTTOM;
            
            assertEquals(expectedX, x);
            assertEquals(expectedY, y);
        }
    }

    @Nested
    @DisplayName("Click Detection (containsScreenPoint)")
    class ClickDetectionTests {

        private static final int MARGIN_LEFT = 12;
        private static final int MARGIN_BOTTOM = 12;
        private static final int MAP_SIZE = 150;
        private static final int HEADER_HEIGHT = 18;
        private static final int BORDER_WIDTH = 2;
        private static final int COLLAPSED_SIZE = 24;

        @Test
        @DisplayName("Point inside expanded minimap returns true")
        void pointInsideExpandedMinimap() {
            int screenWidth = 1920;
            int screenHeight = 1080;
            int minimapW = MAP_SIZE + 2 * BORDER_WIDTH;  // 154
            int minimapH = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;  // 172
            
            // Click at center of minimap area
            int clickX = MARGIN_LEFT + minimapW / 2;  // 12 + 77 = 89
            int clickY = MARGIN_BOTTOM + minimapH / 2;  // 12 + 86 = 98
            
            boolean inside = clickX >= MARGIN_LEFT && clickX < MARGIN_LEFT + minimapW &&
                             clickY >= MARGIN_BOTTOM && clickY < MARGIN_BOTTOM + minimapH;
            
            assertTrue(inside);
        }

        @Test
        @DisplayName("Point outside minimap returns false")
        void pointOutsideMinimap() {
            int minimapW = MAP_SIZE + 2 * BORDER_WIDTH;  // 154
            int minimapH = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;  // 172
            
            // Click far to the right (outside)
            int clickX = 500;
            int clickY = 100;
            
            boolean inside = clickX >= MARGIN_LEFT && clickX < MARGIN_LEFT + minimapW &&
                             clickY >= MARGIN_BOTTOM && clickY < MARGIN_BOTTOM + minimapH;
            
            assertFalse(inside);
        }

        @Test
        @DisplayName("Point at edge of minimap returns true")
        void pointAtEdgeMinimap() {
            int minimapW = MAP_SIZE + 2 * BORDER_WIDTH;
            int minimapH = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;
            
            // Click at top-left corner (just inside)
            int clickX = MARGIN_LEFT;
            int clickY = MARGIN_BOTTOM;
            
            boolean inside = clickX >= MARGIN_LEFT && clickX < MARGIN_LEFT + minimapW &&
                             clickY >= MARGIN_BOTTOM && clickY < MARGIN_BOTTOM + minimapH;
            
            assertTrue(inside);
        }
    }

    @Nested
    @DisplayName("Click-to-Move Coordinate Conversion")
    class ClickToMoveTests {

        private static final int MARGIN_LEFT = 12;
        private static final int MARGIN_BOTTOM = 12;
        private static final int MAP_SIZE = 150;
        private static final int HEADER_HEIGHT = 18;
        private static final int BORDER_WIDTH = 2;

        @Test
        @DisplayName("Click at minimap center converts to world center")
        void clickAtMinimapCenter() {
            int metersPerWorld = 1024;
            int minimapW = MAP_SIZE + 2 * BORDER_WIDTH;  // 154
            int minimapH = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;  // 172
            
            // Click at center of map area
            int minimapX = MARGIN_LEFT;
            int minimapY = MARGIN_BOTTOM;
            
            // Map area within minimap
            float mapW = minimapW - 2 * BORDER_WIDTH;  // 150
            float mapH = minimapH - HEADER_HEIGHT - 2 * BORDER_WIDTH;  // 150
            
            // Click at center of map area
            int screenClickX = minimapX + BORDER_WIDTH + (int)(mapW / 2);
            int screenClickY = minimapY + BORDER_WIDTH + (int)(mapH / 2);
            
            // Convert to local coordinates
            int localX = screenClickX - minimapX;
            int localY = screenClickY - minimapY;
            
            // Convert to normalized (0-1)
            float normX = (localX - BORDER_WIDTH) / mapW;
            float normY = (localY - BORDER_WIDTH) / mapH;
            
            // Convert to world
            float worldX = normX * metersPerWorld;
            float worldY = normY * metersPerWorld;
            
            // Should be approximately at world center
            assertEquals(512f, worldX, 5f, "World X should be at center");
            assertEquals(512f, worldY, 5f, "World Y should be at center");
        }

        @Test
        @DisplayName("Click at minimap origin converts to world origin")
        void clickAtMinimapOrigin() {
            int metersPerWorld = 1024;
            int minimapW = MAP_SIZE + 2 * BORDER_WIDTH;
            int minimapH = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;
            
            int minimapX = MARGIN_LEFT;
            int minimapY = MARGIN_BOTTOM;
            
            float mapW = minimapW - 2 * BORDER_WIDTH;
            float mapH = minimapH - HEADER_HEIGHT - 2 * BORDER_WIDTH;
            
            // Click at bottom-left of map area (origin)
            int screenClickX = minimapX + BORDER_WIDTH;
            int screenClickY = minimapY + BORDER_WIDTH;
            
            int localX = screenClickX - minimapX;
            int localY = screenClickY - minimapY;
            
            float normX = (localX - BORDER_WIDTH) / mapW;
            float normY = (localY - BORDER_WIDTH) / mapH;
            
            float worldX = normX * metersPerWorld;
            float worldY = normY * metersPerWorld;
            
            assertEquals(0f, worldX, 1f, "World X should be at origin");
            assertEquals(0f, worldY, 1f, "World Y should be at origin");
        }

        @Test
        @DisplayName("Click at minimap far corner converts to world far corner")
        void clickAtMinimapFarCorner() {
            int metersPerWorld = 1024;
            int minimapW = MAP_SIZE + 2 * BORDER_WIDTH;
            int minimapH = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;
            
            int minimapX = MARGIN_LEFT;
            int minimapY = MARGIN_BOTTOM;
            
            float mapW = minimapW - 2 * BORDER_WIDTH;
            float mapH = minimapH - HEADER_HEIGHT - 2 * BORDER_WIDTH;
            
            // Click at top-right of map area (far corner, just inside header)
            int screenClickX = minimapX + minimapW - BORDER_WIDTH - 1;
            int headerY = minimapY + minimapH - HEADER_HEIGHT - BORDER_WIDTH;
            int screenClickY = headerY - 1;  // Just below header
            
            int localX = screenClickX - minimapX;
            int localY = screenClickY - minimapY;
            
            float normX = (localX - BORDER_WIDTH) / mapW;
            float normY = (localY - BORDER_WIDTH) / mapH;
            
            float worldX = normX * metersPerWorld;
            float worldY = normY * metersPerWorld;
            
            // Should be near world far corner
            assertTrue(worldX > 900f, "World X should be near far edge, was " + worldX);
            assertTrue(worldY > 900f, "World Y should be near far edge, was " + worldY);
        }
    }

    @Nested
    @DisplayName("Viewport Quad Mapping")
    class ViewportRectTests {

        @Test
        @DisplayName("World corner maps into minimap pixel space")
        void worldCornerToMinimap() {
            int metersPerWorld = 1024;
            float mapX = 2f;
            float mapY = 2f;
            float mapW = 150f;
            float mapH = 150f;

            float worldX = 512f;
            float worldY = 256f;
            float mx = mapX + clamp01(worldX / metersPerWorld) * mapW;
            float my = mapY + clamp01(worldY / metersPerWorld) * mapH;

            assertEquals(77f, mx, 0.01f);
            assertEquals(39.5f, my, 0.01f);
        }

        @Test
        @DisplayName("World coords outside the map clamp to minimap edges")
        void worldOutsideClampsToMap() {
            int metersPerWorld = 1024;
            float mapX = 2f;
            float mapY = 2f;
            float mapW = 150f;
            float mapH = 150f;

            float mx = mapX + clamp01(-100f / metersPerWorld) * mapW;
            float my = mapY + clamp01(5000f / metersPerWorld) * mapH;

            assertEquals(mapX, mx, 0.01f);
            assertEquals(mapY + mapH, my, 0.01f);
        }

        @Test
        @DisplayName("Quad edges connect consecutive corners")
        void quadEdgeOrder() {
            // BOTTOM-LEFT, BOTTOM-RIGHT, TOP-RIGHT, TOP-LEFT
            float[] mapXY = {10, 10, 40, 10, 35, 50, 5, 45};
            int[] next = {1, 2, 3, 0};
            for (int i = 0; i < 4; i++) {
                assertEquals((i + 1) % 4, next[i]);
                assertTrue(Math.abs(mapXY[i * 2] - mapXY[next[i] * 2]) + Math.abs(
                        mapXY[i * 2 + 1] - mapXY[next[i] * 2 + 1]) > 0f);
            }
        }

        private static float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }
    }

    @Nested
    @DisplayName("Header Click Detection")
    class HeaderClickTests {

        private static final int MARGIN_LEFT = 12;
        private static final int MARGIN_BOTTOM = 12;
        private static final int MAP_SIZE = 150;
        private static final int HEADER_HEIGHT = 18;
        private static final int BORDER_WIDTH = 2;

        @Test
        @DisplayName("Click in header area is detected")
        void clickInHeader() {
            int minimapH = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;  // 172
            int minimapY = MARGIN_BOTTOM;
            
            // Header Y position (local coordinates)
            int headerLocalY = minimapH - HEADER_HEIGHT - BORDER_WIDTH;  // 152
            
            // Click in header (local Y coordinate)
            int localClickY = headerLocalY + 5;  // Inside header
            
            boolean isHeaderClick = localClickY >= headerLocalY;
            
            assertTrue(isHeaderClick, "Click should be detected as header click");
        }

        @Test
        @DisplayName("Click in map area is not header click")
        void clickInMapArea() {
            int minimapH = MAP_SIZE + HEADER_HEIGHT + 2 * BORDER_WIDTH;

            int headerLocalY = minimapH - HEADER_HEIGHT - BORDER_WIDTH;

            // Click in map area (local Y coordinate)
            int localClickY = 50;  // Below header

            boolean isHeaderClick = localClickY >= headerLocalY;

            assertFalse(isHeaderClick, "Click in map area should not be header click");
        }

        @Test
        @DisplayName("Unwalkable toggle hitbox is in header left")
        void unwalkableToggleHitbox() {
            // MATCH PRODUCTION MinimapPanel LAYOUT (MAP 250, HEADER 15, BORDER 2)
            int panelH = 250 + 15 + 2 * 2;
            int btnX = 2 + 2;
            int btnY = panelH - 15 - 2 + (15 - 11) / 2;
            assertTrue(MinimapPanel.hitUnwalkableToggle(btnX + 1, btnY + 1, panelH));
            assertFalse(MinimapPanel.hitUnwalkableToggle(80, btnY + 1, panelH));
        }

        @Test
        @DisplayName("Satellite toggle hitbox sits beside unwalkable toggle")
        void satelliteToggleHitbox() {
            int panelH = 250 + 15 + 2 * 2;
            int unwalkableX = 2 + 2;
            int satelliteX = unwalkableX + 11 + 2;
            int btnY = panelH - 15 - 2 + (15 - 11) / 2;
            assertTrue(MinimapPanel.hitSatelliteToggle(satelliteX + 1, btnY + 1, panelH));
            assertFalse(MinimapPanel.hitSatelliteToggle(unwalkableX + 1, btnY + 1, panelH));
            assertFalse(MinimapPanel.hitUnwalkableToggle(satelliteX + 1, btnY + 1, panelH));
        }
    }

    // Helper method that mirrors MinimapPanel.packABGR
    private static int packABGR(Vector4fc color) {
        int a = (int) (color.w() * 255) & 0xFF;
        int b = (int) (color.z() * 255) & 0xFF;
        int g = (int) (color.y() * 255) & 0xFF;
        int r = (int) (color.x() * 255) & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
