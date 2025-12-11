package de.erethon.hephaestus.items.upgrades;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.utils.HRandom;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.attribute.CraftAttribute;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.*;

public class HAttributeModifyingUpgrade extends HItemUpgrade {

    private final Map<Holder<Attribute>, Map<Integer, Map<double[], Integer>>> attributeModifiers = new HashMap<>();

    public Map<Holder<Attribute>, Map<Integer, Map<double[], Integer>>> getAttributeDefinition() {
        return Collections.unmodifiableMap(attributeModifiers);
    }

    public HAttributeModifyingUpgrade() {
    }

    @Override
    public HRolledUpgrade roll(HItemStack stack) {
        return rollAtLevel(stack, stack.getItemLevel());
    }

    @Override
    public HRolledUpgrade rollAtLevel(HItemStack stack, int level) {
        int itemLevel = level; // use supplied level instead of stack's level
        Hephaestus.INSTANCE.getLogger().info("Rolling upgrade " + getId() + " at level " + itemLevel);
        Hephaestus.INSTANCE.getLogger().info("attributeModifiers map size: " + attributeModifiers.size());
        var valueTag = new net.minecraft.nbt.CompoundTag();
        for (Map.Entry<Holder<Attribute>, Map<Integer, Map<double[], Integer>>> entry : attributeModifiers.entrySet()) {
            Map<Integer, Map<double[], Integer>> perLevel = entry.getValue();
            Hephaestus.INSTANCE.getLogger().info("Processing attribute, perLevel keys: " + perLevel.keySet());
            Map<double[], Integer> levelModifiers = perLevel.get(itemLevel);
            if (levelModifiers == null) {
                // find nearest lower level definition
                int best = Integer.MIN_VALUE;
                for (Integer defined : perLevel.keySet()) {
                    if (defined <= itemLevel && defined > best) best = defined;
                }
                if (best != Integer.MIN_VALUE) {
                    levelModifiers = perLevel.get(best);
                    Hephaestus.INSTANCE.getLogger().info("Using fallback level " + best + " for item level " + itemLevel);
                }
            }
            if (levelModifiers == null || levelModifiers.isEmpty()) {
                Hephaestus.INSTANCE.getLogger().warning("No level modifiers found for level " + itemLevel);
                continue;
            }
            double value = HRandom.selectWeightedCurveValue(levelModifiers);
            var key = entry.getKey().unwrapKey().orElse(null);
            if (key == null) {
                Hephaestus.INSTANCE.getLogger().warning("Attribute key unwrap returned null");
                continue;
            }
            String keyString = key.identifier().toString();
            Hephaestus.INSTANCE.getLogger().info("Rolling attribute " + keyString + " = " + value);
            valueTag.putDouble(keyString, value);
        }
        Hephaestus.INSTANCE.getLogger().info("Final valueTag size: " + valueTag.size());
        return new HRolledUpgrade(stack, this, valueTag);
    }

