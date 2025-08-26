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

        JobCharacterBridgeUtil.getCharacterJobRecord(player).thenAccept(characterJob -> {
            if (characterJob == null) {
                player.sendMessage(Component.text("You need a job to discover recipes!", NamedTextColor.RED));
                return;
            }

            String jobId = characterJob.job().getId();
            JobRecipe discoveredRecipe = recipeManager.discoverRecipe(jobId, ingredients);

            if (discoveredRecipe == null) {
                player.sendMessage(Component.text("No recipe discovered with these ingredients.", NamedTextColor.YELLOW));
                setResultSlot(null);
                return;
            }

            HCharacter character = characterJob.character();
            progressManager.hasDiscoveredRecipe(character.getCharacterID(), discoveredRecipe.getId())
                .thenAccept(alreadyDiscovered -> {
                    if (alreadyDiscovered) {
                        player.sendMessage(Component.text("You already know this recipe!", NamedTextColor.YELLOW));
                        showRecipeResult(discoveredRecipe);
                        return;
                    }

                    JobCharacterBridgeUtil.getJobLevel(characterJob).thenAccept(level -> {
                        if (level < discoveredRecipe.getRequiredLevel()) {
                            player.sendMessage(Component.text("Your job level is too low for this recipe! Required: " +
                                discoveredRecipe.getRequiredLevel(), NamedTextColor.RED));
                            return;
                        }

                        progressManager.discoverRecipe(character.getCharacterID(), discoveredRecipe.getId())
                            .thenRun(() -> {
                                player.sendMessage(Component.text("Recipe discovered: " + discoveredRecipe.getId(), NamedTextColor.GREEN));
                                showRecipeResult(discoveredRecipe);

                                long discoveryXp = discoveredRecipe.getBaseExperience() / 4;
                                JobCharacterBridgeUtil.grantJobExperience(characterJob, discoveryXp);
                                player.sendMessage(Component.text("+" + discoveryXp + " job experience!", NamedTextColor.AQUA));
                            });
                    });
                });
        });
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
