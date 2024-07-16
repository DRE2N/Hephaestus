package de.erethon.hephaestus.items.upgrades;

import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.utils.HRandom;
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
import java.util.Map;

public class HAttributeModifyingUpgrade extends HItemUpgrade {

    private final Map<Attribute, Map<Integer, Map<Double, Integer>>> attributeModifiers = new HashMap<>();

    public HAttributeModifyingUpgrade() {
    }

    @Override
    public void roll(HItemStack stack) {
        super.roll(stack);
        int itemLevel = stack.getItemLevel();
        for (Map.Entry<Attribute, Map<Integer, Map<Double, Integer>>> entry : attributeModifiers.entrySet()) {
            Map<Double, Integer> levelModifiers = entry.getValue().get(itemLevel);
            if (levelModifiers != null) {
                stack.getVanillaStack().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers()
                        .add(new ItemAttributeModifiers.Entry((Holder<Attribute>) entry.getKey(), new AttributeModifier(ResourceLocation.parse("hephaestus:" + id),
                                HRandom.selectWeightedRandomValue(levelModifiers), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY));
            }
        }
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
    public CompoundTag toNBT() {
        return super.toNBT();
    }
}