    @Override
    public YamlConfiguration load(File file) {
        YamlConfiguration config = super.load(file);
        Hephaestus.INSTANCE.getLogger().info("Loading upgrade from file: " + file.getName());
        if (!config.contains("attributes")) {
            Hephaestus.INSTANCE.getLogger().warning("No 'attributes' section found in " + file.getName());
            return config;
        }
        ConfigurationSection attributesRoot = config.getConfigurationSection("attributes");
        if (attributesRoot == null) {
            Hephaestus.INSTANCE.getLogger().warning("'attributes' section is null in " + file.getName());
            return config;
        }
        Hephaestus.INSTANCE.getLogger().info("Found attributes section with keys: " + attributesRoot.getKeys(false));
        for (String attrKey : attributesRoot.getKeys(false)) {
            Hephaestus.INSTANCE.getLogger().info("Processing attribute key: " + attrKey);
            org.bukkit.attribute.Attribute bukkitAttribute;
            try {
                bukkitAttribute = Registry.ATTRIBUTE.get(new NamespacedKey("minecraft", attrKey.toLowerCase()));
            } catch (IllegalArgumentException e) {
                Hephaestus.INSTANCE.getLogger().warning("Invalid attribute key: " + attrKey + " in " + file.getName());
                continue;
            }
            if (bukkitAttribute == null) {
                Hephaestus.INSTANCE.getLogger().warning("Null attribute key: " + attrKey + " in " + file.getName());
                continue;
            }
            Hephaestus.INSTANCE.getLogger().info("Successfully resolved attribute: " + bukkitAttribute.getKey());
            Holder<Attribute> attribute = CraftAttribute.bukkitToMinecraftHolder(bukkitAttribute);
            Map<Integer, Map<double[], Integer>> levelModifiers = new HashMap<>();
            ConfigurationSection perAttribute = attributesRoot.getConfigurationSection(attrKey);
            if (perAttribute == null) {
                Hephaestus.INSTANCE.getLogger().warning("No config section for attribute: " + attrKey);
                continue;
            }
            Hephaestus.INSTANCE.getLogger().info("Found level keys for " + attrKey + ": " + perAttribute.getKeys(false));
            for (String levelKey : perAttribute.getKeys(false)) {
                int level;
                try { level = Integer.parseInt(levelKey); } catch (NumberFormatException ex) {
                    Hephaestus.INSTANCE.getLogger().warning("Invalid level key: " + levelKey);
                    continue;
                }

                // Check if it's a list-based config (with min/max/weight entries)
                List<Map<?, ?>> rangeList = perAttribute.getMapList(levelKey);
                if (!rangeList.isEmpty()) {
                    Map<double[], Integer> rangeWeights = new HashMap<>();
                    for (Map<?, ?> rangeMap : rangeList) {
                        Object minObj = rangeMap.get("min");
                        Object maxObj = rangeMap.get("max");
                        Object weightObj = rangeMap.get("weight");

                        if (minObj instanceof Number && maxObj instanceof Number && weightObj instanceof Number) {
                            double min = ((Number) minObj).doubleValue();
                            double max = ((Number) maxObj).doubleValue();
                            int weight = ((Number) weightObj).intValue();

                            if (weight > 0 && max >= min) {
                                rangeWeights.put(new double[]{min, max}, weight);
                                Hephaestus.INSTANCE.getLogger().info("Added range: [" + min + ", " + max + "] = " + weight);
                            }
                        }
                    }
                    if (!rangeWeights.isEmpty()) {
                        levelModifiers.put(level, rangeWeights);
                        Hephaestus.INSTANCE.getLogger().info("Added " + rangeWeights.size() + " ranges for level " + level);
                    }
                    continue;
                }

                // Fall back to old discrete value format for backwards compatibility
                ConfigurationSection valueSection = perAttribute.getConfigurationSection(levelKey);
                if (valueSection == null) {
                    Hephaestus.INSTANCE.getLogger().warning("No config section for level: " + levelKey);
                    continue;
                }
                Hephaestus.INSTANCE.getLogger().info("Found value keys for level " + level + ": " + valueSection.getKeys(false));
                Map<double[], Integer> weights = new HashMap<>();
                for (String valueKey : valueSection.getKeys(false)) {
                    try {
                        double val = Double.parseDouble(valueKey);
                        int weight = valueSection.getInt(valueKey, 0);
                        if (weight > 0) {
                            // Convert discrete value to a tiny range for backwards compatibility
                            weights.put(new double[]{val, val}, weight);
                            Hephaestus.INSTANCE.getLogger().info("Added discrete value as range: [" + val + ", " + val + "] = " + weight);
                        }
                    } catch (NumberFormatException ignored) {
                        Hephaestus.INSTANCE.getLogger().warning("Invalid value key: " + valueKey);
                    }
                }
                if (!weights.isEmpty()) {
                    levelModifiers.put(level, weights);
                    Hephaestus.INSTANCE.getLogger().info("Added " + weights.size() + " ranges for level " + level);
                }
            }
            if (!levelModifiers.isEmpty()) {
                attributeModifiers.put(attribute, levelModifiers);
                Hephaestus.INSTANCE.getLogger().info("Successfully loaded attribute " + attrKey + " with " + levelModifiers.size() + " levels");
            }
        }
        Hephaestus.INSTANCE.getLogger().info("Finished loading. Total attributes loaded: " + attributeModifiers.size());
        return config;
    }

}
