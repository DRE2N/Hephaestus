package de.erethon.hephaestus.jobs.crafting;

import org.bukkit.configuration.ConfigurationSection;

public class RecipeIngredient {

    private final String itemId;
    private final int amount;
    private final int minLevel;
    private final boolean consumeOnCraft;

    public RecipeIngredient(String itemId, int amount, int minLevel, boolean consumeOnCraft) {
        this.itemId = itemId;
        this.amount = amount;
        this.minLevel = minLevel;
        this.consumeOnCraft = consumeOnCraft;
    }

    public RecipeIngredient(String itemId, int amount) {
        this(itemId, amount, 0, true);
    }

    public String getItemId() {
        return itemId;
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

    public void serialize(ConfigurationSection section) {
        section.set("itemId", itemId);
        section.set("amount", amount);
        section.set("minLevel", minLevel);
        section.set("consumeOnCraft", consumeOnCraft);
    }

    public static RecipeIngredient deserialize(ConfigurationSection section) {
        return new RecipeIngredient(
                section.getString("itemId"),
                section.getInt("amount"),
                section.getInt("minLevel", 0),
                section.getBoolean("consumeOnCraft", true)
        );
    }
}
