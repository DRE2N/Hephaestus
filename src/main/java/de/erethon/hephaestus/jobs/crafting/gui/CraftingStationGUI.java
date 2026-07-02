package de.erethon.hephaestus.jobs.crafting.gui;

import de.erethon.hecate.data.HCharacter;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.events.HJobCraftItemEvent;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.items.HItemUtil;
import de.erethon.hephaestus.jobs.JobCharacterBridgeUtil;
import de.erethon.hephaestus.jobs.crafting.JobRecipe;
import de.erethon.hephaestus.jobs.crafting.PlayerCraftingProgress;
import de.erethon.hephaestus.jobs.crafting.RecipeIngredient;
import de.erethon.hephaestus.jobs.crafting.RecipeManager;
import de.erethon.hephaestus.jobs.crafting.RecipeResult;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
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
    private int craftQuantity = 1;
    private BukkitTask craftingTask = null;

    private JobRecipe currentlyCrafting = null;
    private RecipeResult craftingResult = null;
    private int craftingProgress = 0;
    private int totalCraftingTime = 0;
    private BukkitTask progressTask = null;

    // Recipe display slots - 4 rows x 9 columns (36 slots total)
    private static final int[] ACTUAL_RECIPE_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    // Row 5: Control buttons (slots 36-44)
    private static final int QUANTITY_DECREASE_SLOT = 36;
    private static final int QUANTITY_DISPLAY_SLOT = 37;
    private static final int QUANTITY_INCREASE_SLOT = 38;
    private static final int CRAFT_BUTTON_SLOT = 40;
    private static final int CRAFT_RESULT_SLOT = 42;

    // Row 6: Navigation and utility buttons (slots 45-53)
    private static final int DISCOVERY_BUTTON_SLOT = 45;
    private static final int PREV_PAGE_SLOT = 48;
    private static final int NEXT_PAGE_SLOT = 50;

    private static final int RECIPES_PER_PAGE = 36;

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
        // Fill all slots with dummy items first
        ItemStack dummy = getDummyItem();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, dummy);
        }

        // Clear recipe display slots (top 4 rows)
        for (int slot : ACTUAL_RECIPE_SLOTS) {
            inventory.setItem(slot, null);
        }

        setupDiscoveryButton();
        setupCraftButton();
        setupResultSlot();
        setupQuantityControls();
        setupNavigationButtons();
    }

    private static ItemStack getDummyItem() {
        ItemStack dummy = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        //dummy.setData(DataComponentTypes.ITEM_MODEL, NamespacedKey.minecraft("air"));
        dummy.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true));
        return dummy;
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

                    if (ingredient.isChoice()) {
                        String choiceName = ingredient.getChoice().getChoiceId();
                        lore.add(Component.text("• " + totalNeeded + "x " + choiceName + " (any)", NamedTextColor.GRAY));
                    } else {
                        String itemName = getItemDisplayName(ingredient.getItemId());
                        lore.add(Component.text("• " + totalNeeded + "x " + itemName, NamedTextColor.GRAY));
                    }
                }
            }
            meta.lore(lore);
        });

        // Use data component to show quantity visually
        quantityDisplay.setData(DataComponentTypes.MAX_STACK_SIZE, Math.max(1, Math.min(99, craftQuantity)));
        quantityDisplay.setAmount(Math.max(1, Math.min(99, craftQuantity)));

        inventory.setItem(QUANTITY_DISPLAY_SLOT, quantityDisplay);
    }

    /**
     * Helper method to get the display name of an item from its ID.
     */
    private String getItemDisplayName(String itemId) {
        if (itemId == null) {
            return "unknown";
        }
        String id = itemId.replace(":", ".");
        Component displayName = GlobalTranslator.render(Component.translatable("hephaestus.item." + id + ".name"), player.locale());
        return PlainTextComponentSerializer.plainText().serialize(displayName);
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
                runSync(() -> player.sendMessage(Component.text("You need a job to access recipes!", NamedTextColor.RED)));
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

                    List<JobRecipe> allJobRecipes = recipeManager.getRecipesForJob(jobId);
                    List<JobRecipe> loadedRecipes = allJobRecipes.stream()
                            .filter(recipe -> discovered.contains(recipe.getId()) && recipe.getRequiredLevel() <= level)
                            .sorted(Comparator.comparing(JobRecipe::getRequiredLevel).thenComparing(JobRecipe::getId))
                            .toList();

                    runSync(() -> {
                        availableRecipes = loadedRecipes;
                        if (currentPage > Math.max(0, (availableRecipes.size() - 1) / RECIPES_PER_PAGE)) {
                            currentPage = 0;
                        }
                        updateRecipeDisplay();
                    });
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load player recipes: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });
    }

    private void updateRecipeDisplay() {
        // Clear all recipe slots
        for (int slot : ACTUAL_RECIPE_SLOTS) {
            inventory.setItem(slot, null);
        }

        int startIndex = currentPage * RECIPES_PER_PAGE;
        int endIndex = Math.min(startIndex + RECIPES_PER_PAGE, availableRecipes.size());

        for (int i = startIndex; i < endIndex; i++) {
            JobRecipe recipe = availableRecipes.get(i);
            int slotIndex = i - startIndex;
            if (slotIndex >= ACTUAL_RECIPE_SLOTS.length) continue;
            try {
                ItemStack recipeItem = createRecipeDisplayItem(recipe);
                inventory.setItem(ACTUAL_RECIPE_SLOTS[slotIndex], recipeItem);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create display item for recipe '" + recipe.getId() + "': " + e.getMessage());
                ItemStack errorItem = createTranslatableGuiItem(Material.BARRIER,
                    Component.text(recipe.getId(), NamedTextColor.RED),
                    Component.text("Configuration error - check server logs", NamedTextColor.GRAY));
                inventory.setItem(ACTUAL_RECIPE_SLOTS[slotIndex], errorItem);
            }
        }

        updateNavigationButtons();
    }

    private ItemStack createRecipeDisplayItem(JobRecipe recipe) {
        ItemStack displayItem;

        // Use getDisplayResult() to handle both fixed and dynamic recipes
        RecipeResult displayResult = recipe.getDisplayResult();

        if (displayResult != null && displayResult.getItemId() != null) {
            var hItem = plugin.getLibrary().get(displayResult.getItemId());
            if (hItem != null) {
                HItemStack resultStack = hItem.createStack(1, displayResult.getItemLevel(),
                                displayResult.getSocketPattern(), displayResult.getRarity());
                if (resultStack != null) {
                    displayItem = resultStack.getBukkitStack().clone();
                } else {
                    displayItem = createInvalidRecipeItem(recipe, "Result stack could not be created");
                }
            } else {
                displayItem = HItemUtil.createItemStack(displayResult.getItemId(), 1);
                if (displayItem == null || displayItem.getType().isAir()) {
                    plugin.getLogger().warning("Item '" + displayResult.getItemId() + "' referenced in recipe '" + recipe.getId() + "' cannot be created");
                    displayItem = createInvalidRecipeItem(recipe, "Unknown result item: " + displayResult.getItemId());
                }
            }
        } else {
            plugin.getLogger().warning("Recipe '" + recipe.getId() + "' has no valid display result - check configuration");
            displayItem = createInvalidRecipeItem(recipe, "Missing result");
        }

        if (displayResult != null && displayResult.getItemId() != null && plugin.getLibrary().get(displayResult.getItemId()) != null) {
            String resultId = displayResult.getItemId().replace(":", ".");
            displayItem.setData(DataComponentTypes.ITEM_NAME, Component.translatable("hephaestus.item." + resultId + ".name"));
        } else if (displayItem.getType() == Material.BARRIER) {
            displayItem.setData(DataComponentTypes.ITEM_NAME,
                Component.text("[Invalid Recipe: " + recipe.getId() + "]", NamedTextColor.RED));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Level: " + recipe.getRequiredLevel(), NamedTextColor.YELLOW));
        lore.add(Component.text("XP: " + recipe.getBaseExperience(), NamedTextColor.AQUA));
        lore.add(Component.text("Time: " + (recipe.getCraftingTime() / 20.0) + "s", NamedTextColor.GREEN));
        lore.add(Component.text(""));
        lore.add(Component.text("Ingredients:", NamedTextColor.GRAY));

        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isChoice()) {
                // Show choice ingredient with all options
                lore.add(Component.text("• " + ingredient.getAmount() + "x (any of):", NamedTextColor.AQUA));
                for (var option : ingredient.getChoice().getOptions()) {
                    String itemName = getItemDisplayName(option.getItemId());
                    String level = option.getMinLevel() > 0 ? " (Level " + option.getMinLevel() + ")" : "";
                    lore.add(Component.text("  - " + itemName + level, NamedTextColor.GRAY));
                }
            } else {
                // Show fixed ingredient with proper name
                String itemName = getItemDisplayName(ingredient.getItemId());
                lore.add(Component.text("• " + ingredient.getAmount() + "x " + itemName, NamedTextColor.WHITE));
            }
        }

        lore.add(Component.text(""));
        lore.add(Component.text("Click to select", NamedTextColor.GREEN));
        displayItem.setData(DataComponentTypes.LORE, ItemLore.lore(lore));

        return displayItem;
    }

    private ItemStack createInvalidRecipeItem(JobRecipe recipe, String reason) {
        return createTranslatableGuiItem(Material.BARRIER,
                Component.text(recipe.getDisplayName(), NamedTextColor.RED),
                Component.text(reason, NamedTextColor.GRAY),
                Component.text("Recipe: " + recipe.getId(), NamedTextColor.DARK_GRAY));
    }

    private void updateNavigationButtons() {
        ItemStack prevButton = inventory.getItem(PREV_PAGE_SLOT);
        if (prevButton != null) {
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (currentPage > 0) {
                prevMeta.displayName(Component.translatable("hephaestus.crafting.gui.nav.previous", NamedTextColor.YELLOW));
            } else {
                prevMeta.displayName(Component.translatable("hephaestus.crafting.gui.nav.previous", NamedTextColor.DARK_GRAY));
            }
            prevButton.setItemMeta(prevMeta);
        }

        ItemStack nextButton = inventory.getItem(NEXT_PAGE_SLOT);
        if (nextButton != null) {
            ItemMeta nextMeta = nextButton.getItemMeta();
            int maxPages = Math.max(0, (availableRecipes.size() - 1) / RECIPES_PER_PAGE);
            if (currentPage < maxPages) {
                nextMeta.displayName(Component.translatable("hephaestus.crafting.gui.nav.next", NamedTextColor.YELLOW));
            } else {
                nextMeta.displayName(Component.translatable("hephaestus.crafting.gui.nav.next", NamedTextColor.DARK_GRAY));
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

        // Cancel all clicks in the GUI inventory
        if (slot >= 0 && slot < 54) {
            event.setCancelled(true);

            if (slot == PREV_PAGE_SLOT && currentPage > 0) {
                currentPage--;
                updateRecipeDisplay();
            } else if (slot == NEXT_PAGE_SLOT) {
                int maxPages = Math.max(0, (availableRecipes.size() - 1) / RECIPES_PER_PAGE);
                if (currentPage < maxPages) {
                    currentPage++;
                    updateRecipeDisplay();
                }
            } else if (slot == QUANTITY_DECREASE_SLOT) {
                adjustQuantity(event.getClick() == ClickType.SHIFT_LEFT ? -10 : -1);
            } else if (slot == QUANTITY_INCREASE_SLOT) {
                adjustQuantity(event.getClick() == ClickType.SHIFT_LEFT ? 10 : 1);
            } else if (slot == DISCOVERY_BUTTON_SLOT) {
                new RecipeDiscoveryGUI(plugin, player).open();
            } else if (slot == CRAFT_BUTTON_SLOT) {
                if (selectedRecipe != null && canCraftQuantity()) {
                    startCrafting();
                }
            } else if (isRecipeSlot(slot)) {
                selectRecipeFromSlot(slot);
            }
            // QUANTITY_DISPLAY_SLOT and CRAFT_RESULT_SLOT are display only, no action needed
        } else if (selectedRecipe != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updateResultDisplay();
                updateCraftButton();
            }, 1L);
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
            // Calculate result based on current ingredients
            List<ItemStack> ingredients = getIngredientsFromSlots();
            RecipeResult result;

            if (!ingredients.isEmpty()) {
                result = selectedRecipe.calculateResult(ingredients);
            } else {
                result = selectedRecipe.getDisplayResult();
            }

            inventory.setItem(CRAFT_RESULT_SLOT, createResultDisplayItem(selectedRecipe, result, craftQuantity));
        }
    }

    private boolean canCraftQuantity() {
        return selectedRecipe != null && buildConsumptionPlan() != null;
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

        // Calculate result based on ingredients (handles both fixed and dynamic results)
        List<ItemStack> ingredients = getIngredientsFromSlots();
        RecipeResult result;

        // If we have ingredients, calculate based on them; otherwise use display result
        if (!ingredients.isEmpty()) {
            result = recipe.calculateResult(ingredients);
        } else {
            result = recipe.getDisplayResult();
        }

        inventory.setItem(CRAFT_RESULT_SLOT, createResultDisplayItem(recipe, result, craftQuantity));

        updateQuantityDisplay();
        updateCraftButton();
        RecipeResult displayResult = recipe.getDisplayResult();
        String recipeName = (displayResult != null && displayResult.getItemId() != null)
            ? getItemDisplayName(displayResult.getItemId())
            : recipe.getDisplayName();
        GUIUtils.updateTitle(player, Component.text("Crafting: " + recipeName, NamedTextColor.DARK_GREEN));
    }

    private ItemStack createResultDisplayItem(JobRecipe recipe, RecipeResult result, int quantity) {
        if (result == null || result.getItemId() == null) {
            return createInvalidRecipeItem(recipe, "Missing result");
        }
        var hItem = plugin.getLibrary().get(result.getItemId());
        ItemStack displayStack;
        if (hItem != null) {
            HItemStack resultStack = hItem.createStack(Math.max(1, result.getAmount() * quantity), result.getItemLevel(),
                    result.getSocketPattern(), result.getRarity());
            if (resultStack == null) {
                return createInvalidRecipeItem(recipe, "Result stack could not be created");
            }
            displayStack = resultStack.getBukkitStack();
        } else {
            displayStack = HItemUtil.createItemStack(result.getItemId(), Math.max(1, result.getAmount() * quantity));
            if (displayStack == null || displayStack.getType().isAir()) {
                return createInvalidRecipeItem(recipe, "Unknown result item: " + result.getItemId());
            }
        }
        displayStack.setAmount(Math.max(1, Math.min(displayStack.getMaxStackSize(), result.getAmount() * quantity)));
        return displayStack;
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

        boolean canCraft = buildConsumptionPlan() != null;
        for (var ingredient : selectedRecipe.getIngredients()) {
            int requiredPerCraft = ingredient.getAmount();
            int totalRequired = requiredPerCraft * craftQuantity;
            int available;
            String ingredientName;

            if (ingredient.isChoice()) {
                // For choice ingredients, count items from any option in the choice
                available = countChoiceItemsInCraftingSlots(ingredient);
                ingredientName = ingredient.getChoice().getChoiceId() + " (any)";
            } else {
                // For fixed ingredients - use display name
                available = countItemsInCraftingSlots(ingredient.getItemId());
                ingredientName = getItemDisplayName(ingredient.getItemId());
            }

            Component ingredientLine;
            if (available >= totalRequired) {
                ingredientLine = Component.text("* " + totalRequired + "x " + ingredientName +
                    " (" + requiredPerCraft + " each)", NamedTextColor.GREEN);
            } else {
                ingredientLine = Component.text("x " + available + "/" + totalRequired + "x " + ingredientName +
                    " (" + requiredPerCraft + " each)", NamedTextColor.RED);
            }
            lore.add(ingredientLine);
        }

        if (!canCraft) {
            lore.add(Component.text(""));
            lore.add(Component.text("You are lacking the ingredients for this recipe", NamedTextColor.RED));
        } else {
            lore.add(Component.text(""));
            int totalTime = calculateTotalCraftingTime(selectedRecipe.getCraftingTime(), craftQuantity);
            lore.add(Component.text("Total crafting time: " +
                (totalTime / 20.0) + "s", NamedTextColor.YELLOW));
        }

        ItemStack craftButton = createGuiItem(Material.ANVIL, canCraft ? "Craft " + craftQuantity + "x Items" : "Missing Ingredients");
        ItemMeta meta = craftButton.getItemMeta();
        meta.lore(lore);
        craftButton.setItemMeta(meta);
        inventory.setItem(CRAFT_BUTTON_SLOT, craftButton);
    }

    private int countItemsInCraftingSlots(String itemId) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            if (itemId.equals(HItemUtil.getItemId(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private int countChoiceItemsInCraftingSlots(RecipeIngredient ingredient) {
        if (!ingredient.isChoice()) {
            return 0;
        }

        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            String itemId = HItemUtil.getItemId(item);
            if (itemId != null && ingredient.matches(itemId)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private List<ItemStack> getIngredientsFromSlots() {
        List<ItemStack> ingredients = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                ingredients.add(item);
            }
        }
        return ingredients;
    }


    /**
     * Calculate total crafting time with diminishing returns for bulk crafting.
     * Uses a logarithmic scale to prevent excessive wait times.
     * Formula: baseTime * (1 + log2(quantity))
     * Examples:
     * - 1 item: 1.0x base time
     * - 5 items: ~3.3x base time (instead of 5x)
     * - 10 items: ~4.3x base time (instead of 10x)
     * - 20 items: ~5.4x base time (instead of 20x)
     */
    private int calculateTotalCraftingTime(int baseTime, int quantity) {
        if (quantity <= 1) {
            return baseTime;
        }
        double multiplier = 1.0 + (Math.log(quantity) / Math.log(2));
        return (int) Math.ceil(baseTime * multiplier);
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

        // Calculate result BEFORE consuming ingredients (for dynamic recipes)
        List<ItemStack> ingredients = getIngredientsFromSlots();
        RecipeResult calculatedResult = selectedRecipe.calculateResult(ingredients);

        if (calculatedResult == null) {
            plugin.getLogger().warning("Recipe '" + selectedRecipe.getId() + "' has no result - check the recipe configuration (missing 'result' section or invalid resultType)");
            player.sendMessage(Component.text("This recipe has a configuration error and cannot be crafted. Please report this to an admin.", NamedTextColor.RED));
            return;
        }

        ItemStack sampleResult = createRecipeResultStack(calculatedResult, 1);
        if (sampleResult == null || sampleResult.getType().isAir()) {
            plugin.getLogger().warning("Recipe '" + selectedRecipe.getId() + "' result item '" + calculatedResult.getItemId() + "' cannot be created");
            player.sendMessage(Component.text("This recipe has a configuration error and cannot be crafted. Please report this to an admin.", NamedTextColor.RED));
            return;
        }

        // Consume ingredients for the total quantity
        if (!consumeIngredients()) {
            player.sendMessage(Component.text("Failed to consume ingredients!", NamedTextColor.RED));
            return;
        }

        currentlyCrafting = selectedRecipe;
        craftingResult = calculatedResult; // Store the result for later use
        craftingProgress = 0;
        totalCraftingTime = calculateTotalCraftingTime(selectedRecipe.getCraftingTime(), craftQuantity);

        player.sendMessage(Component.text("Started crafting " + craftQuantity + "x " + selectedRecipe.getId() + "...", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

        progressTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateCraftingProgress, 0L, 1L);
    }

    private ItemStack createRecipeResultStack(RecipeResult result, int amount) {
        if (result == null || result.getItemId() == null || result.getItemId().isBlank()) {
            return null;
        }
        var hItem = plugin.getLibrary().get(result.getItemId());
        if (hItem != null) {
            HItemStack resultStack = hItem.createStack(amount, result.getItemLevel(),
                    result.getSocketPattern(), result.getRarity());
            return resultStack == null ? null : resultStack.getBukkitStack();
        }
        return HItemUtil.createItemStack(result.getItemId(), amount);
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
        Map<Integer, Integer> consumptionPlan = buildConsumptionPlan();
        if (consumptionPlan == null) {
            return false;
        }

        // All checks passed, actually consume the items from player inventory
        for (Map.Entry<Integer, Integer> entry : consumptionPlan.entrySet()) {
            int slot = entry.getKey();
            int amount = entry.getValue();

            ItemStack item = player.getInventory().getItem(slot);
            if (item != null) {
                if (item.getAmount() <= amount) {
                    player.getInventory().setItem(slot, null);
                } else {
                    item.setAmount(item.getAmount() - amount);
                }
            }
        }

        return true;
    }

    private Map<Integer, Integer> buildConsumptionPlan() {
        if (selectedRecipe == null) {
            return null;
        }
        ItemStack[] contents = player.getInventory().getContents();
        Map<Integer, Integer> reservedBySlot = new HashMap<>();
        Map<Integer, Integer> consumeBySlot = new HashMap<>();

        for (RecipeIngredient ingredient : selectedRecipe.getIngredients()) {
            int needed = ingredient.getAmount() * craftQuantity;
            int found = 0;

            for (int slot = 0; slot < contents.length && found < needed; slot++) {
                ItemStack item = contents[slot];
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                String itemId = HItemUtil.getItemId(item);
                if (itemId == null || !ingredient.matches(itemId)) {
                    continue;
                }
                int alreadyReserved = reservedBySlot.getOrDefault(slot, 0);
                int available = item.getAmount() - alreadyReserved;
                if (available <= 0) {
                    continue;
                }
                int toUse = Math.min(available, needed - found);
                reservedBySlot.put(slot, alreadyReserved + toUse);
                if (ingredient.isConsumeOnCraft()) {
                    consumeBySlot.put(slot, consumeBySlot.getOrDefault(slot, 0) + toUse);
                }
                found += toUse;
            }

            if (found < needed) {
                return null;
            }
        }
        return consumeBySlot;
    }

    private void completeCrafting(JobRecipe recipe) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        int completedQuantity = craftQuantity; // Store the quantity that was crafted
        RecipeResult result = craftingResult; // Use the stored result

        currentlyCrafting = null;
        craftingResult = null;
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
                        runSync(() -> {
                            // Create the result items with proper quantity
                            int totalResultAmount = result.getAmount() * completedQuantity;

                            // Split into stacks if necessary (max stack size considerations)
                            while (totalResultAmount > 0) {
                                int stackAmount = Math.min(totalResultAmount, 64);
                                ItemStack bukkitStack;
                                var hItem = plugin.getLibrary().get(result.getItemId());
                                if (hItem != null) {
                                    HItemStack resultStack = hItem.createStack(stackAmount, result.getItemLevel(),
                                            result.getSocketPattern(), result.getRarity());
                                    bukkitStack = resultStack == null ? null : resultStack.getBukkitStack();
                                } else {
                                    bukkitStack = HItemUtil.createItemStack(result.getItemId(), stackAmount);
                                }
                                if (bukkitStack != null && !bukkitStack.getType().isAir()) {
                                    bukkitStack.setAmount(Math.min(totalResultAmount, bukkitStack.getMaxStackSize()));
                                    player.getInventory().addItem(bukkitStack);
                                    totalResultAmount -= bukkitStack.getAmount();
                                } else {
                                    break;
                                }
                            }

                            player.sendMessage(Component.text("Crafted " + completedQuantity + "x " + recipe.getDisplayName() +
                                " (+" + totalXp + " XP)", NamedTextColor.GREEN));
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);

                            player.closeInventory();
                            new CraftingStationGUI(plugin, player).open();
                            // Call event
                            new HJobCraftItemEvent(player, recipe, result.getItemId(), completedQuantity).callEvent();
                        });
                    });
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            if (craftingTask != null && !craftingTask.isCancelled()) {
                craftingTask.cancel();
                GUIUtils.updateTitle(player, Component.text("Crafting Station", NamedTextColor.DARK_GREEN));
            }
            if (progressTask != null && !progressTask.isCancelled()) {
                progressTask.cancel();
                GUIUtils.updateTitle(player, Component.text("Crafting Station", NamedTextColor.DARK_GREEN));
            }

            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
        }
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

    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }
}
