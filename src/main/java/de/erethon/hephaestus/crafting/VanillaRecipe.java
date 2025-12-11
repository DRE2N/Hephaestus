package de.erethon.hephaestus.crafting;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import net.minecraft.resources.Identifier;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents a vanilla crafting table recipe for HItems
 */
public class VanillaRecipe {

    private final String id;
    private final RecipeType type;
    private final ItemStack result;
    private final String resultItemId; // Store original item ID for level application
    private final int fixedLevel; // For fixed level results
    private final int minLevel; // For random level range
    private final int maxLevel; // For random level range
    private final List<String> pattern; // For shaped recipes
    private final Map<Character, String> ingredients; // For shaped recipes
    private final List<String> shapelessIngredients; // For shapeless recipes

    public enum RecipeType {
        SHAPED,
        SHAPELESS
    }

    public VanillaRecipe(String id, RecipeType type, ItemStack result, String resultItemId, int fixedLevel, int minLevel, int maxLevel,
                        List<String> pattern, Map<Character, String> ingredients, List<String> shapelessIngredients) {
        this.id = id;
        this.type = type;
        this.result = result;
        this.resultItemId = resultItemId;
        this.fixedLevel = fixedLevel;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.pattern = pattern != null ? new ArrayList<>(pattern) : null;
        this.ingredients = ingredients != null ? new HashMap<>(ingredients) : null;
        this.shapelessIngredients = shapelessIngredients != null ? new ArrayList<>(shapelessIngredients) : null;
    }

    public static VanillaRecipe deserialize(ConfigurationSection section) {
        String id = section.getName();
        String typeString = section.getString("type", "shaped");
        RecipeType type = RecipeType.valueOf(typeString.toUpperCase());

        // Parse result
        ConfigurationSection resultSection = section.getConfigurationSection("result");
        if (resultSection == null) {
            throw new IllegalArgumentException("Recipe " + id + " has no result section");
        }

        String resultItemId = resultSection.getString("item");
        int amount = resultSection.getInt("amount", 1);
        ItemStack result = createItemStack(resultItemId, amount);

        // Parse level configuration
        int fixedLevel = 0;
        int minLevel = 0;
        int maxLevel = 0;

        if (resultSection.contains("level")) {
            Object levelConfig = resultSection.get("level");
            if (levelConfig instanceof Integer) {
                // Fixed level
                fixedLevel = (Integer) levelConfig;
            } else if (levelConfig instanceof String) {
                String levelStr = (String) levelConfig;
                if (levelStr.contains("-")) {
                    // Range format: "1-5"
                    String[] parts = levelStr.split("-");
                    if (parts.length == 2) {
                        try {
                            minLevel = Integer.parseInt(parts[0].trim());
                            maxLevel = Integer.parseInt(parts[1].trim());
                            if (minLevel > maxLevel) {
                                throw new IllegalArgumentException("Invalid level range: min > max");
                            }
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid level range format: " + levelStr);
                        }
                    } else {
                        throw new IllegalArgumentException("Invalid level range format: " + levelStr);
                    }
                } else {
                    // Single level as string
                    try {
                        fixedLevel = Integer.parseInt(levelStr);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid level format: " + levelStr);
                    }
                }
            } else if (levelConfig instanceof ConfigurationSection) {
                ConfigurationSection levelSection = (ConfigurationSection) levelConfig;
                minLevel = levelSection.getInt("min", 0);
                maxLevel = levelSection.getInt("max", 0);
                if (minLevel > maxLevel && maxLevel > 0) {
                    throw new IllegalArgumentException("Invalid level range: min > max");
                }
            }
        }

        List<String> pattern = null;
        Map<Character, String> ingredients = null;
        List<String> shapelessIngredients = null;

        if (type == RecipeType.SHAPED) {
            pattern = section.getStringList("pattern");
            if (pattern.isEmpty()) {
                throw new IllegalArgumentException("Shaped recipe " + id + " has no pattern");
            }

            ingredients = new HashMap<>();
            ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
            if (ingredientsSection != null) {
                for (String key : ingredientsSection.getKeys(false)) {
                    if (key.length() == 1) {
                        ingredients.put(key.charAt(0), ingredientsSection.getString(key));
                    }
                }
            }
        } else if (type == RecipeType.SHAPELESS) {
            shapelessIngredients = section.getStringList("ingredients");
            if (shapelessIngredients.isEmpty()) {
                throw new IllegalArgumentException("Shapeless recipe " + id + " has no ingredients");
            }
        }

        return new VanillaRecipe(id, type, result, resultItemId, fixedLevel, minLevel, maxLevel, pattern, ingredients, shapelessIngredients);
    }

    private static ItemStack createItemStack(String itemId, int amount) {
        // Try to get as HItem first
        HItem hItem = Hephaestus.getItem(itemId);
        if (hItem != null) {
            return hItem.createStack(amount).getBukkitStack();
        }

        // Fall back to vanilla material
        try {
            Identifier location = Identifier.parse(itemId);
            if (location.getNamespace().equals("minecraft")) {
                Material material = Material.getMaterial(location.getPath().toUpperCase());
                if (material != null) {
                    return new ItemStack(material, amount);
                }
            }
        } catch (Exception e) {
            // Invalid resource location format, try direct material lookup
            Material material = Material.getMaterial(itemId.toUpperCase());
            if (material != null) {
                return new ItemStack(material, amount);
            }
        }

        throw new IllegalArgumentException("Unknown item: " + itemId);
    }

