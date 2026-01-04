package de.erethon.hephaestus.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Some utility methods for randomness and loading weights from a config
public class HRandom {

    /**
     * Select a random double value from weighted ranges using a triangular distribution.
     * Each range has a weight, and within each range values are distributed with a bias toward the center.
     *
     * @param rangeWeights Map of (min, max) pairs to their weights
     * @return A random double value from the weighted ranges
     */
    public static double selectWeightedCurveValue(Map<double[], Integer> rangeWeights) {
        if (rangeWeights == null || rangeWeights.isEmpty()) {
            throw new IllegalArgumentException("Range weights map cannot be null or empty.");
        }

        // First, select which range to use based on weights
        int totalWeight = rangeWeights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be positive.");
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(totalWeight);
        double[] selectedRange = null;
        for (Map.Entry<double[], Integer> entry : rangeWeights.entrySet()) {
            randomIndex -= entry.getValue();
            if (randomIndex < 0) {
                selectedRange = entry.getKey();
                break;
            }
        }

        if (selectedRange == null || selectedRange.length < 2) {
            throw new IllegalStateException("Failed to select a valid range.");
        }

        double min = selectedRange[0];
        double max = selectedRange[1];

        // Use triangular distribution within the range (biased toward center)
        // This creates a smooth curve where middle values are more likely
        double u1 = ThreadLocalRandom.current().nextDouble();
        double u2 = ThreadLocalRandom.current().nextDouble();
        double triangular = (u1 + u2) / 2.0; // Average of two uniform randoms creates triangular

        return min + (max - min) * triangular;
    }

    /**
     * Select a random double value from weighted ranges with configurable curve bias.
     *
     * @param rangeWeights Map of (min, max) pairs to their weights
     * @param biasPower   Power to apply to the random value
     * @return A random double value from the weighted ranges
     */
    public static double selectWeightedCurveValue(Map<double[], Integer> rangeWeights, double biasPower) {
        if (rangeWeights == null || rangeWeights.isEmpty()) {
            throw new IllegalArgumentException("Range weights map cannot be null or empty.");
        }

        // Select which range to use based on weights
        int totalWeight = rangeWeights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be positive.");
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(totalWeight);
        double[] selectedRange = null;
        for (Map.Entry<double[], Integer> entry : rangeWeights.entrySet()) {
            randomIndex -= entry.getValue();
            if (randomIndex < 0) {
                selectedRange = entry.getKey();
                break;
            }
        }

        if (selectedRange == null || selectedRange.length < 2) {
            throw new IllegalStateException("Failed to select a valid range.");
        }

        double min = selectedRange[0];
        double max = selectedRange[1];

        // Apply bias power to create curve
        double rand = ThreadLocalRandom.current().nextDouble();
        double biased = Math.pow(rand, biasPower);

        return min + (max - min) * biased;
    }

    public static <T> Map<T, Integer> loadWeights(YamlConfiguration config, String path) {
        Map<T, Integer> weights = new HashMap<>();

        // Try list-of-range-maps first (e.g. random.level: - min/max/weight ...)
        // getMapList always returns a non-null list (possibly empty)
        List<Map<?, ?>> mapList = config.getMapList(path);
        if (!mapList.isEmpty()) {
            for (Map<?, ?> item : mapList) {
                if (item == null) {
                    System.err.println("Warning: Null entry in list at '" + path + "'");
                    continue;
                }
                Object minObj = item.get("min");
                Object maxObj = item.get("max");
                Object weightObj = item.get("weight");

                if (minObj == null || maxObj == null || weightObj == null) {
                    System.err.println("Warning: Malformed entry in '" + path + "' (needs min,max,weight): " + item);
                    continue;
                }
                if (!(weightObj instanceof Number)) {
                    System.err.println("Warning: Weight not numeric in '" + path + "': " + weightObj);
                    continue;
                }
                if (!(minObj instanceof Number) || !(maxObj instanceof Number)) {
                    System.err.println("Warning: min/max not numeric in '" + path + "': min=" + minObj + ", max=" + maxObj);
                    continue;
                }

                int weight = ((Number) weightObj).intValue();
                if (weight <= 0) {
                    continue;
                }

                double min = ((Number) minObj).doubleValue();
                double max = ((Number) maxObj).doubleValue();

                boolean intRange = (minObj instanceof Integer || minObj instanceof Long)
                        && (maxObj instanceof Integer || maxObj instanceof Long);

                if (max < min) {
                    System.err.println("Warning: max < min in '" + path + "': " + item);
                    continue;
                }

                if (intRange) {
                    for (int i = (int) min; i <= (int) max; i++) {
                        //noinspection unchecked
                        weights.put((T) Integer.valueOf(i), weight);
                    }
                } else {
                    for (double d = min; d <= max + 1e-9; d += 1.0) {
                        // coarse stepping
                        //noinspection unchecked
                        weights.put((T) Double.valueOf(d), weight);
                    }
                }
            }
            return weights;
        }

        // Fall back to key->weight section (rarity / slots style)
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return weights;
        }
        Map<String, Object> directValues = section.getValues(false);

        boolean allNumericKeys = directValues.keySet().stream().allMatch(k -> tryParseNumber(k) != null);

