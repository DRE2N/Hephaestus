package de.erethon.hephaestus.jobs.crafting;

import de.erethon.hephaestus.jobs.JobDatabaseManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PlayerCraftingProgress {

    private final JobDatabaseManager databaseManager;

    public PlayerCraftingProgress(JobDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Records that a player has discovered a recipe
     */
    public CompletableFuture<Void> discoverRecipe(UUID characterId, String recipeId) {
        return databaseManager.executeAsync(handle -> {
            CraftingProgressDao dao = handle.attach(CraftingProgressDao.class);
            dao.discoverRecipe(characterId, recipeId);
        });
    }

    /**
     * Records a crafting attempt and increments the count
     */
    public CompletableFuture<Integer> recordCraft(UUID characterId, String recipeId) {
        return databaseManager.queryAsync(handle -> {
            CraftingProgressDao dao = handle.attach(CraftingProgressDao.class);
            dao.recordCraft(characterId, recipeId);
            // Get the updated count
            return dao.getCraftCount(characterId, recipeId).orElse(1);
        });
    }

    /**
     * Records multiple crafting attempts and increments the count by the specified amount
     */
    public CompletableFuture<Integer> recordCraftMultiple(UUID characterId, String recipeId, int quantity) {
        return databaseManager.queryAsync(handle -> {
            CraftingProgressDao dao = handle.attach(CraftingProgressDao.class);
            // Record each craft individually to maintain proper count
            for (int i = 0; i < quantity; i++) {
                dao.recordCraft(characterId, recipeId);
            }
            // Get the updated count
            return dao.getCraftCount(characterId, recipeId).orElse(quantity);
        });
    }

    /**
     * Gets all discovered recipes for a character
     */
    public CompletableFuture<Set<String>> getDiscoveredRecipes(UUID characterId) {
        return databaseManager.queryAsync(handle -> {
            CraftingProgressDao dao = handle.attach(CraftingProgressDao.class);
            List<String> recipes = dao.getDiscoveredRecipes(characterId);
            return new HashSet<>(recipes);
        });
    }

    /**
     * Gets the number of times a character has crafted a specific recipe
     */
    public CompletableFuture<Integer> getCraftCount(UUID characterId, String recipeId) {
        return databaseManager.queryAsync(handle -> {
            CraftingProgressDao dao = handle.attach(CraftingProgressDao.class);
            return dao.getCraftCount(characterId, recipeId).orElse(0);
        });
    }

    /**
     * Gets all craft counts for a character
     */
    public CompletableFuture<Map<String, Integer>> getAllCraftCounts(UUID characterId) {
        return databaseManager.queryAsync(handle -> {
            CraftingProgressDao dao = handle.attach(CraftingProgressDao.class);
            List<CraftingProgressDao.CraftCount> counts = dao.getAllCraftCounts(characterId);
            return counts.stream()
                    .collect(Collectors.toMap(
                            CraftingProgressDao.CraftCount::recipeId,
                            CraftingProgressDao.CraftCount::timesCrafted
                    ));
        });
    }

    /**
     * Convenience method to check if a recipe is discovered
     */
    public CompletableFuture<Boolean> hasDiscoveredRecipe(UUID characterId, String recipeId) {
        return databaseManager.queryAsync(handle -> {
            CraftingProgressDao dao = handle.attach(CraftingProgressDao.class);
            return dao.hasDiscoveredRecipe(characterId, recipeId);
        });
    }
}
