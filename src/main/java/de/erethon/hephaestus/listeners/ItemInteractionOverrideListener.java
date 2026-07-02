package de.erethon.hephaestus.listeners;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.items.HItemUtil;
import de.erethon.hephaestus.items.interactions.ItemInteractionSettings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ItemInteractionOverrideListener implements Listener {

    private final Hephaestus plugin;
    private final Set<NamespacedKey> registeredSmeltingRecipes = new HashSet<>();

    public ItemInteractionOverrideListener(Hephaestus plugin) {
        this.plugin = plugin;
    }

    public void registerSmeltingRecipes() {
        clearSmeltingRecipes();
        for (var key : plugin.getLibrary().getKeys()) {
            HItem item = plugin.getLibrary().get(key);
            if (item == null || !item.getInteractionSettings().hasSmelting()) {
                continue;
            }
            ItemInteractionSettings.SmeltingSettings settings = item.getInteractionSettings().getSmelting();
            ItemStack result = HItemUtil.createItemStack(settings.result(), settings.amount());
            if (result == null || result.getType().isAir()) {
                plugin.getLogger().warning("Invalid smelting result '" + settings.result() + "' for item " + item.getKey());
                continue;
            }
            RecipeChoice input = createInputChoice(item);
            String basePath = sanitize(item.getKey().toString());
            if (settings.furnace()) {
                NamespacedKey recipeKey = new NamespacedKey(plugin, "smelting_furnace_" + basePath);
                Bukkit.addRecipe(new FurnaceRecipe(recipeKey, result, input, settings.experience(), settings.cookingTime()));
                registeredSmeltingRecipes.add(recipeKey);
            }
            if (settings.blastFurnace()) {
                NamespacedKey recipeKey = new NamespacedKey(plugin, "smelting_blast_" + basePath);
                Bukkit.addRecipe(new BlastingRecipe(recipeKey, result, input, settings.experience(), settings.cookingTime()));
                registeredSmeltingRecipes.add(recipeKey);
            }
            if (settings.smoker()) {
                NamespacedKey recipeKey = new NamespacedKey(plugin, "smelting_smoker_" + basePath);
                Bukkit.addRecipe(new SmokingRecipe(recipeKey, result, input, settings.experience(), settings.cookingTime()));
                registeredSmeltingRecipes.add(recipeKey);
            }
        }
    }

    private void clearSmeltingRecipes() {
        for (NamespacedKey key : registeredSmeltingRecipes) {
            Bukkit.removeRecipe(key);
        }
        registeredSmeltingRecipes.clear();
    }

    private RecipeChoice createInputChoice(HItem item) {
        if (item.isVanilla()) {
            Material material = HItemUtil.materialFromId(item.getKey().toString());
            if (material != null) {
                return new RecipeChoice.MaterialChoice(material);
            }
        }
        return new RecipeChoice.ExactChoice(item.createStack().getBukkitStack());
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack base = event.getInventory().getFirstItem();
        ItemStack material = event.getInventory().getSecondItem();
        RepairResult repairResult = createRepairResult(base, material);
        if (repairResult == null) {
            return;
        }
        event.setResult(repairResult.result());
        event.getInventory().setRepairCost(repairResult.cost());
    }

    @EventHandler
    public void onAnvilResultClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil) || event.getRawSlot() != 2) {
            return;
        }
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }
        RepairResult repairResult = createRepairResult(anvil.getFirstItem(), anvil.getSecondItem());
        if (repairResult == null) {
            return;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            event.setCancelled(true);
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE && player.getLevel() < repairResult.cost()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        event.setCurrentItem(null);
        anvil.setFirstItem(null);

        ItemStack material = anvil.getSecondItem();
        if (material != null) {
            int remaining = material.getAmount() - repairResult.materialsUsed();
            if (remaining > 0) {
                ItemStack remainingStack = material.clone();
                remainingStack.setAmount(remaining);
                anvil.setSecondItem(remainingStack);
            } else {
                anvil.setSecondItem(null);
            }
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setLevel(player.getLevel() - repairResult.cost());
        }
        player.setItemOnCursor(repairResult.result());
    }

    private RepairResult createRepairResult(ItemStack base, ItemStack material) {
        if (base == null || base.getType().isAir() || material == null || material.getType().isAir()) {
            return null;
        }
        HItemStack hBase = Hephaestus.getStack(base);
        if (hBase == null || hBase.getItem() == null || !hBase.getItem().getInteractionSettings().hasRepair()) {
            return null;
        }
        ItemInteractionSettings.RepairSettings settings = hBase.getItem().getInteractionSettings().getRepair();
        String materialId = HItemUtil.getItemId(material);
        if (materialId == null || settings.materials().stream().noneMatch(materialId::equals)) {
            return null;
        }
        ItemMeta meta = base.getItemMeta();
        if (!(meta instanceof Damageable damageable) || !damageable.hasDamage()) {
            return null;
        }
        int maxDurability = base.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return null;
        }
        int repairPerItem = Math.max(1, (int) Math.round(maxDurability * settings.durabilityPerItem()));
        int materialsNeeded = Math.max(1, (int) Math.ceil((double) damageable.getDamage() / repairPerItem));
        int materialsUsed = Math.min(material.getAmount(), materialsNeeded);
        int repairedDamage = Math.max(0, damageable.getDamage() - (repairPerItem * materialsUsed));

        ItemStack result = base.clone();
        result.setAmount(1);
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta instanceof Damageable resultDamageable) {
            resultDamageable.setDamage(repairedDamage);
            result.setItemMeta(resultMeta);
        }
        return new RepairResult(result, settings.cost(), materialsUsed);
    }

    private String sanitize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace(':', '_').replaceAll("[^a-z0-9_./-]", "_");
    }

    private record RepairResult(ItemStack result, int cost, int materialsUsed) {
    }
}
