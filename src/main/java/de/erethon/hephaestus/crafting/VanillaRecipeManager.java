package de.erethon.hephaestus.crafting;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.items.HItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.io.File;
import java.util.*;

/**
 * Manages vanilla crafting table recipes for HItems
 */
public class VanillaRecipeManager {

    private final Hephaestus plugin;
    private final File recipesFile;
    private final Map<String, VanillaRecipe> recipes = new HashMap<>();
    private final Set<NamespacedKey> registeredRecipeKeys = new HashSet<>();

    public VanillaRecipeManager(Hephaestus plugin, File recipesFile) {
        this.plugin = plugin;
        this.recipesFile = recipesFile;
        loadRecipes();
    }

    private void loadRecipes() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");

        if (recipesSection == null) {
            plugin.getLogger().info("No vanilla recipes section found in " + recipesFile.getName());
            return;
        }

        // Clear existing recipes
        clearRegisteredRecipes();
        recipes.clear();

        for (String key : recipesSection.getKeys(false)) {
            ConfigurationSection recipeSection = recipesSection.getConfigurationSection(key);
            if (recipeSection != null) {
                try {
                    VanillaRecipe recipe = VanillaRecipe.deserialize(recipeSection);
                    recipes.put(recipe.getId(), recipe);

                    // Register the recipe with Bukkit
                    registerBukkitRecipe(recipe);

                    plugin.getLogger().info("Loaded and registered vanilla recipe: " + recipe.getId());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load vanilla recipe " + key + ": " + e.getMessage());
                }
            }
        }

