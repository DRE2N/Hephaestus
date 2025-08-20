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

    private final Map<Holder<Attribute>, Map<Integer, Map<Double, Integer>>> attributeModifiers = new HashMap<>();

    public Map<Holder<Attribute>, Map<Integer, Map<Double, Integer>>> getAttributeDefinition() {
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
        var valueTag = new net.minecraft.nbt.CompoundTag();
        for (Map.Entry<Holder<Attribute>, Map<Integer, Map<Double, Integer>>> entry : attributeModifiers.entrySet()) {
            Map<Integer, Map<Double, Integer>> perLevel = entry.getValue();
            Map<Double, Integer> levelModifiers = perLevel.get(itemLevel);
            if (levelModifiers == null) {
                // find nearest lower level definition
                int best = Integer.MIN_VALUE;
                for (Integer defined : perLevel.keySet()) {
                    if (defined <= itemLevel && defined > best) best = defined;
                }
                if (best != Integer.MIN_VALUE) {
                    levelModifiers = perLevel.get(best);
                }
            }
            if (levelModifiers == null || levelModifiers.isEmpty()) continue;
            double value = HRandom.selectWeightedRandomValue(levelModifiers);
            var key = entry.getKey().unwrapKey().orElse(null);
            if (key == null) continue;
            valueTag.putDouble(key.location().toString(), value);
        }
        return new HRolledUpgrade(stack, this, valueTag);
    }

    @Override
    public YamlConfiguration load(File file) {
        YamlConfiguration config = super.load(file);
        if (!config.contains("attributes")) {
            return config;
        }
        ConfigurationSection attributesRoot = config.getConfigurationSection("attributes");
        if (attributesRoot == null) return config;
        for (String attrKey : attributesRoot.getKeys(false)) {
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
            Holder<Attribute> attribute = CraftAttribute.bukkitToMinecraftHolder(bukkitAttribute);
            Map<Integer, Map<Double, Integer>> levelModifiers = new HashMap<>();
            ConfigurationSection perAttribute = attributesRoot.getConfigurationSection(attrKey);
            if (perAttribute == null) continue;
            for (String levelKey : perAttribute.getKeys(false)) {
                int level;
                try { level = Integer.parseInt(levelKey); } catch (NumberFormatException ex) { continue; }
                ConfigurationSection valueSection = perAttribute.getConfigurationSection(levelKey);
                if (valueSection == null) continue;
                Map<Double, Integer> weights = new HashMap<>();
                for (String valueKey : valueSection.getKeys(false)) {
                    try {
                        double val = Double.parseDouble(valueKey);
                        int weight = valueSection.getInt(valueKey, 0);
                        if (weight > 0) weights.put(val, weight);
                    } catch (NumberFormatException ignored) {}
                }
                if (!weights.isEmpty()) levelModifiers.put(level, weights);
            }
            if (!levelModifiers.isEmpty()) attributeModifiers.put(attribute, levelModifiers);
        }
        return config;
    }

}
