package de.erethon.hephaestus.jobs.crafting;

import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.items.HRarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobRecipe {

    private final String id;
    private final String jobId;
    private final int requiredLevel;
    private final List<RecipeIngredient> ingredients;
    private final RecipeResult result;
    private final long baseExperience;
    private final int craftingTime; // in ticks
    private final HRarity minRarity;
    private final boolean discoverable;

    public JobRecipe(String id, String jobId, int requiredLevel, List<RecipeIngredient> ingredients,
                     RecipeResult result, long baseExperience, int craftingTime, HRarity minRarity, boolean discoverable) {
        this.id = id;
        this.jobId = jobId;
        this.requiredLevel = requiredLevel;
        this.ingredients = new ArrayList<>(ingredients);
        this.result = result;
        this.baseExperience = baseExperience;
        this.craftingTime = craftingTime;
        this.minRarity = minRarity;
        this.discoverable = discoverable;
    }

    public String getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public List<RecipeIngredient> getIngredients() {
        return new ArrayList<>(ingredients);
    }

    public RecipeResult getResult() {
        return result;
    }

    public long getBaseExperience() {
        return baseExperience;
    }

    public int getCraftingTime() {
        return craftingTime;
    }

    public HRarity getMinRarity() {
        return minRarity;
    }

    public boolean isDiscoverable() {
        return discoverable;
    }

    /**
     * Calculate experience based on how many times this recipe has been crafted
     * @param timesCrafted number of times the player has crafted this recipe
     * @return experience amount with diminishing returns
     */
    public long calculateExperience(int timesCrafted) {
        if (timesCrafted == 0) {
            return baseExperience; // First craft gives full XP
        }

        // Diminishing returns formula: baseXP * (0.8^timesCrafted) with minimum of 10% base XP
        double multiplier = Math.pow(0.8, timesCrafted);
        multiplier = Math.max(multiplier, 0.05); // Minimum 5% of base XP

        return Math.round(baseExperience * multiplier);
    }

    /**
     * Check if the provided ingredients match this recipe
     * @param providedIngredients list of item stacks to check
     * @return true if ingredients match the recipe requirements
     */
    public boolean matchesIngredients(List<ItemStack> providedIngredients) {
        Map<String, Integer> requiredCounts = new HashMap<>();
        for (RecipeIngredient ingredient : ingredients) {
            requiredCounts.put(ingredient.getItemId(), ingredient.getAmount());
        }

        Map<String, Integer> providedCounts = new HashMap<>();
        for (ItemStack stack : providedIngredients) {
            if (stack == null || stack.getType().isAir()) continue;

            HItemStack hStack = HItemStack.getFromStack(stack);
            if (hStack != null) {
                String itemId = hStack.getItem().getKey().toString();
                providedCounts.put(itemId, providedCounts.getOrDefault(itemId, 0) + stack.getAmount());
            }
        }

        return requiredCounts.equals(providedCounts);
    }

    public void serialize(ConfigurationSection section) {
        section.set("id", id);
        section.set("jobId", jobId);
        section.set("requiredLevel", requiredLevel);
        section.set("baseExperience", baseExperience);
        section.set("craftingTime", craftingTime);
        section.set("minRarity", minRarity.name());
        section.set("discoverable", discoverable);

        ConfigurationSection ingredientsSection = section.createSection("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            ConfigurationSection ingredientSection = ingredientsSection.createSection(String.valueOf(i));
            ingredients.get(i).serialize(ingredientSection);
        }

        ConfigurationSection resultSection = section.createSection("result");
        result.serialize(resultSection);
    }

    public static JobRecipe deserialize(ConfigurationSection section) {
        String id = section.getString("id");
        String jobId = section.getString("jobId");
        int requiredLevel = section.getInt("requiredLevel");
        long baseExperience = section.getLong("baseExperience");
        int craftingTime = section.getInt("craftingTime");
        HRarity minRarity = HRarity.valueOf(section.getString("minRarity", "COMMON"));
        boolean discoverable = section.getBoolean("discoverable", true);

        List<RecipeIngredient> ingredients = new ArrayList<>();
        ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
        if (ingredientsSection != null) {
            for (String key : ingredientsSection.getKeys(false)) {
                ConfigurationSection ingredientSection = ingredientsSection.getConfigurationSection(key);
                if (ingredientSection != null) {
                    ingredients.add(RecipeIngredient.deserialize(ingredientSection));
                }
            }
        }

        RecipeResult result = null;
        ConfigurationSection resultSection = section.getConfigurationSection("result");
        if (resultSection != null) {
            result = RecipeResult.deserialize(resultSection);
        }

        return new JobRecipe(id, jobId, requiredLevel, ingredients, result, baseExperience, craftingTime, minRarity, discoverable);
    }
}
