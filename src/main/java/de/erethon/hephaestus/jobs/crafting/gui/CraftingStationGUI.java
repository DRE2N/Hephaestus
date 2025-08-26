package de.erethon.hephaestus.jobs.crafting.gui;

import de.erethon.hecate.data.HCharacter;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.jobs.JobCharacterBridgeUtil;
import de.erethon.hephaestus.jobs.crafting.JobRecipe;
import de.erethon.hephaestus.jobs.crafting.PlayerCraftingProgress;
import de.erethon.hephaestus.jobs.crafting.RecipeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CraftingStationGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final RecipeManager recipeManager;
    private final PlayerCraftingProgress progressManager;
    private final Player player;
    private final Inventory inventory;

    private List<JobRecipe> availableRecipes = new ArrayList<>();
    private int currentPage = 0;
    private JobRecipe selectedRecipe = null;
    private BukkitTask craftingTask = null;

    private JobRecipe currentlyCrafting = null;
    private int craftingProgress = 0;
    private int totalCraftingTime = 0;
    private BukkitTask progressTask = null;

    private static final int[] CRAFTING_SLOTS = {1, 2, 3, 10, 11, 12, 19, 20, 21};
    private static final int CRAFT_RESULT_SLOT = 16;
    private static final int CRAFT_BUTTON_SLOT = 25;
    private static final int DISCOVERY_BUTTON_SLOT = 49;

    private static final int[] ACTUAL_RECIPE_SLOTS = {
        36, 37, 38, 39, 40, 41, 42, 43, 44,
        45, 46, 47, 48, 49, 50, 51, 52, 53
    };

    private static final int RECIPES_PER_PAGE = 18;
    private static final int PREV_PAGE_SLOT = 33;
    private static final int NEXT_PAGE_SLOT = 35;

    public CraftingStationGUI(Hephaestus plugin, Player player) {
        this.plugin = plugin;
        this.recipeManager = plugin.getRecipeManager();
        this.progressManager = plugin.getPlayerCraftingProgress();
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54,
            Component.text("Crafting Station", NamedTextColor.DARK_GREEN));

        setupInterface();
        loadPlayerRecipes();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupInterface() {
        ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, background);
        }

        for (int slot : CRAFTING_SLOTS) {
            inventory.setItem(slot, null);
        }

        setupDiscoveryButton();
        setupCraftButton();
        setupResultSlot();
        setupNavigationButtons();
    }

    private void setupDiscoveryButton() {
        ItemStack discoveryButton = createTranslatableGuiItem(Material.ENCHANTING_TABLE,
            Component.translatable("hephaestus.crafting.gui.discovery.title"),
            Component.translatable("hephaestus.crafting.gui.discovery.lore1"),
            Component.translatable("hephaestus.crafting.gui.discovery.lore2"));
        inventory.setItem(DISCOVERY_BUTTON_SLOT, discoveryButton);
    }

    private void setupCraftButton() {
        ItemStack craftButton = createTranslatableGuiItem(Material.ANVIL,
            Component.translatable("hephaestus.crafting.gui.craft.title"),
            Component.translatable("hephaestus.crafting.gui.craft.lore1"),
            Component.translatable("hephaestus.crafting.gui.craft.lore2"));
        inventory.setItem(CRAFT_BUTTON_SLOT, craftButton);
    }

    private void setupResultSlot() {
        ItemStack resultPlaceholder = createTranslatableGuiItem(Material.LIME_STAINED_GLASS_PANE,
            Component.translatable("hephaestus.crafting.gui.result.title"),
            Component.translatable("hephaestus.crafting.gui.result.lore"));
        inventory.setItem(CRAFT_RESULT_SLOT, resultPlaceholder);
    }

    private void setupNavigationButtons() {
        ItemStack prevButton = createTranslatableGuiItem(Material.ARROW, Component.translatable("hephaestus.crafting.gui.nav.previous"));
        ItemStack nextButton = createTranslatableGuiItem(Material.ARROW, Component.translatable("hephaestus.crafting.gui.nav.next"));
        inventory.setItem(PREV_PAGE_SLOT, prevButton);
        inventory.setItem(NEXT_PAGE_SLOT, nextButton);
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

    private void loadPlayerRecipes() {
        JobCharacterBridgeUtil.getCharacterJobRecord(player).thenAccept(characterJob -> {
            if (characterJob == null) {
                player.sendMessage(Component.text("You need a job to access recipes!", NamedTextColor.RED));
                return;
            }

            HCharacter character = characterJob.character();
            String jobId = characterJob.job().getId();

            CompletableFuture<Set<String>> discoveredFuture = progressManager.getDiscoveredRecipes(character.getCharacterID());
            CompletableFuture<Integer> levelFuture = JobCharacterBridgeUtil.getJobLevel(characterJob);

            CompletableFuture.allOf(discoveredFuture, levelFuture).thenRun(() -> {
                try {
                    Set<String> discovered = discoveredFuture.get();
                    int level = levelFuture.get();

                    availableRecipes = recipeManager.getRecipesForJob(jobId).stream()
                        .filter(recipe -> discovered.contains(recipe.getId()))
                        .filter(recipe -> recipe.getRequiredLevel() <= level)
                        .sorted(Comparator.comparing(JobRecipe::getRequiredLevel))
                        .toList();

                    updateRecipeDisplay();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load player recipes: " + e.getMessage());
                }
            });
        });
    }

    private void updateRecipeDisplay() {
        for (int slot : ACTUAL_RECIPE_SLOTS) {
            ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
            inventory.setItem(slot, background);
        }

        int startIndex = currentPage * RECIPES_PER_PAGE;
        int endIndex = Math.min(startIndex + RECIPES_PER_PAGE, availableRecipes.size());

        for (int i = startIndex; i < endIndex; i++) {
            JobRecipe recipe = availableRecipes.get(i);
            ItemStack recipeItem = createRecipeDisplayItem(recipe);

            int slotIndex = i - startIndex;
            if (slotIndex < ACTUAL_RECIPE_SLOTS.length) {
                inventory.setItem(ACTUAL_RECIPE_SLOTS[slotIndex], recipeItem);
            }
        }

        updateNavigationButtons();
    }

    private ItemStack createRecipeDisplayItem(JobRecipe recipe) {
        HItemStack resultStack = plugin.getLibrary().get(recipe.getResult().getItemId())
            .createStack(1, recipe.getResult().getItemLevel(),
                        recipe.getResult().getSocketPattern(), recipe.getResult().getRarity());

        ItemStack displayItem;
        if (resultStack != null) {
            displayItem = resultStack.getBukkitStack().clone();
        } else {
            displayItem = new ItemStack(Material.CRAFTING_TABLE);
        }

        ItemMeta meta = displayItem.getItemMeta();
        meta.displayName(Component.text(recipe.getId(), NamedTextColor.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Level: " + recipe.getRequiredLevel(), NamedTextColor.YELLOW));
        lore.add(Component.text("XP: " + recipe.getBaseExperience(), NamedTextColor.AQUA));
        lore.add(Component.text("Time: " + (recipe.getCraftingTime() / 20.0) + "s", NamedTextColor.GREEN));
        lore.add(Component.text(""));
        lore.add(Component.text("Ingredients:", NamedTextColor.GRAY));

        for (var ingredient : recipe.getIngredients()) {
            lore.add(Component.text("• " + ingredient.getAmount() + "x " + ingredient.getItemId(), NamedTextColor.WHITE));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("Click to select", NamedTextColor.GREEN));
        meta.lore(lore);
        displayItem.setItemMeta(meta);

        return displayItem;
    }

    private void updateNavigationButtons() {
        ItemStack prevButton = inventory.getItem(PREV_PAGE_SLOT);
        if (prevButton != null) {
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (currentPage > 0) {
                prevMeta.displayName(Component.text("Previous Page", NamedTextColor.YELLOW));
            } else {
                prevMeta.displayName(Component.text("Previous Page", NamedTextColor.DARK_GRAY));
            }
            prevButton.setItemMeta(prevMeta);
        }

        ItemStack nextButton = inventory.getItem(NEXT_PAGE_SLOT);
        if (nextButton != null) {
            ItemMeta nextMeta = nextButton.getItemMeta();
            int maxPages = Math.max(0, (availableRecipes.size() - 1) / RECIPES_PER_PAGE);
            if (currentPage < maxPages) {
                nextMeta.displayName(Component.text("Next Page", NamedTextColor.YELLOW));
            } else {
                nextMeta.displayName(Component.text("Next Page", NamedTextColor.DARK_GRAY));
            }
            nextButton.setItemMeta(nextMeta);
        }
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
            if (slot == PREV_PAGE_SLOT && currentPage > 0) {
                event.setCancelled(true);
                currentPage--;
                updateRecipeDisplay();
            } else if (slot == NEXT_PAGE_SLOT) {
                event.setCancelled(true);
                int maxPages = Math.max(0, (availableRecipes.size() - 1) / RECIPES_PER_PAGE);
                if (currentPage < maxPages) {
                    currentPage++;
                    updateRecipeDisplay();
                }
            } else if (slot == DISCOVERY_BUTTON_SLOT) {
                event.setCancelled(true);
                new RecipeDiscoveryGUI(plugin, player).open();
            } else if (slot == CRAFT_BUTTON_SLOT) {
                event.setCancelled(true);
                if (selectedRecipe != null) {
                    startCrafting();
                }
            } else if (slot == CRAFT_RESULT_SLOT) {
                event.setCancelled(true);
                // Result slot is display only
            } else if (isCraftingSlot(slot)) {
                // Allow normal item placement/removal in crafting slots
                Bukkit.getScheduler().runTaskLater(plugin, this::updateCraftingState, 1L);
            } else if (isRecipeSlot(slot)) {
                event.setCancelled(true);
                selectRecipeFromSlot(slot);
            } else {
                event.setCancelled(true);
            }
        }
    }

    private boolean isCraftingSlot(int slot) {
        for (int craftingSlot : CRAFTING_SLOTS) {
            if (slot == craftingSlot) {
                return true;
            }
        }
        return false;
    }

    private boolean isRecipeSlot(int slot) {
        for (int recipeSlot : ACTUAL_RECIPE_SLOTS) {
            if (slot == recipeSlot) {
                return true;
            }
        }
        return false;
    }

    private void selectRecipeFromSlot(int slot) {
        for (int i = 0; i < ACTUAL_RECIPE_SLOTS.length; i++) {
            if (ACTUAL_RECIPE_SLOTS[i] == slot) {
                int recipeIndex = currentPage * RECIPES_PER_PAGE + i;
                if (recipeIndex < availableRecipes.size()) {
                    selectRecipe(availableRecipes.get(recipeIndex));
                }
                break;
            }
        }
    }

    private void selectRecipe(JobRecipe recipe) {
        this.selectedRecipe = recipe;
        HItemStack resultStack = plugin.getLibrary().get(recipe.getResult().getItemId())
            .createStack(recipe.getResult().getAmount(), recipe.getResult().getItemLevel(),
                        recipe.getResult().getSocketPattern(), recipe.getResult().getRarity());

        if (resultStack != null) {
            inventory.setItem(CRAFT_RESULT_SLOT, resultStack.getBukkitStack());
        }
        updateCraftButton();
    }

    private void updateCraftButton() {
        if (selectedRecipe == null) {
            setupCraftButton();
            return;
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Recipe: " + selectedRecipe.getId(), NamedTextColor.YELLOW));
        lore.add(Component.text(""));

        boolean canCraft = true;
        for (var ingredient : selectedRecipe.getIngredients()) {
            int required = ingredient.getAmount();
            int available = countItemsInCraftingSlots(ingredient.getItemId());

            Component ingredientLine;
            if (available >= required) {
                ingredientLine = Component.text("✓ " + required + "x " + ingredient.getItemId(), NamedTextColor.GREEN);
            } else {
                ingredientLine = Component.text("✗ " + available + "/" + required + "x " + ingredient.getItemId(), NamedTextColor.RED);
                canCraft = false;
            }
            lore.add(ingredientLine);
        }

        if (!canCraft) {
            lore.add(Component.text(""));
            lore.add(Component.text("Place ingredients in crafting slots!", NamedTextColor.RED));
        }

        ItemStack craftButton = createGuiItem(Material.ANVIL, canCraft ? "Craft Item" : "Missing Ingredients");
        ItemMeta meta = craftButton.getItemMeta();
        meta.lore(lore);
        craftButton.setItemMeta(meta);
        inventory.setItem(CRAFT_BUTTON_SLOT, craftButton);
    }

    private int countItemsInCraftingSlots(String itemId) {
        int count = 0;
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;

            HItemStack hStack = HItemStack.getFromStack(item);
            if (hStack != null && hStack.getItem().getKey().toString().equals(itemId)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void updateCraftingState() {
        if (selectedRecipe != null) {
            updateCraftButton();
        }
    }

    private void startCrafting() {
        if (selectedRecipe == null) return;
        if (currentlyCrafting != null) {
            player.sendMessage(Component.text("Already crafting!", NamedTextColor.RED));
            return;
        }

        if (!hasRequiredIngredients(selectedRecipe)) {
            player.sendMessage(Component.text("You don't have the required ingredients!", NamedTextColor.RED));
            return;
        }

        if (!consumeIngredients(selectedRecipe)) {
            player.sendMessage(Component.text("Failed to consume ingredients!", NamedTextColor.RED));
            return;
        }

        currentlyCrafting = selectedRecipe;
        totalCraftingTime = selectedRecipe.getCraftingTime();
        craftingProgress = 0;

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.0f);
        player.sendMessage(Component.text("Crafting started...", NamedTextColor.YELLOW));

        int updateInterval = Math.min(totalCraftingTime / 20, 20);

        progressTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            craftingProgress += updateInterval;

            if (craftingProgress >= totalCraftingTime) {
                // Crafting complete
                craftingProgress = totalCraftingTime;
                updateProgressDisplay(100);

                if (progressTask != null) {
                    progressTask.cancel();
                    progressTask = null;
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    completeCrafting(currentlyCrafting);
                }, 1L);
            } else {
                int percentage = (int) ((double) craftingProgress / totalCraftingTime * 100);
                updateProgressDisplay(percentage);
                if (craftingProgress % 40 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.3f, 1.0f);
                }
            }
        }, 0L, updateInterval);

        craftingTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (currentlyCrafting != null) {
                completeCrafting(currentlyCrafting);
            }
        }, totalCraftingTime + 5L);
    }

    private void updateProgressDisplay(int percentage) {
        if (currentlyCrafting == null) return;
        String progressBar = createProgressBar(percentage);
        String timeRemaining = String.format("%.1f", (totalCraftingTime - craftingProgress) / 20.0);
        Component title = Component.text(progressBar + " " + timeRemaining + "s", NamedTextColor.YELLOW);
        GUIUtils.updateTitle(player, title);
    }

    private String createProgressBar(int percentage) {
        int barLength = 10;
        int filledBars = (int) (percentage / 100.0 * barLength);

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        bar.append("]");
        bar.append(" ").append(percentage).append("%");

        return bar.toString();
    }

    private boolean hasRequiredIngredients(JobRecipe recipe) {
        for (var ingredient : recipe.getIngredients()) {
            if (countItemsInCraftingSlots(ingredient.getItemId()) < ingredient.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private boolean consumeIngredients(JobRecipe recipe) {
        Map<String, Integer> toConsume = new HashMap<>();
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isConsumeOnCraft()) {
                toConsume.put(ingredient.getItemId(), ingredient.getAmount());
            }
        }

        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;

            HItemStack hStack = HItemStack.getFromStack(item);
            if (hStack != null) {
                String itemId = hStack.getItem().getKey().toString();
                if (toConsume.containsKey(itemId)) {
                    int needed = toConsume.get(itemId);
                    int available = item.getAmount();

                    if (available >= needed) {
                        item.setAmount(available - needed);
                        toConsume.remove(itemId);
                        if (item.getAmount() == 0) {
                            inventory.setItem(slot, null);
                        }
                    } else {
                        toConsume.put(itemId, needed - available);
                        inventory.setItem(slot, null);
                    }
                }
            }
        }

        return toConsume.isEmpty();
    }

    private void completeCrafting(JobRecipe recipe) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        currentlyCrafting = null;
        craftingProgress = 0;
        totalCraftingTime = 0;

        if (craftingTask != null && !craftingTask.isCancelled()) {
            craftingTask.cancel();
            craftingTask = null;
        }
        if (progressTask != null && !progressTask.isCancelled()) {
            progressTask.cancel();
            progressTask = null;
        }

        GUIUtils.updateTitle(player, Component.text("Crafting Station", NamedTextColor.DARK_GREEN));

        JobCharacterBridgeUtil.getCharacterJobRecord(player).thenAccept(characterJob -> {
            if (characterJob == null) return;

            HCharacter character = characterJob.character();

            progressManager.getCraftCount(character.getCharacterID(), recipe.getId())
                    .thenCompose(craftCount -> {
                        long xp = recipe.calculateExperience(craftCount);

                        JobCharacterBridgeUtil.grantJobExperience(characterJob, xp);

                        return progressManager.recordCraft(character.getCharacterID(), recipe.getId())
                                .thenApply(newCount -> xp);
                    })
                    .thenAccept(xp -> {
                        HItemStack resultStack = plugin.getLibrary().get(recipe.getResult().getItemId())
                                .createStack(recipe.getResult().getAmount(), recipe.getResult().getItemLevel(),
                                        recipe.getResult().getSocketPattern(), recipe.getResult().getRarity());

                        if (resultStack != null) {
                            player.getInventory().addItem(resultStack.getBukkitStack());
                        }

                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
                        // TODO: We seem to be clearing the GUI until the next click here
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (selectedRecipe != null) {
                                selectRecipe(selectedRecipe);
                            }
                        });
                    });
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            for (int slot : CRAFTING_SLOTS) {
                ItemStack item = inventory.getItem(slot);
                if (item != null) {
                    player.getInventory().addItem(item);
                }
            }

            if (craftingTask != null && !craftingTask.isCancelled()) {
                craftingTask.cancel();
                GUIUtils.updateTitle(player, Component.text("Crafting Station", NamedTextColor.DARK_GREEN));
            }

            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
        }
    }

    public void open() {
        player.openInventory(inventory);
    }
}
