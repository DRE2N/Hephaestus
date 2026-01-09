package de.erethon.hephaestus.jobs.crafting.gui;

import de.erethon.hecate.data.HCharacter;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.jobs.CharacterJob;
import de.erethon.hephaestus.jobs.JobCharacterBridgeUtil;
import de.erethon.hephaestus.jobs.crafting.JobRecipe;
import de.erethon.hephaestus.jobs.crafting.PlayerCraftingProgress;
import de.erethon.hephaestus.jobs.crafting.RecipeManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RecipeDiscoveryGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final RecipeManager recipeManager;
    private final PlayerCraftingProgress progressManager;
    private final Player player;
    private final Inventory inventory;

    private static final int[] DISCOVERY_SLOTS = {9, 11, 13, 15, 17};
    private static final int[] SEPARATOR_SLOTS = {10, 12, 14, 16};
    private static final int DISCOVER_BUTTON_SLOT = 22;
    private static final int RESULT_SLOT = 34;
    private static final int CLEAR_BUTTON_SLOT = 31;
    private static final int CRAFTING_STATION_BUTTON_SLOT = 49;

    public RecipeDiscoveryGUI(Hephaestus plugin, Player player) {
        this.plugin = plugin;
        this.recipeManager = plugin.getRecipeManager();
        this.progressManager = plugin.getPlayerCraftingProgress();
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54,
            Component.text("Recipe Discovery", NamedTextColor.DARK_PURPLE));

        setupInterface();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupInterface() {
        ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            if (!isDiscoverySlot(i) && !isSeparatorSlot(i) && i != DISCOVER_BUTTON_SLOT && i != RESULT_SLOT
                && i != CLEAR_BUTTON_SLOT && i != CRAFTING_STATION_BUTTON_SLOT) {
                inventory.setItem(i, background);
            }
        }

        ItemStack discoverButton = createTranslatableGuiItem(Material.BREWING_STAND,
            Component.translatable("hephaestus.crafting.gui.discovery.discover.title"),
            Component.translatable("hephaestus.crafting.gui.discovery.discover.lore1"),
            Component.translatable("hephaestus.crafting.gui.discovery.discover.lore2"));
        inventory.setItem(DISCOVER_BUTTON_SLOT, discoverButton);

        ItemStack clearButton = createTranslatableGuiItem(Material.BARRIER,
            Component.translatable("hephaestus.crafting.gui.discovery.clear.title"),
            Component.translatable("hephaestus.crafting.gui.discovery.clear.lore"));
        inventory.setItem(CLEAR_BUTTON_SLOT, clearButton);

        ItemStack craftingButton = createTranslatableGuiItem(Material.CRAFTING_TABLE,
            Component.translatable("hephaestus.crafting.gui.discovery.crafting.title"),
            Component.translatable("hephaestus.crafting.gui.discovery.crafting.lore1"),
            Component.translatable("hephaestus.crafting.gui.discovery.crafting.lore2"));
        inventory.setItem(CRAFTING_STATION_BUTTON_SLOT, craftingButton);

        ItemStack resultPlaceholder = createTranslatableGuiItem(Material.LIME_STAINED_GLASS_PANE,
            Component.translatable("hephaestus.crafting.gui.discovery.result.title"),
            Component.translatable("hephaestus.crafting.gui.discovery.result.lore"));
        inventory.setItem(RESULT_SLOT, resultPlaceholder);

        ItemStack separator = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot : SEPARATOR_SLOTS) {
            inventory.setItem(slot, separator);
        }
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));

        if (lore.length > 0) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line, NamedTextColor.GRAY));
            }
            meta.lore(loreComponents);
        }

        item.setItemMeta(meta);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return item;
    }

    private ItemStack createTranslatableGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.color(NamedTextColor.WHITE));

        if (lore.length > 0) {
            List<Component> loreComponents = new ArrayList<>();
            for (Component line : lore) {
                loreComponents.add(line.color(NamedTextColor.GRAY));
            }
            meta.lore(loreComponents);
        }

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;

        int slot = event.getRawSlot();

        if (slot >= 0 && slot < 54) {
            if (slot == DISCOVER_BUTTON_SLOT) {
                event.setCancelled(true);
                attemptDiscovery();
            } else if (slot == CLEAR_BUTTON_SLOT) {
                event.setCancelled(true);
                clearSlots();
            } else if (slot == CRAFTING_STATION_BUTTON_SLOT) {
                event.setCancelled(true);
                new CraftingStationGUI(plugin, player).open();
            } else if (slot == RESULT_SLOT) {
                event.setCancelled(true);
            } else if (isDiscoverySlot(slot)) {
                Bukkit.getScheduler().runTaskLater(plugin, this::updateDiscoveryState, 1L);
            } else {
                event.setCancelled(true);
            }
        }
    }

    private void attemptDiscovery() {
        List<ItemStack> ingredients = getIngredients();
        if (ingredients.isEmpty()) {
            player.sendMessage(Component.text("Place some ingredients in the discovery slots first!", NamedTextColor.RED));
            return;
        }

        plugin.getLogger().info("Attempting recipe discovery for player " + player.getName());
        plugin.getLogger().info("Ingredients provided: " + ingredients.size());

        // Debug: log each ingredient
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack stack = ingredients.get(i);
            plugin.getLogger().info("  Ingredient " + i + ": " + stack.getType() + " x" + stack.getAmount());
            HItemStack hStack = HItemStack.getFromStack(stack);
            if (hStack != null) {
                String itemId = hStack.getItem().getKey().toString();
                plugin.getLogger().info("    -> HItemStack ID: " + itemId);
            } else {
                plugin.getLogger().info("    -> Not an HItemStack (vanilla item)");
            }
        }

        JobCharacterBridgeUtil.getCharacterJobRecord(player).thenAccept(characterJob -> {
            if (characterJob == null) {
                player.sendMessage(Component.text("You need a job to discover recipes!", NamedTextColor.RED));
                return;
            }

            String jobId = characterJob.job().getId();
            plugin.getLogger().info("Player job: " + jobId);

            // Use the new discovery method that returns ALL recipes with matching ingredients
            List<JobRecipe> discoveredRecipes = recipeManager.discoverAllRecipesWithIngredients(jobId, ingredients);

            if (discoveredRecipes.isEmpty()) {
                plugin.getLogger().info("No recipes discovered with these ingredients.");
                player.sendMessage(Component.text("No recipe discovered with these ingredients.", NamedTextColor.YELLOW));
                setResultSlot(null);
                return;
            }

            plugin.getLogger().info("Found " + discoveredRecipes.size() + " recipe(s) with matching ingredients");

            HCharacter character = characterJob.character();

            // Process all discovered recipes
            processMultipleRecipeDiscoveries(character, characterJob, discoveredRecipes);
        });
    }

    private void processMultipleRecipeDiscoveries(HCharacter character, CharacterJob characterJob, List<JobRecipe> recipes) {
        List<JobRecipe> newlyDiscovered = new ArrayList<>();
        List<JobRecipe> alreadyKnown = new ArrayList<>();
        List<JobRecipe> levelTooLow = new ArrayList<>();

        // First, categorize all recipes
        int[] processedCount = {0};
        for (JobRecipe recipe : recipes) {
            progressManager.hasDiscoveredRecipe(character.getCharacterID(), recipe.getId())
                .thenAccept(alreadyDiscovered -> {
                    if (alreadyDiscovered) {
                        alreadyKnown.add(recipe);
                    } else {
                        JobCharacterBridgeUtil.getJobLevel(characterJob).thenAccept(level -> {
                            if (level < recipe.getRequiredLevel()) {
                                levelTooLow.add(recipe);
                            } else {
                                newlyDiscovered.add(recipe);
                            }

                            processedCount[0]++;

                            // Once all recipes are processed, handle the results
                            if (processedCount[0] == recipes.size()) {
                                handleDiscoveryResults(character, characterJob, newlyDiscovered, alreadyKnown, levelTooLow);
                            }
                        });
                    }

                    if (alreadyDiscovered) {
                        processedCount[0]++;

                        // Once all recipes are processed, handle the results
                        if (processedCount[0] == recipes.size()) {
                            handleDiscoveryResults(character, characterJob, newlyDiscovered, alreadyKnown, levelTooLow);
                        }
                    }
                });
        }
    }

    private void handleDiscoveryResults(HCharacter character, CharacterJob characterJob,
                                       List<JobRecipe> newlyDiscovered,
                                       List<JobRecipe> alreadyKnown,
                                       List<JobRecipe> levelTooLow) {
        // Discover all new recipes
        long totalXp = 0;
        for (JobRecipe recipe : newlyDiscovered) {
            progressManager.discoverRecipe(character.getCharacterID(), recipe.getId());
            long discoveryXp = recipe.getBaseExperience() / 4;
            totalXp += discoveryXp;
        }

        // Send feedback to player
        if (!newlyDiscovered.isEmpty()) {
            if (newlyDiscovered.size() == 1) {
                player.sendMessage(Component.text("Recipe discovered: " + newlyDiscovered.get(0).getId(), NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Discovered " + newlyDiscovered.size() + " recipes:", NamedTextColor.GREEN));
                for (JobRecipe recipe : newlyDiscovered) {
                    player.sendMessage(Component.text("  - " + recipe.getId(), NamedTextColor.GREEN));
                }
            }

            if (totalXp > 0) {
                JobCharacterBridgeUtil.grantJobExperience(characterJob, totalXp);
                player.sendMessage(Component.text("+" + totalXp + " job experience!", NamedTextColor.AQUA));
            }

            // Show the first discovered recipe in the result slot
            showRecipeResult(newlyDiscovered.get(0));
        }

        if (!alreadyKnown.isEmpty()) {
            if (alreadyKnown.size() == 1) {
                player.sendMessage(Component.text("You already know this recipe!", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("You already know " + alreadyKnown.size() + " of these recipes.", NamedTextColor.YELLOW));
            }

            // If no new recipes, show the first known recipe
            if (newlyDiscovered.isEmpty() && !alreadyKnown.isEmpty()) {
                showRecipeResult(alreadyKnown.get(0));
            }
        }

        if (!levelTooLow.isEmpty()) {
            if (levelTooLow.size() == 1) {
                player.sendMessage(Component.text("Your job level is too low for this recipe! Required: " +
                    levelTooLow.get(0).getRequiredLevel(), NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text(levelTooLow.size() + " recipes require a higher job level.", NamedTextColor.RED));
            }
        }
    }

    private void showRecipeResult(JobRecipe recipe) {
        HItemStack resultStack = plugin.getLibrary().get(recipe.getResult().getItemId())
            .createStack(recipe.getResult().getAmount(), recipe.getResult().getItemLevel(),
                        recipe.getResult().getSocketPattern(), recipe.getResult().getRarity());

        if (resultStack != null) {
            setResultSlot(resultStack.getBukkitStack());
        }
    }

    private void setResultSlot(ItemStack item) {
        if (item == null) {
            ItemStack placeholder = createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Discovery Result",
                "Discovered recipes will appear here");
            inventory.setItem(RESULT_SLOT, placeholder);
        } else {
            inventory.setItem(RESULT_SLOT, item);
        }
    }

    private void clearSlots() {
        for (int slot : DISCOVERY_SLOTS) {
            ItemStack current = inventory.getItem(slot);
            if (current != null) {
                player.getInventory().addItem(current);
                inventory.setItem(slot, null);
            }
        }
        setResultSlot(null);
    }

    private List<ItemStack> getIngredients() {
        List<ItemStack> ingredients = new ArrayList<>();
        for (int slot : DISCOVERY_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null) {
                ingredients.add(item.clone());
            }
        }
        return ingredients;
    }

    private void updateDiscoveryState() {
    }

    private boolean isDiscoverySlot(int slot) {
        for (int discoverySlot : DISCOVERY_SLOTS) {
            if (slot == discoverySlot) {
                return true;
            }
        }
        return false;
    }

    private boolean isSeparatorSlot(int slot) {
        for (int separatorSlot : SEPARATOR_SLOTS) {
            if (slot == separatorSlot) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            clearSlots();
            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
        }
    }

    public void open() {
        player.openInventory(inventory);
    }
}
