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

        // Use tier-specific itemId and amount if provided, otherwise use base values
        String resultItemId = modifier.getItemId() != null ? modifier.getItemId() : baseItemId;
        int resultAmount = modifier.getAmount() > 0 ? modifier.getAmount() : baseAmount;

        return new RecipeResult(
                resultItemId,
                resultAmount,
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
        private final String itemId; // Optional: if null, uses baseItemId
        private final int amount; // Optional: if 0, uses baseAmount
        private final int itemLevel;
        private final HRarity rarity;
        private final String socketPattern;

        public TierModifier(String itemId, int amount, int itemLevel, HRarity rarity, String socketPattern) {
            this.itemId = itemId;
            this.amount = amount;
            this.itemLevel = itemLevel;
            this.rarity = rarity;
            this.socketPattern = socketPattern;
        }

        public TierModifier(int itemLevel, HRarity rarity, String socketPattern) {
            this(null, 0, itemLevel, rarity, socketPattern);
        }

        public String getItemId() {
            return itemId;
        }

        public int getAmount() {
            return amount;
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
            if (itemId != null) {
                section.set("itemId", itemId);
            }
            if (amount > 0) {
                section.set("amount", amount);
            }
            section.set("itemLevel", itemLevel);
            section.set("rarity", rarity.name());
            if (socketPattern != null) {
                section.set("socketPattern", socketPattern);
            }
        }

        public static TierModifier deserialize(ConfigurationSection section) {
            return new TierModifier(
                    section.getString("itemId", null),
                    section.getInt("amount", 0),
                    section.getInt("itemLevel"),
                    HRarity.valueOf(section.getString("rarity", "COMMON")),
                    section.getString("socketPattern", null)
            );
        }
    }
}

