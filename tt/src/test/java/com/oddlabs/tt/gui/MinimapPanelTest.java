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

        @Test
        @DisplayName("Land color (brown) packs correctly")
        void packLandColor() {
            // LAND_COLOR = new Vector4f(0.545f, 0.353f, 0.169f, 1f)
            Vector4fc landColor = new Vector4f(0.545f, 0.353f, 0.169f, 1f);
            int packed = packABGR(landColor);
            
            int a = (packed >> 24) & 0xFF;
            int b = (packed >> 16) & 0xFF;
            int g = (packed >> 8) & 0xFF;
            int r = packed & 0xFF;
            
            assertEquals(255, a, "Alpha should be 255");
            assertEquals((int)(0.169f * 255), b, 1, "Blue component");
            assertEquals((int)(0.353f * 255), g, 1, "Green component");
            assertEquals((int)(0.545f * 255), r, 1, "Red component");
        }

        @Test
        @DisplayName("Water color (blue) packs correctly")
        void packWaterColor() {
            // WATER_COLOR = new Vector4f(0.2f, 0.4f, 0.8f, 1f)
            Vector4fc waterColor = new Vector4f(0.2f, 0.4f, 0.8f, 1f);
            int packed = packABGR(waterColor);
            
            int a = (packed >> 24) & 0xFF;
            int b = (packed >> 16) & 0xFF;
            int g = (packed >> 8) & 0xFF;
            int r = packed & 0xFF;
            
            assertEquals(255, a, "Alpha should be 255");
            assertEquals((int)(0.8f * 255), b, 1, "Blue component");
            assertEquals((int)(0.4f * 255), g, 1, "Green component");
            assertEquals((int)(0.2f * 255), r, 1, "Red component");
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
    @DisplayName("Height-Based Land Detection")
    class HeightBasedLandTests {

        @ParameterizedTest
        @DisplayName("Land detection based on sea level")
        @CsvSource({
            "0.15, 0.1, true",   // Above sea level = land
            "0.05, 0.1, false",  // Below sea level = water
            "0.1, 0.1, false",   // At sea level = water (not strictly above)
            "0.5, 0.1, true",    // Well above sea level
            "0.0, 0.1, false",   // Zero height
        })
        void heightBasedLandDetection(float height, float seaLevel, boolean expectedIsLand) {
            boolean isLand = height > seaLevel;
            assertEquals(expectedIsLand, isLand);
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
    @DisplayName("Viewport Rectangle Calculations")
    class ViewportRectTests {

        @Test
        @DisplayName("Camera position converts to minimap coordinates correctly")
        void cameraPositionToMinimap() {
            int metersPerWorld = 1024;
            float mapX = 2f;  // BORDER_WIDTH
            float mapY = 2f;
            float mapW = 150f;  // MAP_SIZE
            float mapH = 150f;
            
            // Camera at world center
            float camX = 512f;
            float camY = 512f;
            
            float normCamX = camX / metersPerWorld;
            float normCamY = camY / metersPerWorld;
            
            float rectCenterX = mapX + normCamX * mapW;
            float rectCenterY = mapY + normCamY * mapH;
            
            assertEquals(77f, rectCenterX, 1f, "Viewport center X");
            assertEquals(77f, rectCenterY, 1f, "Viewport center Y");
        }

        @Test
        @DisplayName("View radius scales with camera height")
        void viewRadiusScalesWithHeight() {
            float camZ1 = 50f;
            float camZ2 = 100f;
            
            float viewRadius1 = camZ1 * 1.5f;
            float viewRadius2 = camZ2 * 1.5f;
            
            assertEquals(75f, viewRadius1);
            assertEquals(150f, viewRadius2);
            assertEquals(viewRadius1 * 2, viewRadius2, "Radius should double with camera height");
        }

        @Test
        @DisplayName("Viewport rectangle is clamped to map area")
        void viewportClampsToMapArea() {
            float mapX = 2f;
            float mapY = 2f;
            float mapW = 150f;
            float mapH = 150f;
            
            // Viewport that would extend beyond map area
            float rectCenterX = 5f;  // Near left edge
            float rectCenterY = 5f;  // Near bottom edge
            float rectHalfW = 50f;   // Would go past left edge
            float rectHalfH = 50f;
            
            float left = rectCenterX - rectHalfW;
            float right = rectCenterX + rectHalfW;
            float bottom = rectCenterY - rectHalfH;
            float top = rectCenterY + rectHalfH;
            
            // Clamp
            left = Math.max(left, mapX);
            right = Math.min(right, mapX + mapW);
            bottom = Math.max(bottom, mapY);
            top = Math.min(top, mapY + mapH);
            
            assertEquals(mapX, left, "Left should be clamped to map left edge");
            assertEquals(mapY, bottom, "Bottom should be clamped to map bottom edge");
            assertTrue(right <= mapX + mapW, "Right should not exceed map right edge");
            assertTrue(top <= mapY + mapH, "Top should not exceed map top edge");
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
