package de.erethon.hephaestus.items.upgrades;

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
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HAttributeModifyingUpgrade extends HItemUpgrade {

    private final Map<Attribute, Map<Integer, Map<Double, Integer>>> attributeModifiers = new HashMap<>();

    public HAttributeModifyingUpgrade() {
    }

    @Override
    public HRolledUpgrade roll(HItemStack stack) {
        super.roll(stack);
        int itemLevel = stack.getItemLevel();
        CompoundTag valueTag = new CompoundTag();
        for (Map.Entry<Attribute, Map<Integer, Map<Double, Integer>>> entry : attributeModifiers.entrySet()) {
            Map<Double, Integer> levelModifiers = entry.getValue().get(itemLevel);
            if (levelModifiers != null) {
                double value = HRandom.selectWeightedRandomValue(levelModifiers);
                stack.getVanillaStack().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers()
                        .add(new ItemAttributeModifiers.Entry((Holder<Attribute>) entry.getKey(), new AttributeModifier(ResourceLocation.parse("hephaestus:" + id),
                                value, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY));
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
            Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(ResourceLocation.parse("minecraft:" + key));
            if (attribute == null) {
                continue;
            }
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

    @Override
    public List<Component> getLore() {
        return null;
    }
}
