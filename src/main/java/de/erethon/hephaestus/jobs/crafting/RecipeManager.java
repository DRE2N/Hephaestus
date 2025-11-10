package de.erethon.hephaestus.jobs.crafting;

import de.erethon.hephaestus.Hephaestus;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class RecipeManager {

    private final Map<String, JobRecipe> recipes = new HashMap<>();
    private final Map<String, Set<String>> recipesByJob = new HashMap<>();
    private final File recipesDirectory;

    public RecipeManager(File recipesDirectory) {
        this.recipesDirectory = recipesDirectory;
        loadRecipes();
    }

    private void loadRecipes() {
        if (!recipesDirectory.exists()) {
            recipesDirectory.mkdirs();
            Hephaestus.log("Created recipes directory at: " + recipesDirectory.getPath());
        }

        recipes.clear();
        recipesByJob.clear();

        List<File> recipeFiles = findAllYmlFiles(recipesDirectory);

        if (recipeFiles.isEmpty()) {
            Hephaestus.log("No recipe files found in " + recipesDirectory.getPath());
            return;
        }

        int totalLoaded = 0;
        for (File file : recipeFiles) {
            int loaded = loadRecipesFromFile(file);
            totalLoaded += loaded;
        }

        Hephaestus.log("Loaded " + totalLoaded + " recipes from " + recipeFiles.size() + " file(s)");
    }

    /**
     * Recursively finds all .yml files in the given directory and subdirectories
     */
    private List<File> findAllYmlFiles(File directory) {
        List<File> ymlFiles = new ArrayList<>();

        if (!directory.exists() || !directory.isDirectory()) {
            return ymlFiles;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return ymlFiles;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively search subdirectories
                ymlFiles.addAll(findAllYmlFiles(file));
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".yml")) {
                ymlFiles.add(file);
            }
        }

        return ymlFiles;
    }

    /**
     * Loads recipes from a single YAML file
     * @param file the file to load recipes from
     * @return the number of recipes loaded from this file
     */
    private int loadRecipesFromFile(File file) {
        int loadedCount = 0;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection recipesSection = config.getConfigurationSection("recipes");

            if (recipesSection == null) {
                Hephaestus.log("No recipes section found in " + file.getName());
                return 0;
            }

            for (String key : recipesSection.getKeys(false)) {
                ConfigurationSection recipeSection = recipesSection.getConfigurationSection(key);
                if (recipeSection != null) {
                    try {
                        JobRecipe recipe = JobRecipe.deserialize(recipeSection);

                        // Check for duplicate recipe IDs
                        if (recipes.containsKey(recipe.getId())) {
                            Hephaestus.log("Warning: Duplicate recipe ID '" + recipe.getId() +
                                    "' found in " + file.getName() + " - overwriting previous definition");
                        }

                        recipes.put(recipe.getId(), recipe);
                        recipesByJob.computeIfAbsent(recipe.getJobId(), k -> new HashSet<>()).add(recipe.getId());

                        loadedCount++;
                    } catch (Exception e) {
                        Hephaestus.log("Failed to load recipe '" + key + "' from " + file.getName() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }

            if (loadedCount > 0) {
                Hephaestus.log("Loaded " + loadedCount + " recipe(s) from " + file.getName());
            }
        } catch (Exception e) {
            Hephaestus.log("Failed to load recipe file " + file.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        return loadedCount;
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
        if (jobRecipeIds == null) {
            Hephaestus.log("No recipes found for job: " + jobId);
            return null;
        }

        Hephaestus.log("Checking " + jobRecipeIds.size() + " recipes for job '" + jobId + "'");

        // First try to find recipes that match ingredients for discovery
        for (String recipeId : jobRecipeIds) {
            JobRecipe recipe = recipes.get(recipeId);
            if (recipe == null) continue;

            Hephaestus.log("  Checking recipe: " + recipeId);
            Hephaestus.log("    - Discoverable: " + recipe.isDiscoverable());

            if (recipe.isDiscoverable()) {
                boolean matches = recipe.matchesIngredientsForDiscovery(ingredients);
                Hephaestus.log("    - Matches ingredients: " + matches);

                if (matches) {
                    Hephaestus.log("  -> Found matching recipe: " + recipeId);
                    return recipe;
                }
            }
        }

        Hephaestus.log("No matching recipe found for discovery");
        return null;
    }

    /**
     * Discover all recipes that match the given ingredients
     * @param jobId the job ID to search recipes for
     * @param ingredients list of item stacks provided for discovery
     * @return list of all discovered recipes (may be empty)
     */
    public List<JobRecipe> discoverAllRecipesWithIngredients(String jobId, List<ItemStack> ingredients) {
        List<JobRecipe> matchingRecipes = new ArrayList<>();
        Set<String> jobRecipeIds = recipesByJob.get(jobId);

        if (jobRecipeIds == null) {
            Hephaestus.log("No recipes found for job: " + jobId);
            return matchingRecipes;
        }

        Hephaestus.log("Checking " + jobRecipeIds.size() + " recipes for job '" + jobId + "'");

        // Find all recipes that match ingredients for discovery
        for (String recipeId : jobRecipeIds) {
            JobRecipe recipe = recipes.get(recipeId);
            if (recipe == null) continue;

            Hephaestus.log("  Checking recipe: " + recipeId);
            Hephaestus.log("    - Discoverable: " + recipe.isDiscoverable());

            if (recipe.isDiscoverable()) {
                boolean matches = recipe.matchesIngredientsForDiscovery(ingredients);
                Hephaestus.log("    - Matches ingredients: " + matches);

                if (matches) {
                    Hephaestus.log("  -> Found matching recipe: " + recipeId);
                    matchingRecipes.add(recipe);
                }
            }
        }

        Hephaestus.log("Found " + matchingRecipes.size() + " matching recipe(s) for discovery");
        return matchingRecipes;
    }

    public void reloadRecipes() {
        loadRecipes();
    }

    public boolean hasRecipe(String recipeId) {
        return recipes.containsKey(recipeId);
    }
}
