package de.erethon.hephaestus.jobs.crafting;

import de.erethon.hephaestus.items.HRarity;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Modifies the recipe result based on the tier of materials used.
 * This allows a single recipe pattern to produce different quality results.
 */
public class ResultModifier {

    private final String baseItemId; // The base item ID template, e.g., "hephaestus:heavy_helmet"
    private final int baseAmount;
    private final Map<Integer, TierModifier> tierModifiers; // Tier -> modifier mapping

    public ResultModifier(String baseItemId, int baseAmount, Map<Integer, TierModifier> tierModifiers) {
        this.baseItemId = baseItemId;
        this.baseAmount = baseAmount;
        this.tierModifiers = new HashMap<>(tierModifiers);
    }

    public String getBaseItemId() {
        return baseItemId;
    }

    public int getBaseAmount() {
        return baseAmount;
    }

    /**
     * Calculate the final result based on the tier used
     * @param tier the tier of materials used
     * @return the modified recipe result
     */
    public RecipeResult calculateResult(int tier) {
        TierModifier modifier = tierModifiers.getOrDefault(tier, new TierModifier(0, HRarity.COMMON, null));

        return new RecipeResult(
                baseItemId,
                baseAmount,
                modifier.getItemLevel(),
                modifier.getRarity(),
                modifier.getSocketPattern()
        );
    }

    public void serialize(ConfigurationSection section) {
        section.set("baseItemId", baseItemId);
        section.set("baseAmount", baseAmount);

        ConfigurationSection modifiersSection = section.createSection("tierModifiers");
        for (Map.Entry<Integer, TierModifier> entry : tierModifiers.entrySet()) {
            ConfigurationSection modifierSection = modifiersSection.createSection(String.valueOf(entry.getKey()));
            entry.getValue().serialize(modifierSection);
        }
    }

    public static ResultModifier deserialize(ConfigurationSection section) {
        String baseItemId = section.getString("baseItemId");
        int baseAmount = section.getInt("baseAmount", 1);
        Map<Integer, TierModifier> tierModifiers = new HashMap<>();

        ConfigurationSection modifiersSection = section.getConfigurationSection("tierModifiers");
        if (modifiersSection != null) {
            for (String key : modifiersSection.getKeys(false)) {
                ConfigurationSection modifierSection = modifiersSection.getConfigurationSection(key);
                if (modifierSection != null) {
                    tierModifiers.put(Integer.parseInt(key), TierModifier.deserialize(modifierSection));
                }
            }
        }

        return new ResultModifier(baseItemId, baseAmount, tierModifiers);
    }

    /**
     * Modifier values for a specific tier
     */
    public static class TierModifier {
        private final int itemLevel;
        private final HRarity rarity;
        private final String socketPattern;

        public TierModifier(int itemLevel, HRarity rarity, String socketPattern) {
            this.itemLevel = itemLevel;
            this.rarity = rarity;
            this.socketPattern = socketPattern;
        }

        public int getItemLevel() {
            return itemLevel;
        }

        public HRarity getRarity() {
            return rarity;
        }

        public String getSocketPattern() {
            return socketPattern;
        }

        public void serialize(ConfigurationSection section) {
            section.set("itemLevel", itemLevel);
            section.set("rarity", rarity.name());
            if (socketPattern != null) {
                section.set("socketPattern", socketPattern);
            }
        }

        public static TierModifier deserialize(ConfigurationSection section) {
            return new TierModifier(
                    section.getInt("itemLevel"),
                    HRarity.valueOf(section.getString("rarity", "COMMON")),
                    section.getString("socketPattern", null)
            );
        }
    }
}

