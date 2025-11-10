package de.erethon.hephaestus.jobs.crafting;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a choice of ingredients for a recipe slot.
 * This allows the same recipe pattern to use different tier materials.
 */
public class IngredientChoice {

    private final String choiceId; // e.g., "tier_metal", "tier_leather"
    private final List<IngredientOption> options;

    public IngredientChoice(String choiceId, List<IngredientOption> options) {
        this.choiceId = choiceId;
        this.options = new ArrayList<>(options);
    }

    public String getChoiceId() {
        return choiceId;
    }

    public List<IngredientOption> getOptions() {
        return new ArrayList<>(options);
    }

    /**
     * Find which option matches the given item ID
     * @param itemId the item ID to check
     * @return the matching option, or null if not found
     */
    public IngredientOption findMatchingOption(String itemId) {
        for (IngredientOption option : options) {
            if (option.getItemId().equals(itemId)) {
                return option;
            }
        }
        return null;
    }

    /**
     * Check if this choice contains the given item ID
     * @param itemId the item ID to check
     * @return true if any option matches
     */
    public boolean containsItem(String itemId) {
        de.erethon.hephaestus.Hephaestus.log("              Choice '" + choiceId + "' has " + options.size() + " options");
        for (IngredientOption option : options) {
            de.erethon.hephaestus.Hephaestus.log("                Option: " + option.getItemId() + " (tier " + option.getTier() + ")");
            if (option.getItemId().equals(itemId)) {
                de.erethon.hephaestus.Hephaestus.log("                  -> MATCH!");
                return true;
            }
        }
        return findMatchingOption(itemId) != null;
    }

    public void serialize(ConfigurationSection section) {
        section.set("choiceId", choiceId);

        ConfigurationSection optionsSection = section.createSection("options");
        for (int i = 0; i < options.size(); i++) {
            ConfigurationSection optionSection = optionsSection.createSection(String.valueOf(i));
            options.get(i).serialize(optionSection);
        }
    }

    public static IngredientChoice deserialize(ConfigurationSection section) {
        String choiceId = section.getString("choiceId");
        List<IngredientOption> options = new ArrayList<>();

        ConfigurationSection optionsSection = section.getConfigurationSection("options");
        if (optionsSection != null) {
            for (String key : optionsSection.getKeys(false)) {
                ConfigurationSection optionSection = optionsSection.getConfigurationSection(key);
                if (optionSection != null) {
                    options.add(IngredientOption.deserialize(optionSection));
                }
            }
        }

        return new IngredientChoice(choiceId, options);
    }

    /**
     * Represents a single option within a choice
     */
    public static class IngredientOption {
        private final String itemId;
        private final int tier; // The tier level (1-6 for levels 1-10, 11-20, etc.)
        private final int minLevel; // Minimum job level to use this option

        public IngredientOption(String itemId, int tier, int minLevel) {
            this.itemId = itemId;
            this.tier = tier;
            this.minLevel = minLevel;
        }

        public String getItemId() {
            return itemId;
        }

        public int getTier() {
            return tier;
        }

        public int getMinLevel() {
            return minLevel;
        }

        public void serialize(ConfigurationSection section) {
            section.set("itemId", itemId);
            section.set("tier", tier);
            section.set("minLevel", minLevel);
        }

        public static IngredientOption deserialize(ConfigurationSection section) {
            return new IngredientOption(
                    section.getString("itemId"),
                    section.getInt("tier"),
                    section.getInt("minLevel", 0)
            );
        }
    }
}

