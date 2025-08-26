package de.erethon.hephaestus.jobs.crafting;

import de.erethon.hephaestus.items.HRarity;
import org.bukkit.configuration.ConfigurationSection;

public class RecipeResult {

    private final String itemId;
    private final int amount;
    private final int itemLevel;
    private final HRarity rarity;
    private final String socketPattern; // Optional socket pattern for equipment

    public RecipeResult(String itemId, int amount, int itemLevel, HRarity rarity, String socketPattern) {
        this.itemId = itemId;
        this.amount = amount;
        this.itemLevel = itemLevel;
        this.rarity = rarity;
        this.socketPattern = socketPattern;
    }

    public RecipeResult(String itemId, int amount) {
        this(itemId, amount, 0, HRarity.COMMON, null);
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
        section.set("itemId", itemId);
        section.set("amount", amount);
        section.set("itemLevel", itemLevel);
        section.set("rarity", rarity.name());
        if (socketPattern != null) {
            section.set("socketPattern", socketPattern);
        }
    }

    public static RecipeResult deserialize(ConfigurationSection section) {
        return new RecipeResult(
                section.getString("itemId"),
                section.getInt("amount"),
                section.getInt("itemLevel", 0),
                HRarity.valueOf(section.getString("rarity", "COMMON")),
                section.getString("socketPattern")
        );
    }
}
