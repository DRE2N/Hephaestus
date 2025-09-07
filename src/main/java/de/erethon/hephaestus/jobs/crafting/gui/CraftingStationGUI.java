package de.erethon.hephaestus.jobs.crafting.gui;

import de.erethon.hecate.data.HCharacter;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.jobs.JobCharacterBridgeUtil;
import de.erethon.hephaestus.jobs.crafting.JobRecipe;
import de.erethon.hephaestus.jobs.crafting.PlayerCraftingProgress;
import de.erethon.hephaestus.jobs.crafting.RecipeManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public class CraftingStationGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final RecipeManager recipeManager;
    private final PlayerCraftingProgress progressManager;
    private final Player player;
    private final Inventory inventory;

    private List<JobRecipe> availableRecipes = new ArrayList<>();
    private int currentPage = 0;
    private JobRecipe selectedRecipe = null;
    private int craftQuantity = 1; // New field for bulk crafting
    private BukkitTask craftingTask = null;

    private JobRecipe currentlyCrafting = null;
    private int craftingProgress = 0;
    private int totalCraftingTime = 0;
    private BukkitTask progressTask = null;

    private static final int[] CRAFTING_SLOTS = {1, 2, 3, 10, 11, 12, 19, 20, 21};
    private static final int CRAFT_RESULT_SLOT = 16;
    private static final int CRAFT_BUTTON_SLOT = 25;
    private static final int DISCOVERY_BUTTON_SLOT = 49;

    // New slots for quantity controls
    private static final int QUANTITY_DECREASE_SLOT = 6;
    private static final int QUANTITY_DISPLAY_SLOT = 7;
    private static final int QUANTITY_INCREASE_SLOT = 8;

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
        setupQuantityControls();
        setupNavigationButtons();
    }

    private void setupQuantityControls() {
        // Decrease button
        ItemStack decreaseButton = createTranslatableGuiItem(Material.RED_CONCRETE,
            Component.translatable("hephaestus.crafting.gui.quantity.decrease.title"),
            Component.translatable("hephaestus.crafting.gui.quantity.decrease.lore1"),
            Component.translatable("hephaestus.crafting.gui.quantity.decrease.lore2"));
        inventory.setItem(QUANTITY_DECREASE_SLOT, decreaseButton);

        // Increase button
        ItemStack increaseButton = createTranslatableGuiItem(Material.GREEN_CONCRETE,
            Component.translatable("hephaestus.crafting.gui.quantity.increase.title"),
            Component.translatable("hephaestus.crafting.gui.quantity.increase.lore1"),
            Component.translatable("hephaestus.crafting.gui.quantity.increase.lore2"));
        inventory.setItem(QUANTITY_INCREASE_SLOT, increaseButton);

        updateQuantityDisplay();
    }

    private void updateQuantityDisplay() {
        ItemStack quantityDisplay = new ItemStack(Material.PAPER, 1);
        quantityDisplay.editMeta(meta -> {
            meta.displayName(Component.translatable("hephaestus.crafting.gui.quantity.display.title",
                Component.text(craftQuantity, NamedTextColor.YELLOW)).color(NamedTextColor.WHITE));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.translatable("hephaestus.crafting.gui.quantity.display.lore1"));
            if (selectedRecipe != null) {
                lore.add(Component.text(""));
                lore.add(Component.translatable("hephaestus.crafting.gui.quantity.display.total_ingredients"));
                for (var ingredient : selectedRecipe.getIngredients()) {
                    int totalNeeded = ingredient.getAmount() * craftQuantity;
                    lore.add(Component.text("• " + totalNeeded + "x " + ingredient.getItemId(), NamedTextColor.GRAY));
                }
            }
            meta.lore(lore);
        });

        // Use data component to show quantity visually
        quantityDisplay.setData(DataComponentTypes.MAX_STACK_SIZE, Math.max(1, Math.min(99, craftQuantity)));
        quantityDisplay.setAmount(Math.max(1, Math.min(99, craftQuantity)));

        inventory.setItem(QUANTITY_DISPLAY_SLOT, quantityDisplay);
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
            } else if (slot == QUANTITY_DECREASE_SLOT) {
                event.setCancelled(true);
                adjustQuantity(event.getClick() == ClickType.SHIFT_LEFT ? -10 : -1);
            } else if (slot == QUANTITY_INCREASE_SLOT) {
                event.setCancelled(true);
                adjustQuantity(event.getClick() == ClickType.SHIFT_LEFT ? 10 : 1);
            } else if (slot == QUANTITY_DISPLAY_SLOT) {
                event.setCancelled(true);
                // Display slot is read-only
            } else if (slot == DISCOVERY_BUTTON_SLOT) {
                event.setCancelled(true);
                new RecipeDiscoveryGUI(plugin, player).open();
            } else if (slot == CRAFT_BUTTON_SLOT) {
                event.setCancelled(true);
                if (selectedRecipe != null && canCraftQuantity()) {
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

    private void adjustQuantity(int change) {
        int newQuantity = Math.max(1, Math.min(99, craftQuantity + change));
        if (newQuantity != craftQuantity) {
            craftQuantity = newQuantity;
            updateQuantityDisplay();
            updateCraftButton();
            updateResultDisplay();

            // Play sound feedback
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }

    private void updateResultDisplay() {
        if (selectedRecipe != null) {
            HItemStack resultStack = plugin.getLibrary().get(selectedRecipe.getResult().getItemId())
                .createStack(selectedRecipe.getResult().getAmount() * craftQuantity, selectedRecipe.getResult().getItemLevel(),
                            selectedRecipe.getResult().getSocketPattern(), selectedRecipe.getResult().getRarity());

            if (resultStack != null) {
                ItemStack displayStack = resultStack.getBukkitStack();
                displayStack.setAmount(Math.max(1, Math.min(99, selectedRecipe.getResult().getAmount() * craftQuantity)));
                inventory.setItem(CRAFT_RESULT_SLOT, displayStack);
            }
        }
    }

    private boolean canCraftQuantity() {
        if (selectedRecipe == null) return false;

        for (var ingredient : selectedRecipe.getIngredients()) {
            int totalRequired = ingredient.getAmount() * craftQuantity;
            int available = countItemsInCraftingSlots(ingredient.getItemId());
            if (available < totalRequired) {
                return false;
            }
        }
        return true;
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
        this.craftQuantity = 1; // Reset quantity when selecting new recipe

        HItemStack resultStack = plugin.getLibrary().get(recipe.getResult().getItemId())
            .createStack(recipe.getResult().getAmount() * craftQuantity, recipe.getResult().getItemLevel(),
                        recipe.getResult().getSocketPattern(), recipe.getResult().getRarity());

        if (resultStack != null) {
            ItemStack displayStack = resultStack.getBukkitStack();
            displayStack.setAmount(Math.max(1, Math.min(99, recipe.getResult().getAmount() * craftQuantity)));
            inventory.setItem(CRAFT_RESULT_SLOT, displayStack);
        }

        updateQuantityDisplay();
        updateCraftButton();
    }

    private void updateCraftButton() {
        if (selectedRecipe == null) {
            setupCraftButton();
            return;
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Recipe: " + selectedRecipe.getId(), NamedTextColor.YELLOW));
        lore.add(Component.text("Quantity: " + craftQuantity + "x", NamedTextColor.AQUA));
        lore.add(Component.text(""));

        boolean canCraft = true;
        for (var ingredient : selectedRecipe.getIngredients()) {
            int requiredPerCraft = ingredient.getAmount();
            int totalRequired = requiredPerCraft * craftQuantity;
            int available = countItemsInCraftingSlots(ingredient.getItemId());

            Component ingredientLine;
            if (available >= totalRequired) {
                ingredientLine = Component.text("✓ " + totalRequired + "x " + ingredient.getItemId() +
                    " (" + requiredPerCraft + " each)", NamedTextColor.GREEN);
            } else {
                ingredientLine = Component.text("✗ " + available + "/" + totalRequired + "x " + ingredient.getItemId() +
                    " (" + requiredPerCraft + " each)", NamedTextColor.RED);
                canCraft = false;
            }
            lore.add(ingredientLine);
        }

        if (!canCraft) {
            lore.add(Component.text(""));
            lore.add(Component.text("Place ingredients in crafting slots!", NamedTextColor.RED));
        } else {
            lore.add(Component.text(""));
            lore.add(Component.text("Total crafting time: " +
                ((selectedRecipe.getCraftingTime() * craftQuantity) / 20.0) + "s", NamedTextColor.YELLOW));
        }

        ItemStack craftButton = createGuiItem(Material.ANVIL, canCraft ? "Craft " + craftQuantity + "x Items" : "Missing Ingredients");
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
        if (selectedRecipe == null || !canCraftQuantity()) {
            player.sendMessage(Component.text("Cannot craft: missing ingredients!", NamedTextColor.RED));
            return;
        }

        if (currentlyCrafting != null) {
            player.sendMessage(Component.text("Already crafting something!", NamedTextColor.RED));
            return;
        }

        // Consume ingredients for the total quantity
        if (!consumeIngredients()) {
            player.sendMessage(Component.text("Failed to consume ingredients!", NamedTextColor.RED));
            return;
        }

        currentlyCrafting = selectedRecipe;
        craftingProgress = 0;
        totalCraftingTime = selectedRecipe.getCraftingTime() * craftQuantity;

        player.sendMessage(Component.text("Started crafting " + craftQuantity + "x " + selectedRecipe.getId() + "...", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

        progressTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateCraftingProgress, 0L, 1L);
    }

    private void updateCraftingProgress() {
        craftingProgress++;

        int percentage = (int) ((double) craftingProgress / totalCraftingTime * 100);
        updateProgressDisplay(percentage);

        if (craftingProgress >= totalCraftingTime) {
            completeCrafting(currentlyCrafting);
        }
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

    private boolean consumeIngredients() {
        Map<String, Integer> requiredAmounts = new HashMap<>();
        for (var ingredient : selectedRecipe.getIngredients()) {
            requiredAmounts.put(ingredient.getItemId(), ingredient.getAmount() * craftQuantity);
        }

        // Check if we have enough of each ingredient
        Map<String, Integer> availableAmounts = new HashMap<>();
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;

            HItemStack hStack = HItemStack.getFromStack(item);
            if (hStack != null) {
                String itemId = hStack.getItem().getKey().toString();
                availableAmounts.put(itemId, availableAmounts.getOrDefault(itemId, 0) + item.getAmount());
            }
        }

        for (Map.Entry<String, Integer> required : requiredAmounts.entrySet()) {
            if (availableAmounts.getOrDefault(required.getKey(), 0) < required.getValue()) {
                return false;
            }
        }

        // Actually consume the ingredients
        Map<String, Integer> toConsume = new HashMap<>(requiredAmounts);
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;

            HItemStack hStack = HItemStack.getFromStack(item);
            if (hStack != null) {
                String itemId = hStack.getItem().getKey().toString();
                if (toConsume.containsKey(itemId) && toConsume.get(itemId) > 0) {
                    int consumeAmount = Math.min(item.getAmount(), toConsume.get(itemId));
                    toConsume.put(itemId, toConsume.get(itemId) - consumeAmount);

                    if (item.getAmount() <= consumeAmount) {
                        inventory.setItem(slot, null);
                    } else {
                        item.setAmount(item.getAmount() - consumeAmount);
                    }
                }
            }
        }

        return true;
    }

    private void completeCrafting(JobRecipe recipe) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        int completedQuantity = craftQuantity; // Store the quantity that was crafted
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
                        // Calculate XP for the total quantity crafted
                        long totalXp = IntStream.range(0, completedQuantity).mapToLong(i -> recipe.calculateExperience(craftCount + i)).sum();

                        JobCharacterBridgeUtil.grantJobExperience(characterJob, totalXp);

                        // Record the craft count increase
                        return progressManager.recordCraftMultiple(character.getCharacterID(), recipe.getId(), completedQuantity)
                                .thenApply(newCount -> totalXp);
                    })
                    .thenAccept(totalXp -> {
                        // Create the result items with proper quantity
                        int totalResultAmount = recipe.getResult().getAmount() * completedQuantity;

                        // Split into stacks if necessary (max stack size considerations)
                        while (totalResultAmount > 0) {
                            HItemStack resultStack = plugin.getLibrary().get(recipe.getResult().getItemId())
                                    .createStack(Math.min(totalResultAmount, 64), recipe.getResult().getItemLevel(),
                                            recipe.getResult().getSocketPattern(), recipe.getResult().getRarity());

                            if (resultStack != null) {
                                ItemStack bukkitStack = resultStack.getBukkitStack();
                                bukkitStack.setAmount(Math.min(totalResultAmount, 64));
                                player.getInventory().addItem(bukkitStack);
                                totalResultAmount -= bukkitStack.getAmount();
                            } else {
                                break;
                            }
                        }

                        player.sendMessage(Component.text("Crafted " + completedQuantity + "x " + recipe.getId() +
                            " (+" + totalXp + " XP)", NamedTextColor.GREEN));
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);

                        // Reset the crafting UI
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            craftQuantity = 1; // Reset to 1 after crafting
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

    public void open() {
        player.openInventory(inventory);
    }
}
