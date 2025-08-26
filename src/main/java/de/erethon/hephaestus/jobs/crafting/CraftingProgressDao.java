package de.erethon.hephaestus.jobs.crafting;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CraftingProgressDao {

    @SqlUpdate("""
        CREATE TABLE IF NOT EXISTS character_discovered_recipes (
            character_id UUID NOT NULL,
            recipe_id VARCHAR(255) NOT NULL,
            discovered_at TIMESTAMP NOT NULL DEFAULT NOW(),
            PRIMARY KEY (character_id, recipe_id)
        )
        """)
    void createDiscoveredRecipesTable();

    @SqlUpdate("""
        CREATE TABLE IF NOT EXISTS character_recipe_crafts (
            character_id UUID NOT NULL,
            recipe_id VARCHAR(255) NOT NULL,
            times_crafted INTEGER NOT NULL DEFAULT 0,
            last_crafted TIMESTAMP DEFAULT NOW(),
            PRIMARY KEY (character_id, recipe_id)
        )
        """)
    void createRecipeCraftsTable();

    @SqlUpdate("""
        INSERT INTO character_discovered_recipes (character_id, recipe_id, discovered_at) 
        VALUES (:characterId, :recipeId, NOW()) 
        ON CONFLICT (character_id, recipe_id) DO NOTHING
        """)
    void discoverRecipe(@Bind("characterId") UUID characterId, @Bind("recipeId") String recipeId);

    @SqlUpdate("""
        INSERT INTO character_recipe_crafts (character_id, recipe_id, times_crafted, last_crafted) 
        VALUES (:characterId, :recipeId, 1, NOW()) 
        ON CONFLICT (character_id, recipe_id) 
        DO UPDATE SET 
            times_crafted = character_recipe_crafts.times_crafted + 1,
            last_crafted = NOW()
        """)
    void recordCraft(@Bind("characterId") UUID characterId, @Bind("recipeId") String recipeId);

    @SqlQuery("""
        SELECT times_crafted FROM character_recipe_crafts 
        WHERE character_id = :characterId AND recipe_id = :recipeId
        """)
    Optional<Integer> getCraftCount(@Bind("characterId") UUID characterId, @Bind("recipeId") String recipeId);

    @SqlQuery("SELECT recipe_id FROM character_discovered_recipes WHERE character_id = :characterId")
    List<String> getDiscoveredRecipes(@Bind("characterId") UUID characterId);

    @SqlQuery("""
        SELECT recipe_id, times_crafted FROM character_recipe_crafts 
        WHERE character_id = :characterId
        """)
    List<CraftCount> getAllCraftCounts(@Bind("characterId") UUID characterId);

    @SqlQuery("""
        SELECT EXISTS(
            SELECT 1 FROM character_discovered_recipes 
            WHERE character_id = :characterId AND recipe_id = :recipeId
        )
        """)
    boolean hasDiscoveredRecipe(@Bind("characterId") UUID characterId, @Bind("recipeId") String recipeId);

    /**
     * Record class for mapping craft count results
     */
    record CraftCount(String recipeId, int timesCrafted) {}
}
