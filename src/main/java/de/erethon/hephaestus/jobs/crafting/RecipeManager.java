package de.erethon.hephaestus.jobs.crafting;

import de.erethon.bedrock.chat.MessageUtil;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HRarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RecipeManager {

    private final Map<String, JobRecipe> recipes = new HashMap<>();
    private final Map<String, Set<String>> recipesByJob = new HashMap<>();
    private final File recipesFile;

    public RecipeManager(File recipesFile) {
        this.recipesFile = recipesFile;
        loadRecipes();
    }

    private void loadRecipes() {
        if (!recipesFile.exists()) {
            createDefaultRecipes();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");

        if (recipesSection == null) {
            Hephaestus.log("No recipes section found in recipes.yml");
            return;
        }

        recipes.clear();
        recipesByJob.clear();

        for (String key : recipesSection.getKeys(false)) {
            ConfigurationSection recipeSection = recipesSection.getConfigurationSection(key);
            if (recipeSection != null) {
                try {
                    JobRecipe recipe = JobRecipe.deserialize(recipeSection);
                    recipes.put(recipe.getId(), recipe);

                    recipesByJob.computeIfAbsent(recipe.getJobId(), k -> new HashSet<>()).add(recipe.getId());

                    Hephaestus.log("Loaded recipe: " + recipe.getId());
                } catch (Exception e) {
                    Hephaestus.log("Failed to load recipe: " + key + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        Hephaestus.log("Loaded " + recipes.size() + " recipes from configuration");
    }

    private void createDefaultRecipes() {
        YamlConfiguration config = new YamlConfiguration();

        // Make some examples so we don't have the same issue as with JXL again
        createMinerRecipes(config);
        createSmithRecipes(config);
        createAlchemistRecipes(config);

        try {
            config.save(recipesFile);
            Hephaestus.log("Created default recipes configuration at: " + recipesFile.getPath());
        } catch (IOException e) {
            Hephaestus.log("Failed to create default recipes configuration: " + e.getMessage());
        }
    }

    private void createMinerRecipes(YamlConfiguration config) {
        ConfigurationSection recipesSection = config.createSection("recipes");

        // Iron Pickaxe Recipe example
        ConfigurationSection ironPickaxeSection = recipesSection.createSection("iron_pickaxe");

        List<RecipeIngredient> ingredients = Arrays.asList(
                new RecipeIngredient("minecraft:iron_ingot", 3),
                new RecipeIngredient("minecraft:stick", 2)
        );
        RecipeResult result = new RecipeResult("minecraft:iron_pickaxe", 1);

        JobRecipe ironPickaxeRecipe = new JobRecipe(
                "iron_pickaxe", "miner", 10, ingredients, result, 150L, 60, HRarity.COMMON, true
        );
        ironPickaxeRecipe.serialize(ironPickaxeSection);
    }

    private void createSmithRecipes(YamlConfiguration config) {
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            recipesSection = config.createSection("recipes");
        }
        // Iron Sword Recipe example
        ConfigurationSection ironSwordSection = recipesSection.createSection("iron_sword");

        List<RecipeIngredient> ingredients = Arrays.asList(
                new RecipeIngredient("minecraft:iron_ingot", 2),
                new RecipeIngredient("minecraft:stick", 1)
        );
        RecipeResult result = new RecipeResult("minecraft:iron_sword", 1);

        JobRecipe ironSwordRecipe = new JobRecipe(
                "iron_sword", "smith", 15, ingredients, result, 200L, 80, HRarity.COMMON, true
        );
        ironSwordRecipe.serialize(ironSwordSection);
    }

    private void createAlchemistRecipes(YamlConfiguration config) {
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            recipesSection = config.createSection("recipes");
        }

        // Healing Potion Recipe example
        ConfigurationSection healingPotionSection = recipesSection.createSection("healing_potion");

        List<RecipeIngredient> ingredients = Arrays.asList(
                new RecipeIngredient("minecraft:glass_bottle", 1),
                new RecipeIngredient("minecraft:spider_eye", 1),
                new RecipeIngredient("minecraft:sugar", 1)
        );
        RecipeResult result = new RecipeResult("minecraft:potion", 1);

        JobRecipe healingPotionRecipe = new JobRecipe(
                "healing_potion", "alchemist", 5, ingredients, result, 100L, 40, HRarity.COMMON, true
        );
        healingPotionRecipe.serialize(healingPotionSection);
    }

    public JobRecipe getRecipe(String recipeId) {
        return recipes.get(recipeId);
    }

    public Collection<JobRecipe> getAllRecipes() {
        return new ArrayList<>(recipes.values());
    }

    public List<JobRecipe> getRecipesForJob(String jobId) {
        Set<String> recipeIds = recipesByJob.getOrDefault(jobId, new HashSet<>());
        return recipeIds.stream()
                .map(recipes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<JobRecipe> getDiscoverableRecipesForJob(String jobId) {
        return getRecipesForJob(jobId).stream()
                .filter(JobRecipe::isDiscoverable)
                .collect(Collectors.toList());
    }

    public List<JobRecipe> getRecipesForJobAndLevel(String jobId, int level) {
        return getRecipesForJob(jobId).stream()
                .filter(recipe -> recipe.getRequiredLevel() <= level)
                .collect(Collectors.toList());
    }

    /**
     * Attempts to discover a recipe based on provided ingredients
     * @param jobId the job to search recipes for
     * @param ingredients the ingredients provided
     * @return the discovered recipe, or null if no match found
     */
    public JobRecipe discoverRecipe(String jobId, List<ItemStack> ingredients) {
        List<JobRecipe> discoverableRecipes = getDiscoverableRecipesForJob(jobId);

        for (JobRecipe recipe : discoverableRecipes) {
            if (recipe.matchesIngredients(ingredients)) {
                return recipe;
            }
        }

        return null;
    }

    /**
     * Discover a recipe using multiple ingredients (new method for improved discovery)
     * @param jobId the job ID to search recipes for
     * @param ingredients list of item stacks provided for discovery
     * @return discovered recipe or null if none found
     */
    public JobRecipe discoverRecipeWithIngredients(String jobId, List<ItemStack> ingredients) {
        Set<String> jobRecipeIds = recipesByJob.get(jobId);
        if (jobRecipeIds == null) return null;

        // First try to find recipes that match ingredients for discovery
        for (String recipeId : jobRecipeIds) {
            JobRecipe recipe = recipes.get(recipeId);
            if (recipe != null && recipe.isDiscoverable() && recipe.matchesIngredientsForDiscovery(ingredients)) {
                return recipe;
            }
        }
        return null;
    }

    public void reloadRecipes() {
        loadRecipes();
    }

    public boolean hasRecipe(String recipeId) {
        return recipes.containsKey(recipeId);
    }
}
