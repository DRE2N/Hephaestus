package de.erethon.hephaestus.items.interactions;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class ItemInteractionSettings {

    private final SmeltingSettings smelting;
    private final RepairSettings repair;

    public ItemInteractionSettings(SmeltingSettings smelting, RepairSettings repair) {
        this.smelting = smelting;
        this.repair = repair;
    }

    public SmeltingSettings getSmelting() {
        return smelting;
    }

    public RepairSettings getRepair() {
        return repair;
    }

    public boolean hasSmelting() {
        return smelting != null && smelting.hasAnyFurnace();
    }

    public boolean hasRepair() {
        return repair != null && !repair.materials().isEmpty();
    }

    public static ItemInteractionSettings deserialize(ConfigurationSection section) {
        if (section == null) {
            return new ItemInteractionSettings(null, null);
        }
        SmeltingSettings smelting = null;
        ConfigurationSection smeltingSection = section.getConfigurationSection("smelting");
        if (smeltingSection != null) {
            smelting = SmeltingSettings.deserialize(smeltingSection);
        }
        RepairSettings repair = null;
        ConfigurationSection repairSection = section.getConfigurationSection("repair");
        if (repairSection != null) {
            repair = RepairSettings.deserialize(repairSection);
        }
        return new ItemInteractionSettings(smelting, repair);
    }

    public record SmeltingSettings(boolean furnace, boolean blastFurnace, boolean smoker, String result,
                                   int amount, float experience, int cookingTime) {
        public boolean hasAnyFurnace() {
            return furnace || blastFurnace || smoker;
        }

        public static SmeltingSettings deserialize(ConfigurationSection section) {
            return new SmeltingSettings(
                    section.getBoolean("furnace", true),
                    section.getBoolean("blast_furnace", section.getBoolean("blastFurnace", false)),
                    section.getBoolean("smoker", false),
                    section.getString("result"),
                    Math.max(1, section.getInt("amount", 1)),
                    (float) section.getDouble("experience", 0.0),
                    Math.max(1, section.getInt("cookingTime", 200))
            );
        }
    }

    public record RepairSettings(List<String> materials, double durabilityPerItem, int cost) {
        public static RepairSettings deserialize(ConfigurationSection section) {
            List<String> materials = new ArrayList<>(section.getStringList("materials"));
            String singleMaterial = section.getString("material");
            if (singleMaterial != null && !singleMaterial.isBlank()) {
                materials.add(singleMaterial);
            }
            return new RepairSettings(
                    materials,
                    Math.max(0.0, section.getDouble("durabilityPerItem", 0.25)),
                    Math.max(0, section.getInt("cost", 1))
            );
        }
    }
}