        for (Map.Entry<String, Object> entry : directValues.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Number) {
                int weight = ((Number) val).intValue();
                if (weight <= 0) continue;

                if (allNumericKeys) {
                    Number num = tryParseNumber(entry.getKey());
                    if (num == null) continue;
                    Object keyObj = isIntegralNumber(num) ? Integer.valueOf(num.intValue()) : Double.valueOf(num.doubleValue());
                    //noinspection unchecked
                    weights.put((T) keyObj, weight);
                } else {
                    //noinspection unchecked
                    weights.put((T) entry.getKey(), weight);
                }
            } else {
                System.err.println("Warning: Expected numeric value for '" + entry.getKey() + "' in '" + path + "', got " + (val == null ? "null" : val.getClass().getSimpleName()));
            }
        }
        return weights;
    }

    public static <T> T selectWeightedRandomValue(Map<T, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("Weights map cannot be null or empty for weighted random selection.");
        }
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be positive for weighted random selection.");
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(totalWeight);
        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            randomIndex -= entry.getValue();
            if (randomIndex < 0) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Something went wrong with the weighted random selection calculation. Total weight: " + totalWeight);
    }

    public static <T extends Comparable<T>> T selectWeightedRandomValue(Map<T, Integer> weights, T minValue) {
        return selectWeightedRandomValue(weights, minValue, null);
    }

    public static <T extends Comparable<T>> T selectWeightedRandomValue(Map<T, Integer> weights, T minValue, T maxValue) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("Weights map cannot be null or empty for weighted random selection.");
        }
        if (minValue == null && maxValue == null) {
            return selectWeightedRandomValue(weights);
        }

        int totalWeight = weights.entrySet().stream()
                .filter(e -> isInRange(e.getKey(), minValue, maxValue))
                .mapToInt(Map.Entry::getValue)
                .sum();

        // If no items in range, fall back to selecting from all items
        if (totalWeight <= 0) {
            return selectWeightedRandomValue(weights);
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(totalWeight);
        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            if (!isInRange(entry.getKey(), minValue, maxValue)) continue;
            randomIndex -= entry.getValue();
            if (randomIndex < 0) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Something went wrong with the weighted random selection (with range). Total weight: " + totalWeight + ", minValue: " + minValue + ", maxValue: " + maxValue);
    }

    // We might have mixed types in the map, so we need to be tolerant
    private static boolean isInRange(Object key, Object minValue, Object maxValue) {
        return isAboveOrEqual(key, minValue) && isBelowOrEqual(key, maxValue);
    }

    // We might have mixed types in the map, so we need to be tolerant
    @SuppressWarnings("unchecked")
    private static boolean isAboveOrEqual(Object key, Object minValue) {
        if (key == null) return false;
        if (minValue == null) return true;

        if (key.getClass().isInstance(minValue) && key instanceof Comparable<?> cmp) {
            try {
                int c = ((Comparable<Object>) cmp).compareTo(minValue);
                return c >= 0;
            } catch (ClassCastException ignored) {
                // Fall through to tolerant checks
            }
        }

        if (key instanceof Number kNum && minValue instanceof Number mNum) {
            return Double.compare(kNum.doubleValue(), mNum.doubleValue()) >= 0;
        }

        if (key instanceof String kStr && minValue instanceof Number mNum2) {
            try {
                double kd = Double.parseDouble(kStr.trim());
                return Double.compare(kd, mNum2.doubleValue()) >= 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        if (key instanceof Number kNum2 && minValue instanceof String mStr) {
            try {
                double md = Double.parseDouble(mStr.trim());
                return Double.compare(kNum2.doubleValue(), md) >= 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        if (key instanceof String ks && minValue instanceof String ms) {
            return ks.compareTo(ms) >= 0;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean isBelowOrEqual(Object key, Object maxValue) {
        if (key == null) return false;
        if (maxValue == null) return true;

        if (key.getClass().isInstance(maxValue) && key instanceof Comparable<?> cmp) {
            try {
                int c = ((Comparable<Object>) cmp).compareTo(maxValue);
                return c <= 0;
            } catch (ClassCastException ignored) {
                // Fall through to tolerant checks
            }
        }

        if (key instanceof Number kNum && maxValue instanceof Number mNum) {
            return Double.compare(kNum.doubleValue(), mNum.doubleValue()) <= 0;
        }

        if (key instanceof String kStr && maxValue instanceof Number mNum2) {
            try {
                double kd = Double.parseDouble(kStr.trim());
                return Double.compare(kd, mNum2.doubleValue()) <= 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        if (key instanceof Number kNum2 && maxValue instanceof String mStr) {
            try {
                double md = Double.parseDouble(mStr.trim());
                return Double.compare(kNum2.doubleValue(), md) <= 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        if (key instanceof String ks && maxValue instanceof String ms) {
            return ks.compareTo(ms) <= 0;
        }

        return false;
    }

    // Helpers to parse numeric-like keys from YAML
    private static Number tryParseNumber(String s) {
        if (s == null) return null;
        String t = s.trim();
        try {
            // Prefer integer when possible
            if (t.matches("[+-]?\\d+")) {
                return Integer.parseInt(t);
            }
            // Fallback to double
            double d = Double.parseDouble(t);
            return d;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isIntegralNumber(Number n) {
        if (n instanceof Integer || n instanceof Long) return true;
        double d = n.doubleValue();
        return Math.abs(d - Math.rint(d)) < 1e-9;
    }
}