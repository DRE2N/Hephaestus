package de.erethon.hephaestus.listeners;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.crafting.VanillaRecipeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles vanilla crafting table events for HItem recipes
 */
public class CraftingListener implements Listener {

    private final Hephaestus plugin;
    private final VanillaRecipeManager recipeManager;

    public CraftingListener(Hephaestus plugin, VanillaRecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        // Convert to 3x3 array (matrix is always 9 elements for crafting table)
        if (matrix.length != 9) {
            return; // Not a crafting table
        }

        // Try to find a matching recipe
        ItemStack result = recipeManager.findRecipeResult(matrix);

        if (result != null) {
            // Set the result in the crafting inventory
            inventory.setResult(result);
            plugin.getLogger().fine("Matched vanilla recipe for crafting result: " + result.getType());
        }
        // If no recipe matches, the event will proceed with vanilla behavior
        // or the result will remain null (no crafting possible)
    }
}
