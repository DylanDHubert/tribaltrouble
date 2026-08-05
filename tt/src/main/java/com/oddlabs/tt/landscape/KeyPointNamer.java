package com.oddlabs.tt.landscape;

import com.oddlabs.tt.model.RacesResources;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds deterministic, race-specific place names from supplied start, middle and end syllables.
 *
 * <p>The proper-name portion has either two parts ({@code start + end}) or three
 * ({@code start + middle + end}), followed by a landmark word appropriate to the race and
 * geographic type. Since the random seed is derived from map position, type and race, players
 * using the same race on the same generated map see identical names without network exchange.
 */
public final class KeyPointNamer {

    private static final String[] VIKING_STARTS = {"Bal", "Bar", "Bel", "Bjor", "Bor", "Bran", "Bro", "Dag", "Eld", "Fal", "Fen", "Fjor", "Fol", "Gar", "Gor", "Gud", "Hal", "Har", "Hol", "Ing", "Karl", "Kel", "Knut", "Lod", "Ol", "Orm", "Rag", "Rol", "Sig", "Skal", "Skar", "Sten", "Stor", "Sve", "Tor", "Ulf", "Val", "Var", "Vid",
    };
    private static final String[] VIKING_MIDDLES = {"a", "e", "o", "ar", "or", "an", "en", "ol", "al", "el", "in", "ing", "orn", "old", "und", "vald", "rik", "sten", "gard", "vik", "holm", "dal",
    };
    private static final String[] VIKING_ENDS = {"a", "ar", "en", "er", "in", "o", "or", "rik", "vald", "vik", "holm", "gard", "dal", "lund", "mark", "fjord", "havn", "fell",
    };

    private static final String[] NATIVE_STARTS = {"Aka", "Ama", "Ana", "Aro", "Awa", "Eko", "Ema", "Haka", "Hana", "Heko", "Ika", "Ina", "Kala", "Kama", "Kana", "Karo", "Kela", "Kena", "Kora", "Laka", "Loma", "Maka", "Mala", "Mana", "Meko", "Mora", "Naka", "Nala", "Noma", "Paka", "Pala", "Raka", "Saka", "Sela", "Taka", "Tala", "Toma", "Waka", "Yana", "Zala",
    };
    private static final String[] NATIVE_MIDDLES = {"hana", "lani", "moro", "naki", "tawa", "kira", "loma", "sena", "wari", "nalo", "meka", "tari", "kano", "mali", "raka", "wena", "soro", "pana", "kela", "nari", "holo", "mira", "kowa", "sala", "maku", "talo", "numa", "yari", "bena", "kumi",
    };
    private static final String[] NATIVE_ENDS = {"na", "ra", "ka", "la", "ta", "ma", "wa", "sha", "sa", "ri", "ni", "li", "lo", "mo", "ko", "tu", "ya", "we", "no", "ho",
    };

    private static final String[] VIKING_PEAKS = {"Peak", "Crag", "Fell"};
    private static final String[] VIKING_VALLEYS = {"Vale", "Dell", "Fen"};
    private static final String[] VIKING_LAKES = {"Lake", "Tarn", "Loch"};

    private static final String[] NATIVE_PEAKS = {"Butte", "Mesa", "Tor", "Knoll", "Rise", "Crown", "Spire", "Bluff",
    };
    private static final String[] NATIVE_VALLEYS = {"Basin", "Hollow", "Glade", "Glen", "Bottom", "Wash", "Fold", "Dell",
    };
    private static final String[] NATIVE_LAKES = {"Lagoon", "Pool", "Oasis", "Mere", "Water", "Wetland", "Fen", "Billabong",
    };
    private static final String[] BEACHES = {"Lagoon", "Inlet", "Reef", "Shoal", "Cove", "Flats", "Spit", "Bight",
    };

    // Forest landmark words were not in the supplied lists, so these complete the five types.
    private static final String[] VIKING_FORESTS = {"Holt", "Skog", "Lund", "Wald", "Wood", "Forest",
    };
    private static final String[] NATIVE_FORESTS = {"Grove", "Woods", "Thicket", "Wilds", "Timber", "Forest",
    };

    private static final int MAX_UNIQUE_ATTEMPTS = 32;

    private KeyPointNamer() {
    }

    /**
     * Return a copy of {@code points} with unique generated names. Assignment uses canonical map
     * position order so input ordering cannot make clients disagree.
     */
    public static @NonNull List<KeyPoint> name(int race, @NonNull List<KeyPoint> points) {
        Integer[] order = new Integer[points.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> points.get(i).worldX()).thenComparingDouble(
                i -> points.get(i).worldY()).thenComparingInt(i -> points.get(i).type().ordinal()));

        String[] names = new String[points.size()];
        Set<String> used = new HashSet<>();
        for (int index : order) {
            String generated = uniqueName(race, points.get(index), used);
            used.add(generated);
            names[index] = generated;
        }

        List<KeyPoint> named = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            KeyPoint point = points.get(i);
            named.add(new KeyPoint(
                    point.type(), point.worldX(), point.worldY(), point.score(), names[i]));
        }
        return named;
    }

    private static @NonNull String uniqueName(
            int race,
            @NonNull KeyPoint point,
            @NonNull Set<String> used) {
        String candidate = compose(race, point, 0);
        for (int salt = 1; used.contains(candidate) && salt < MAX_UNIQUE_ATTEMPTS; salt++) {
            candidate = compose(race, point, salt);
        }
        return candidate;
    }

    private static @NonNull String compose(int race, @NonNull KeyPoint point, int salt) {
        Random random = new Random(seed(point, race, salt));
        String start = pick(starts(race), random);
        String middle = pick(middles(race), random);
        String end = pick(ends(race), random);
        String properName = random.nextBoolean() ? start + end : start + middle + end;
        return properName + " " + pick(landmarks(point.type(), race), random);
    }

    private static long seed(@NonNull KeyPoint point, int race, int salt) {
        long key = 1125899906842597L;
        key = key * 31 + Float.floatToIntBits(point.worldX());
        key = key * 31 + Float.floatToIntBits(point.worldY());
        key = key * 31 + point.type().ordinal();
        key = key * 31 + race;
        key = key * 31 + salt;
        return key;
    }

    private static @NonNull String pick(String @NonNull [] pool, @NonNull Random random) {
        return pool[random.nextInt(pool.length)];
    }

    private static String @NonNull [] starts(int race) {
        return isViking(race) ? VIKING_STARTS : NATIVE_STARTS;
    }

    private static String @NonNull [] middles(int race) {
        return isViking(race) ? VIKING_MIDDLES : NATIVE_MIDDLES;
    }

    private static String @NonNull [] ends(int race) {
        return isViking(race) ? VIKING_ENDS : NATIVE_ENDS;
    }

    private static String @NonNull [] landmarks(@NonNull KeyPointType type, int race) {
        boolean viking = isViking(race);
        return switch (type) {
            case PEAK -> viking ? VIKING_PEAKS : NATIVE_PEAKS;
            case VALLEY -> viking ? VIKING_VALLEYS : NATIVE_VALLEYS;
            case LAKE -> viking ? VIKING_LAKES : NATIVE_LAKES;
            case BEACH -> BEACHES;
            case FOREST -> viking ? VIKING_FORESTS : NATIVE_FORESTS;
        };
    }

    private static boolean isViking(int race) {
        return race == RacesResources.RACE_VIKINGS;
    }
}
