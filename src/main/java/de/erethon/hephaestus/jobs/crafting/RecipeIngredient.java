package de.erethon.hephaestus.jobs.crafting;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public class RecipeIngredient {

    private final String itemId; // Used if this is a fixed ingredient
    private final IngredientChoice choice; // Used if this is a choice ingredient
    private final int amount;
    private final int minLevel;
    private final boolean consumeOnCraft;

    // Constructor for fixed ingredient
    public RecipeIngredient(String itemId, int amount, int minLevel, boolean consumeOnCraft) {
        this.itemId = itemId;
        this.choice = null;
        this.amount = amount;
        this.minLevel = minLevel;
        this.consumeOnCraft = consumeOnCraft;
    }

    // Constructor for choice ingredient
    public RecipeIngredient(IngredientChoice choice, int amount, int minLevel, boolean consumeOnCraft) {
        this.itemId = null;
        this.choice = choice;
        this.amount = amount;
        this.minLevel = minLevel;
        this.consumeOnCraft = consumeOnCraft;
    }

    public RecipeIngredient(String itemId, int amount) {
        this(itemId, amount, 0, true);
    }

    public RecipeIngredient(IngredientChoice choice, int amount) {
        this(choice, amount, 0, true);
    }

    public boolean isChoice() {
        return choice != null;
    }

    public String getItemId() {
        return itemId;
    }

    public IngredientChoice getChoice() {
        return choice;
    }

    public int getAmount() {
        return amount;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public boolean isConsumeOnCraft() {
        return consumeOnCraft;
    }

    /**
     * Check if this ingredient matches the given item ID
     * @param itemId the item ID to check
     * @return true if matches (either fixed or choice)
     */
    public boolean matches(String itemId) {
        if (isChoice()) {
            return choice.containsItem(itemId);
        }
        return this.itemId != null && this.itemId.equals(itemId);
    }

    public boolean matches(ItemStack stack) {
        return matches(de.erethon.hephaestus.items.HItemUtil.getItemId(stack));
    }

    /**
     * Get the tier of the matched item if this is a choice ingredient
     * @param itemId the item ID to check
     * @return the tier, or 0 if not a choice or not found
     */
    public int getTierForItem(String itemId) {
        if (!isChoice()) {
            return 0;
        }
        IngredientChoice.IngredientOption option = choice.findMatchingOption(itemId);
        return option != null ? option.getTier() : 0;
    }

    public void serialize(ConfigurationSection section) {
        if (isChoice()) {
            section.set("type", "choice");
            ConfigurationSection choiceSection = section.createSection("choice");
            choice.serialize(choiceSection);
        } else {
            section.set("type", "fixed");
            section.set("itemId", itemId);
        }
        section.set("amount", amount);
        section.set("minLevel", minLevel);
        section.set("consumeOnCraft", consumeOnCraft);
    }

    public static RecipeIngredient deserialize(ConfigurationSection section) {
        String type = section.getString("type", "fixed");
        int amount = section.getInt("amount");
        int minLevel = section.getInt("minLevel", 0);
        boolean consumeOnCraft = section.getBoolean("consumeOnCraft", true);

        if ("choice".equals(type)) {
            ConfigurationSection choiceSection = section.getConfigurationSection("choice");
            if (choiceSection != null) {
                IngredientChoice choice = IngredientChoice.deserialize(choiceSection);
                return new RecipeIngredient(choice, amount, minLevel, consumeOnCraft);
            }
        }

        // Default to fixed ingredient
        String itemId = section.getString("itemId");
        return new RecipeIngredient(itemId, amount, minLevel, consumeOnCraft);
    }
}