    /**
     * Checks if the given crafting matrix matches this recipe
     * @param matrix 3x3 crafting matrix (9 elements)
     * @return The result ItemStack if matches, null otherwise
     */
    public ItemStack matches(ItemStack[] matrix) {
        if (type == RecipeType.SHAPED) {
            return matchesShaped(matrix);
        } else {
            return matchesShapeless(matrix);
        }
    }

    /**
     * Creates the result ItemStack with appropriate level applied
     * @return The crafted result with level applied
     */
    public ItemStack createResult() {
        // Clone the base result
        ItemStack resultStack = result.clone();

        // Check if this is an HItem that needs level application
        HItem hItem = Hephaestus.getItem(getResultItemId());
        if (hItem != null) {
            // Determine the level to apply
            int level = calculateLevel();

            // Create HItemStack with the determined level
            HItemStack hItemStack = hItem.createStack(resultStack.getAmount(), level);
            return hItemStack.getBukkitStack();
        }

        // Return vanilla item as-is
        return resultStack;
    }

    /**
     * Calculates the level for this recipe result
     * @return The level to apply (0 if no level configured)
     */
    private int calculateLevel() {
        if (fixedLevel > 0) {
            return fixedLevel;
        } else if (maxLevel > minLevel) {
            // Random level in range
            Random random = new Random();
            return random.nextInt(maxLevel - minLevel + 1) + minLevel;
        }
        return 0; // No level configured
    }

    private ItemStack matchesShaped(ItemStack[] matrix) {
        if (pattern == null || ingredients == null) {
            return null;
        }

        // Convert pattern to 3x3 matrix
        String[][] patternMatrix = new String[3][3];
        for (int i = 0; i < 3; i++) {
            String row = i < pattern.size() ? pattern.get(i) : "   ";
            for (int j = 0; j < 3; j++) {
                patternMatrix[i][j] = j < row.length() ? String.valueOf(row.charAt(j)) : " ";
            }
        }

        // Check exact match
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int index = i * 3 + j;
                ItemStack actualItem = matrix[index];
                String patternChar = patternMatrix[i][j];

                if (patternChar.equals(" ")) {
                    // Empty slot required
                    if (actualItem != null && actualItem.getType() != Material.AIR) {
                        return null;
                    }
                } else {
                    // Specific ingredient required
                    String requiredItemId = ingredients.get(patternChar.charAt(0));
                    if (requiredItemId == null) {
                        return null; // Unknown pattern character
                    }

                    ItemStack requiredItem = createItemStack(requiredItemId, 1);
                    if (!VanillaRecipeManager.itemsMatch(requiredItem, actualItem)) {
                        return null;
                    }
                }
            }
        }

        return createResult();
    }

    private ItemStack matchesShapeless(ItemStack[] matrix) {
        if (shapelessIngredients == null) {
            return null;
        }

        // Count required ingredients
        Map<String, Integer> requiredCounts = new HashMap<>();
        for (String ingredient : shapelessIngredients) {
            requiredCounts.put(ingredient, requiredCounts.getOrDefault(ingredient, 0) + 1);
        }

        // Count actual ingredients
        Map<String, Integer> actualCounts = new HashMap<>();
        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            // Find matching required ingredient
            String matchingIngredient = null;
            for (String requiredIngredient : requiredCounts.keySet()) {
                ItemStack requiredItem = createItemStack(requiredIngredient, 1);
                if (VanillaRecipeManager.itemsMatch(requiredItem, item)) {
                    matchingIngredient = requiredIngredient;
                    break;
                }
            }

            if (matchingIngredient == null) {
                return null; // Unexpected item in matrix
            }

            actualCounts.put(matchingIngredient, actualCounts.getOrDefault(matchingIngredient, 0) + item.getAmount());
        }

        // Check if counts match exactly
        for (Map.Entry<String, Integer> entry : requiredCounts.entrySet()) {
            int required = entry.getValue();
            int actual = actualCounts.getOrDefault(entry.getKey(), 0);
            if (actual != required) {
                return null;
            }
        }

        // Check that we don't have extra items
        for (String actualIngredient : actualCounts.keySet()) {
            if (!requiredCounts.containsKey(actualIngredient)) {
                return null;
            }
        }

        return createResult();
    }

    public String getId() {
        return id;
    }

    public RecipeType getType() {
        return type;
    }

    public ItemStack getResult() {
        return result.clone();
    }

    public String getResultItemId() {
        return resultItemId;
    }

    public List<String> getPattern() {
        return pattern != null ? new ArrayList<>(pattern) : null;
    }

    public Map<Character, String> getIngredients() {
        return ingredients != null ? new HashMap<>(ingredients) : null;
    }

    public List<String> getShapelessIngredients() {
        return shapelessIngredients != null ? new ArrayList<>(shapelessIngredients) : null;
    }
}
