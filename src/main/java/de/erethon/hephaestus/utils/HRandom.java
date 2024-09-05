package de.erethon.hephaestus.utils;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Some utility methods for randomness and loading weights from a config
public class HRandom {

    public static <T> Map<T, Integer> loadWeights(YamlConfiguration config, String path) {
        Map<T, Integer> weights = new HashMap<>();
        if (path.equals("random.rarity")) {
            Map<String, Object> rarities = config.getConfigurationSection(path).getValues(false);
            for (Map.Entry<String, Object> entry : rarities.entrySet()) {
                weights.put((T) entry.getKey(), (Integer) entry.getValue());
            }
        } else {
            List<Map<?, ?>> items = config.getMapList(path);
            for (Map<?, ?> item : items) {
                double min, max;
                boolean isInt;
                if (item.get("min") instanceof Integer) {
                    min = (Integer) item.get("min");
                    isInt = true;
                } else {
                    min = (double) item.get("min");
                    isInt = false;
                }
                if (item.get("max") instanceof Integer) {
                    max = (Integer) item.get("max");
                } else {
                    max = (double) item.get("max");
                }
                int weight = (Integer) item.get("weight");
                if (isInt) {
                    for (int i = (int) min; i <= (int) max; i++) {
                        weights.put((T) Integer.valueOf(i), weight);
                    }
                } else {
                    for (double i = min; i <= max; i++) {
                        weights.put((T) Double.valueOf(i), weight);
                    }
                }
            }
        }
        return weights;
    }

    public static <T> T selectWeightedRandomValue(Map<T, Integer> weights) {
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        int randomIndex = ThreadLocalRandom.current().nextInt(totalWeight);
        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            randomIndex -= entry.getValue();
            if (randomIndex < 0) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Something went wrong with the weighted random selection");
    }
}
