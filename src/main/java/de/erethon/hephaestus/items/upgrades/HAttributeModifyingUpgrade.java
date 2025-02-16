package de.erethon.hephaestus.items.upgrades;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.utils.HRandom;
import net.kyori.adventure.text.Component;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.attribute.CraftAttribute;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HAttributeModifyingUpgrade extends HItemUpgrade {

    private final Map<Holder<Attribute>, Map<Integer, Map<Double, Integer>>> attributeModifiers = new HashMap<>();

    public HAttributeModifyingUpgrade() {
    }

    @Override
    public HRolledUpgrade roll(HItemStack stack) {
        int itemLevel = stack.getItemLevel();
        CompoundTag valueTag = new CompoundTag();
        for (Map.Entry<Holder<Attribute>, Map<Integer, Map<Double, Integer>>> entry : attributeModifiers.entrySet()) {
            Map<Double, Integer> levelModifiers = entry.getValue().get(itemLevel);
            if (levelModifiers != null) {
                double value = HRandom.selectWeightedRandomValue(levelModifiers);
                AttributeModifier modifier = new AttributeModifier(ResourceLocation.parse("hephaestus:" + id), value, AttributeModifier.Operation.ADD_VALUE);
                ItemAttributeModifiers.builder().add(entry.getKey(), modifier, EquipmentSlotGroup.ANY).build();
                valueTag.putDouble(id, value);
            }
        }
        return new HRolledUpgrade(stack, this, valueTag);
    }

    @Override
    public YamlConfiguration load(File file) {
        YamlConfiguration config = super.load(file);
        if (!config.contains("attributes")) {
            return config;
        }
        for (String key : config.getConfigurationSection("attributes").getKeys(false)) {
            org.bukkit.attribute.Attribute bukkitAttribute;
            try {
                bukkitAttribute = Registry.ATTRIBUTE.get(new NamespacedKey("minecraft", key.toLowerCase()));
            }
            catch (IllegalArgumentException e) {
                Hephaestus.INSTANCE.getLogger().warning("Invalid attribute key: " + key + " in " + file.getName());
                continue;
            }
            Holder<Attribute> attribute = CraftAttribute.bukkitToMinecraftHolder(bukkitAttribute);
            Map<Integer, Map<Double, Integer>> levelModifiers = new HashMap<>();
            var attributeSection = config.getConfigurationSection("attributes." + key);
            for (String levelKey : attributeSection.getKeys(false)) {
                int level = Integer.parseInt(levelKey);
                levelModifiers.put(level, HRandom.loadWeights(config, "attributes." + key + "." + levelKey));
            }
            attributeModifiers.put(attribute, levelModifiers);
        }
        return config;
    }

}
