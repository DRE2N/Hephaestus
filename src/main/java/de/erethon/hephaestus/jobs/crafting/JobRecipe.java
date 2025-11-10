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
import java.util.Set;
import java.util.HashSet;

public class JobRecipe {

    private final String id;
    private final String jobId;
    private final int requiredLevel;
    private final List<RecipeIngredient> ingredients;
    private final RecipeResult result; // Fixed result, if not using modifier
    private final ResultModifier resultModifier; // Dynamic result based on tier
    private final long baseExperience;
    private final int craftingTime; // in ticks
    private final HRarity minRarity;
    private final boolean discoverable;

    // Constructor with fixed result
    public JobRecipe(String id, String jobId, int requiredLevel, List<RecipeIngredient> ingredients,
                     RecipeResult result, long baseExperience, int craftingTime, HRarity minRarity, boolean discoverable) {
        this.id = id;
        this.jobId = jobId;
        this.requiredLevel = requiredLevel;
        this.ingredients = new ArrayList<>(ingredients);
        this.result = result;
        this.resultModifier = null;
        this.baseExperience = baseExperience;
        this.craftingTime = craftingTime;
        this.minRarity = minRarity;
        this.discoverable = discoverable;
    }

    // Constructor with dynamic result modifier
    public JobRecipe(String id, String jobId, int requiredLevel, List<RecipeIngredient> ingredients,
                     ResultModifier resultModifier, long baseExperience, int craftingTime, HRarity minRarity, boolean discoverable) {
        this.id = id;
        this.jobId = jobId;
        this.requiredLevel = requiredLevel;
        this.ingredients = new ArrayList<>(ingredients);
        this.result = null;
        this.resultModifier = resultModifier;
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

    /**
     * Check if this recipe uses a dynamic result modifier
     * @return true if using result modifier
     */
    public boolean hasDynamicResult() {
        return resultModifier != null;
    }

    /**
     * Get the fixed result (if not using dynamic results)
     * @return the recipe result, or null if using dynamic results
     */
    public RecipeResult getResult() {
        return result;
    }

    /**
     * Calculate the result based on the provided ingredients
     * @param providedIngredients the ingredients being used in the craft
     * @return the calculated result
     */
    public RecipeResult calculateResult(List<ItemStack> providedIngredients) {
        if (!hasDynamicResult()) {
            return result; // Return fixed result
        }

        // Determine the tier from choice ingredients
        int determinedTier = 0;
        for (RecipeIngredient ingredient : ingredients) {
            if (ingredient.isChoice()) {
                for (ItemStack stack : providedIngredients) {
                    if (stack == null || stack.getType().isAir()) continue;

                    HItemStack hStack = HItemStack.getFromStack(stack);
                    if (hStack != null) {
                        String itemId = hStack.getItem().getKey().toString();
                        if (ingredient.matches(itemId)) {
                            int tier = ingredient.getTierForItem(itemId);
                            determinedTier = Math.max(determinedTier, tier);
                        }
                    }
                }
            }
        }

        return resultModifier.calculateResult(determinedTier);
    }

    /**
     * Get a display result for GUI purposes (uses tier 0 for dynamic recipes)
     * @return the display result
     */
    public RecipeResult getDisplayResult() {
        if (!hasDynamicResult()) {
            return result; // Return fixed result
        }
        // For dynamic recipes, show the lowest tier (tier 0) result for display
        return resultModifier.calculateResult(0);
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
     * Discovery requires exact match of all recipe ingredients (ignoring amounts)
     * @param providedIngredients list of item stacks to check
     * @return true if the provided ingredients exactly match the recipe's ingredient types
     */
    public boolean matchesIngredientsForDiscovery(List<ItemStack> providedIngredients) {
        if (ingredients.isEmpty()) return false;

        de.erethon.hephaestus.Hephaestus.log("      Checking discovery match for recipe: " + id);
        de.erethon.hephaestus.Hephaestus.log("      Recipe has " + ingredients.size() + " ingredient(s)");

        // Get unique ingredient types from the recipe (ignoring amounts)
        Set<String> recipeIngredientTypes = new HashSet<>();
        for (RecipeIngredient recipeIngredient : ingredients) {
            if (recipeIngredient.isChoice()) {
                // For choices, we need to track that there's a choice requirement
                recipeIngredientTypes.add("choice:" + recipeIngredient.getChoice().getChoiceId());
            } else {
                recipeIngredientTypes.add(recipeIngredient.getItemId());
            }
        }

        // Get unique ingredient types from provided items
        Set<String> providedIngredientTypes = new HashSet<>();
        for (ItemStack provided : providedIngredients) {
            if (provided == null || provided.getType().isAir()) continue;

            HItemStack hStack = HItemStack.getFromStack(provided);
            if (hStack != null) {
                String itemId = hStack.getItem().getKey().toString();
                de.erethon.hephaestus.Hephaestus.log("          Provided item: " + itemId);

                // Check if this item matches any recipe ingredient (including choices)
                boolean matched = false;
                for (RecipeIngredient recipeIngredient : ingredients) {
                    if (recipeIngredient.matches(itemId)) {
                        if (recipeIngredient.isChoice()) {
                            providedIngredientTypes.add("choice:" + recipeIngredient.getChoice().getChoiceId());
                        } else {
                            providedIngredientTypes.add(recipeIngredient.getItemId());
                        }
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    de.erethon.hephaestus.Hephaestus.log("          -> Provided item doesn't match any recipe ingredient");
                    return false; // Provided an item that's not in the recipe
                }
            } else {
                de.erethon.hephaestus.Hephaestus.log("          Skipping vanilla item: " + provided.getType());
            }
        }

        // Check if the sets match exactly
        boolean exactMatch = recipeIngredientTypes.equals(providedIngredientTypes);
        de.erethon.hephaestus.Hephaestus.log("      Recipe types: " + recipeIngredientTypes);
        de.erethon.hephaestus.Hephaestus.log("      Provided types: " + providedIngredientTypes);
        de.erethon.hephaestus.Hephaestus.log("      Exact match: " + exactMatch);

        return exactMatch;
    }

    /**
     * Check if the provided ingredients match this recipe exactly for crafting
     * @param providedIngredients list of item stacks to check
     * @return true if ingredients match the recipe requirements exactly
     */
    public boolean matchesIngredients(List<ItemStack> providedIngredients) {
        // Count required amounts for each ingredient
        Map<RecipeIngredient, Integer> requiredCounts = new HashMap<>();
        for (RecipeIngredient ingredient : ingredients) {
            requiredCounts.put(ingredient, ingredient.getAmount());
        }

        // Track which ingredients we've matched
        Map<RecipeIngredient, Integer> matchedCounts = new HashMap<>();
        for (RecipeIngredient ingredient : ingredients) {
            matchedCounts.put(ingredient, 0);
        }

        // Count provided items
        for (ItemStack stack : providedIngredients) {
            if (stack == null || stack.getType().isAir()) continue;

            HItemStack hStack = HItemStack.getFromStack(stack);
            if (hStack != null) {
                String itemId = hStack.getItem().getKey().toString();

                // Try to match with each recipe ingredient
                for (RecipeIngredient ingredient : ingredients) {
                    if (ingredient.matches(itemId)) {
                        matchedCounts.put(ingredient, matchedCounts.get(ingredient) + stack.getAmount());
                        break; // Each stack can only match one ingredient
                    }
                }
            }
        }

        // Check if all requirements are met exactly
        return requiredCounts.equals(matchedCounts);
    }

    /**
     * Check if the provided ingredients satisfy the minimum requirements for this recipe
     * @param providedIngredients list of item stacks to check
     * @param multiplier how many times the recipe should be crafted
     * @return true if ingredients are sufficient for the given multiplier
     */
    public boolean hasEnoughIngredients(List<ItemStack> providedIngredients, int multiplier) {
        // Track which ingredients we've matched
        Map<RecipeIngredient, Integer> matchedCounts = new HashMap<>();
        for (RecipeIngredient ingredient : ingredients) {
            matchedCounts.put(ingredient, 0);
        }

        // Count provided items
        for (ItemStack stack : providedIngredients) {
            if (stack == null || stack.getType().isAir()) continue;

            HItemStack hStack = HItemStack.getFromStack(stack);
            if (hStack != null) {
                String itemId = hStack.getItem().getKey().toString();

                // Try to match with each recipe ingredient
                for (RecipeIngredient ingredient : ingredients) {
                    if (ingredient.matches(itemId)) {
                        matchedCounts.put(ingredient, matchedCounts.get(ingredient) + stack.getAmount());
                        break;
                    }
                }
            }
        }

        // Check if we have at least the required amount of each ingredient
        for (RecipeIngredient ingredient : ingredients) {
            int required = ingredient.getAmount() * multiplier;
            if (matchedCounts.getOrDefault(ingredient, 0) < required) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the maximum number of times this recipe can be crafted with the provided ingredients
     * @param providedIngredients list of item stacks to check
     * @return maximum craft count, 0 if recipe cannot be crafted
     */
    public int getMaxCraftableCount(List<ItemStack> providedIngredients) {
        // Track which ingredients we've matched
        Map<RecipeIngredient, Integer> matchedCounts = new HashMap<>();
        for (RecipeIngredient ingredient : ingredients) {
            matchedCounts.put(ingredient, 0);
        }

        // Count provided items
        for (ItemStack stack : providedIngredients) {
            if (stack == null || stack.getType().isAir()) continue;

            HItemStack hStack = HItemStack.getFromStack(stack);
            if (hStack != null) {
                String itemId = hStack.getItem().getKey().toString();

                // Try to match with each recipe ingredient
                for (RecipeIngredient ingredient : ingredients) {
                    if (ingredient.matches(itemId)) {
                        matchedCounts.put(ingredient, matchedCounts.get(ingredient) + stack.getAmount());
                        break;
                    }
                }
            }
        }

        int maxCraftable = Integer.MAX_VALUE;
        for (RecipeIngredient ingredient : ingredients) {
            int available = matchedCounts.getOrDefault(ingredient, 0);
            int possibleCrafts = available / ingredient.getAmount();
            maxCraftable = Math.min(maxCraftable, possibleCrafts);
        }

        return maxCraftable == Integer.MAX_VALUE ? 0 : maxCraftable;
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

        // Serialize result or result modifier
        if (hasDynamicResult()) {
            section.set("resultType", "dynamic");
            ConfigurationSection modifierSection = section.createSection("resultModifier");
            resultModifier.serialize(modifierSection);
        } else {
            section.set("resultType", "fixed");
            ConfigurationSection resultSection = section.createSection("result");
            result.serialize(resultSection);
        }
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

        // Deserialize result or result modifier
        String resultType = section.getString("resultType", "fixed");
        if ("dynamic".equals(resultType)) {
            ConfigurationSection modifierSection = section.getConfigurationSection("resultModifier");
            if (modifierSection != null) {
                ResultModifier modifier = ResultModifier.deserialize(modifierSection);
                return new JobRecipe(id, jobId, requiredLevel, ingredients, modifier, baseExperience, craftingTime, minRarity, discoverable);
            }
        }

        // Default to fixed result
        RecipeResult result = null;
        ConfigurationSection resultSection = section.getConfigurationSection("result");
        if (resultSection != null) {
            result = RecipeResult.deserialize(resultSection);
        }

        return new JobRecipe(id, jobId, requiredLevel, ingredients, result, baseExperience, craftingTime, minRarity, discoverable);
    }
}
