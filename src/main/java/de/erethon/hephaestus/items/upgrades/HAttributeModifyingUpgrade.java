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

    private final Map<Attribute, Map<Integer, Integer>> attributeModifiers = new HashMap<>();

    public HAttributeModifyingUpgrade() {
    }

    @Override
    public void roll(HItemStack stack) {
        super.roll(stack);
        for (Map.Entry<Attribute, Map<Integer, Integer>> entry : attributeModifiers.entrySet()) {
            stack.getStack().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers()
                    .add(new ItemAttributeModifiers.Entry((Holder<Attribute>) entry.getKey(), new AttributeModifier(ResourceLocation.parse("hephaestus:" + id),
                            HRandom.selectWeightedRandomValue(entry.getValue()), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY));
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
            attributeModifiers.put(attribute, HRandom.loadWeights(config, "attributes." + key));
        }
        return config;
    }

    @Override
    public CompoundTag toNBT() {
        return super.toNBT();
    }
}
