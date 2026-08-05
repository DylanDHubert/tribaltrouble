package com.oddlabs.tt.landscape;

import com.oddlabs.tt.model.RacesResources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("KeyPointNamer")
class KeyPointNamerTest {

    private static final int NATIVE = RacesResources.RACE_NATIVES;
    private static final int VIKING = RacesResources.RACE_VIKINGS;

    private static final Set<String> VIKING_PEAKS = Set.of("Peak", "Crag", "Fell");
    private static final Set<String> VIKING_VALLEYS = Set.of("Vale", "Dell", "Fen");
    private static final Set<String> VIKING_LAKES = Set.of("Lake", "Tarn", "Loch");
    private static final Set<String> VIKING_FORESTS = Set.of("Holt", "Skog", "Lund", "Wald", "Wood", "Forest");

    private static final Set<String> NATIVE_PEAKS = Set.of("Butte", "Mesa", "Tor", "Knoll", "Rise", "Crown", "Spire",
            "Bluff");
    private static final Set<String> NATIVE_VALLEYS = Set.of("Basin", "Hollow", "Glade", "Glen", "Bottom", "Wash",
            "Fold", "Dell");
    private static final Set<String> NATIVE_LAKES = Set.of("Lagoon", "Pool", "Oasis", "Mere", "Water", "Wetland", "Fen",
            "Billabong");
    private static final Set<String> NATIVE_FORESTS = Set.of("Grove", "Woods", "Thicket", "Wilds", "Timber", "Forest");
    private static final Set<String> BEACHES = Set.of("Lagoon", "Inlet", "Reef", "Shoal", "Cove", "Flats", "Spit",
            "Bight");

    @Test
    @DisplayName("same race and points yield identical names")
    void deterministicForSameRace() {
        List<KeyPoint> points = sampleMap();
        assertEquals(
                names(KeyPointNamer.name(NATIVE, points)),
                names(KeyPointNamer.name(NATIVE, points)));
    }

    @Test
    @DisplayName("naming is independent of input ordering")
    void independentOfOrder() {
        List<KeyPoint> points = sampleMap();
        List<KeyPoint> shuffled = new ArrayList<>(points);
        Collections.shuffle(shuffled, new java.util.Random(1));

        List<KeyPoint> original = KeyPointNamer.name(VIKING, points);
        List<KeyPoint> reordered = KeyPointNamer.name(VIKING, shuffled);

        for (KeyPoint point : original) {
            String reorderedName = reordered.stream().filter(other -> other.worldX() == point.worldX()
                    && other.worldY() == point.worldY()
                    && other.type() == point.type()).findFirst().orElseThrow().name();
            assertEquals(point.name(), reorderedName);
        }
    }

    @Test
    @DisplayName("names are unique within a map")
    void uniqueWithinMap() {
        List<KeyPoint> named = KeyPointNamer.name(NATIVE, sampleMap());
        assertEquals(named.size(), new HashSet<>(names(named)).size());
    }

    @Test
    @DisplayName("each proper name is followed by a supplied landmark word")
    void usesSuppliedLandmarkWords() {
        for (int race : new int[]{NATIVE, VIKING}) {
            for (KeyPoint point : KeyPointNamer.name(race, sampleMap())) {
                String[] words = point.name().split(" ");
                assertEquals(2, words.length, "expected '<proper name> <landmark>': " + point.name());
                assertTrue(landmarks(point.type(), race).contains(words[1]),
                        () -> "unexpected landmark word: " + point.name());
            }
        }
    }

    @Test
    @DisplayName("proper names use both two-part and three-part constructions")
    void usesTwoAndThreePartConstructions() {
        // Proper-name syllables are concatenated, so sample broadly and compare length ranges.
        // Two-part Native names are generally 5-7 chars; three-part names are substantially longer.
        boolean sawShort = false;
        boolean sawLong = false;
        for (int i = 0; i < 300; i++) {
            KeyPoint point = new KeyPoint(KeyPointType.PEAK, i * 4f, i * 7f, 1f, "Peak");
            String proper = KeyPointNamer.name(NATIVE, List.of(point)).get(0).name().split(" ")[0];
            sawShort |= proper.length() <= 7;
            sawLong |= proper.length() >= 9;
        }
        assertTrue(sawShort, "expected start+end names");
        assertTrue(sawLong, "expected start+middle+end names");
    }

    @Test
    @DisplayName("the same landmark receives different race-specific names")
    void raceSpecificNames() {
        List<KeyPoint> points = sampleMap();
        assertNotEquals(
                names(KeyPointNamer.name(NATIVE, points)),
                names(KeyPointNamer.name(VIKING, points)));
    }

    private static List<KeyPoint> sampleMap() {
        return List.of(
                new KeyPoint(KeyPointType.PEAK, 120f, 340f, 1.0f, "Peak"),
                new KeyPoint(KeyPointType.PEAK, 300f, 80f, 0.9f, "Peak"),
                new KeyPoint(KeyPointType.VALLEY, 220f, 260f, 0.8f, "Valley"),
                new KeyPoint(KeyPointType.FOREST, 60f, 410f, 0.7f, "Forest"),
                new KeyPoint(KeyPointType.FOREST, 440f, 150f, 0.6f, "Forest"),
                new KeyPoint(KeyPointType.LAKE, 380f, 300f, 0.5f, "Lake"),
                new KeyPoint(KeyPointType.BEACH, 20f, 20f, 0.4f, "Beach"));
    }

    private static List<String> names(List<KeyPoint> points) {
        return points.stream().map(KeyPoint::name).toList();
    }

    private static Set<String> landmarks(KeyPointType type, int race) {
        boolean viking = race == VIKING;
        return switch (type) {
            case PEAK -> viking ? VIKING_PEAKS : NATIVE_PEAKS;
            case VALLEY -> viking ? VIKING_VALLEYS : NATIVE_VALLEYS;
            case LAKE -> viking ? VIKING_LAKES : NATIVE_LAKES;
            case BEACH -> BEACHES;
            case FOREST -> viking ? VIKING_FORESTS : NATIVE_FORESTS;
        };
    }
}