        plugin.getLogger().info("Loaded " + recipes.size() + " vanilla recipes");
    }

    private void registerBukkitRecipe(VanillaRecipe vanillaRecipe) {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "vanilla_recipe_" + vanillaRecipe.getId());

        // Remove existing recipe if it exists
        Bukkit.removeRecipe(recipeKey);

        try {
            Recipe bukkitRecipe;

            if (vanillaRecipe.getType() == VanillaRecipe.RecipeType.SHAPED) {
                bukkitRecipe = createShapedRecipe(vanillaRecipe, recipeKey);
            } else {
                bukkitRecipe = createShapelessRecipe(vanillaRecipe, recipeKey);
            }

            if (bukkitRecipe != null) {
                Bukkit.addRecipe(bukkitRecipe);
                registeredRecipeKeys.add(recipeKey);
                plugin.getLogger().fine("Registered Bukkit recipe: " + recipeKey);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register Bukkit recipe for " + vanillaRecipe.getId() + ": " + e.getMessage());
        }
    }

    private ShapedRecipe createShapedRecipe(VanillaRecipe vanillaRecipe, NamespacedKey recipeKey) {
        // Create a base result item for the recipe book (without level/random elements)
        ItemStack baseResult = createBaseResultItem(vanillaRecipe);
        if (baseResult == null) {
            return null;
        }

        ShapedRecipe shapedRecipe = new ShapedRecipe(recipeKey, baseResult);

        // Set the shape
        List<String> pattern = vanillaRecipe.getPattern();
        if (pattern == null || pattern.isEmpty()) {
            plugin.getLogger().warning("Malformed shaped pattern in recipe " + vanillaRecipe.getId() + ": pattern is empty");
            return null;
        }
        if (pattern.size() > 3) {
            plugin.getLogger().warning("Malformed shaped pattern in recipe " + vanillaRecipe.getId() + ": more than 3 rows");
        }

        String[] shapeArray = new String[Math.min(3, pattern.size())];
        for (int i = 0; i < shapeArray.length; i++) {
            String row = pattern.get(i);
            // Ensure the row is exactly 3 characters
            if (row.length() > 3) {
                plugin.getLogger().warning("Malformed shaped pattern row in recipe " + vanillaRecipe.getId() + ": '" + row + "' is longer than 3 characters");
                row = row.substring(0, 3);
            } else if (row.length() < 3) {
                row = String.format("%-3s", row); // Pad with spaces
            }
            shapeArray[i] = row;
        }

        shapedRecipe.shape(shapeArray);

        // Set ingredients
        Map<Character, String> ingredients = vanillaRecipe.getIngredients();
        if (ingredients != null) {
            for (Map.Entry<Character, String> entry : ingredients.entrySet()) {
                char key = entry.getKey();
                String itemId = entry.getValue();

                if (key == ' ') continue; // Skip spaces

                RecipeChoice ingredient = createRecipeChoice(itemId);
                if (ingredient != null) {
                    shapedRecipe.setIngredient(key, ingredient);
                } else {
                    plugin.getLogger().warning("Unknown shaped ingredient '" + itemId + "' in recipe " + vanillaRecipe.getId());
                }
            }
        }

        return shapedRecipe;
    }

    private ShapelessRecipe createShapelessRecipe(VanillaRecipe vanillaRecipe, NamespacedKey recipeKey) {
        // Create a base result item for the recipe book
        ItemStack baseResult = createBaseResultItem(vanillaRecipe);
        if (baseResult == null) {
            return null;
        }

        ShapelessRecipe shapelessRecipe = new ShapelessRecipe(recipeKey, baseResult);

        // Add ingredients
        List<String> ingredients = vanillaRecipe.getShapelessIngredients();
        if (ingredients != null) {
            for (String itemId : ingredients) {
                RecipeChoice ingredient = createRecipeChoice(itemId);
                if (ingredient != null) {
                    shapelessRecipe.addIngredient(ingredient);
                } else {
                    plugin.getLogger().warning("Unknown shapeless ingredient '" + itemId + "' in recipe " + vanillaRecipe.getId());
                }
            }
        }

        return shapelessRecipe;
    }

    private ItemStack createBaseResultItem(VanillaRecipe vanillaRecipe) {
        String resultItemId = vanillaRecipe.getResultItemId();
        if (resultItemId == null) {
            plugin.getLogger().warning("Vanilla recipe " + vanillaRecipe.getId() + " has no result item");
            return null;
        }

        // Try to get as HItem first
        HItem hItem = Hephaestus.getItem(resultItemId);
        if (hItem != null) {
            // Create a basic version for the recipe book (level 1, common rarity)
            HItemStack hStack = hItem.createStack(vanillaRecipe.getResult().getAmount(), 1);
            return hStack.getBukkitStack();
        }

        // Fall back to vanilla item
        ItemStack vanillaResult = createVanillaItem(resultItemId, vanillaRecipe.getResult().getAmount());
        if (vanillaResult == null || vanillaResult.getType().isAir()) {
            plugin.getLogger().warning("Unknown result item '" + resultItemId + "' in vanilla recipe " + vanillaRecipe.getId());
            return null;
        }
        return vanillaResult;
    }

    private RecipeChoice createRecipeChoice(String itemId) {
        // Try HItem first
        HItem hItem = Hephaestus.getItem(itemId);
        if (hItem != null && !hItem.isVanilla()) {
            return new RecipeChoice.ExactChoice(hItem.createStack().getBukkitStack());
        }

        // Try vanilla material
        Material material = HItemUtil.materialFromId(itemId);
        if (material != null && !material.isAir()) {
            return new RecipeChoice.MaterialChoice(material);
        }

        return null;
    }

    private ItemStack createVanillaItem(String itemId, int amount) {
        return HItemUtil.createItemStack(itemId, amount);
    }

    private void clearRegisteredRecipes() {
        for (NamespacedKey key : registeredRecipeKeys) {
            Bukkit.removeRecipe(key);
        }
        int clearedCount = registeredRecipeKeys.size();
        registeredRecipeKeys.clear();
        plugin.getLogger().fine("Cleared " + clearedCount + " registered recipes");
    }

    /**
     * Attempts to match a crafting matrix against all loaded recipes
     * @param matrix 3x3 crafting matrix (9 elements, can contain nulls)
     * @return The crafting result, or null if no recipe matches
     */
    public ItemStack findRecipeResult(ItemStack[] matrix) {
        if (matrix == null || matrix.length != 9) {
            return null;
        }

        for (VanillaRecipe recipe : recipes.values()) {
            ItemStack result = recipe.matches(matrix);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Gets all loaded recipes
     * @return map of recipe ID to recipe
     */
    public Map<String, VanillaRecipe> getRecipes() {
        return new HashMap<>(recipes);
    }

    /**
     * Reloads all recipes from the file
     */
    public void reload() {
        loadRecipes();
    }

    /**
     * Checks if two ItemStacks match for recipe purposes
     * This ignores custom data that shouldn't affect crafting compatibility
     */
    public static boolean itemsMatch(ItemStack required, ItemStack actual) {
        if (required == null && actual == null) {
            return true;
        }
        if (required == null || actual == null) {
            return false;
        }

        // Get HItemStacks to compare the base items
        HItemStack requiredHStack = Hephaestus.getStack(required);
        HItemStack actualHStack = Hephaestus.getStack(actual);

        if (requiredHStack != null && actualHStack != null) {
            // Both are HItems - compare the base HItem types
            return requiredHStack.getItem().getKey().equals(actualHStack.getItem().getKey());
        }
        String requiredId = HItemUtil.getItemId(required);
        String actualId = HItemUtil.getItemId(actual);
        return requiredId != null && requiredId.equals(actualId);
    }
}
