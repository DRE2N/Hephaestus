package de.erethon.hephaestus.auctionhouse.gui;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import de.erethon.hephaestus.auctionhouse.CollectableItem;
import de.erethon.tyche.TychePlugin;
import de.erethon.tyche.models.OwnerType;
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
import java.util.stream.Collectors;

/**
 * GUI for collecting purchased items and earned money.
 * Only accessible when at a trading post (allowCollection = true).
 */
public class CollectionGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final AuctionHouseManager auctionHouse;
    private final Player player;
    private final Inventory inventory;

    private List<CollectableItem> items = new ArrayList<>();
    private long collectableMoney = 0;

    private static final int MONEY_DISPLAY_SLOT = 4;
    private static final int COLLECT_ALL_ITEMS_SLOT = 47;
    private static final int COLLECT_MONEY_SLOT = 51;
    private static final int BACK_SLOT = 49;

    public CollectionGUI(Hephaestus plugin, Player player) {
        this.plugin = plugin;
        this.auctionHouse = plugin.getAuctionHouseManager();
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54,
            Component.text("Collection", NamedTextColor.GOLD));

        loadCollectables();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadCollectables() {
        auctionHouse.getCollectableItems(player.getUniqueId()).thenAccept(collectableItems -> {
            this.items = collectableItems;
            Bukkit.getScheduler().runTask(plugin, this::setupInterface);
        });

        auctionHouse.getCollectableMoney(player.getUniqueId()).thenAccept(money -> {
            this.collectableMoney = money;
            Bukkit.getScheduler().runTask(plugin, this::setupInterface);
        });
    }

    private void setupInterface() {
        inventory.clear();

        // Background
        ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, background);
        }

        // Money display
        ItemStack moneyDisplay = createGuiItem(Material.GOLD_INGOT,
            "Heronen to Collect",
            "Amount: " + collectableMoney,
            "",
            collectableMoney > 0 ? NamedTextColor.GREEN + "Click below to collect"
                                 : NamedTextColor.GRAY + "No Herone to collect");
        inventory.setItem(MONEY_DISPLAY_SLOT, moneyDisplay);

        // Display collectable items
        int slot = 10;
        for (int i = 0; i < Math.min(items.size(), 21); i++) {
            CollectableItem collectableItem = items.get(i);
            ItemStack displayItem = createCollectableItemDisplay(collectableItem);
            inventory.setItem(slot, displayItem);

            slot++;
            // Skip rightmost columns
            if ((slot + 1) % 9 == 0) {
                slot += 2;
            }
            // Stop at row 4
            if (slot >= 37) {
                break;
            }
        }

        // Collect all items button
        if (!items.isEmpty()) {
            ItemStack collectAll = createGuiItem(Material.CHEST,
                "Collect All Items",
                "Items: " + items.size(),
                "",
                NamedTextColor.GREEN + "Click to collect all");
            inventory.setItem(COLLECT_ALL_ITEMS_SLOT, collectAll);
        } else {
            ItemStack noItems = createGuiItem(Material.BARRIER,
                "No Items to Collect",
                "Your collection is empty");
            inventory.setItem(COLLECT_ALL_ITEMS_SLOT, noItems);
        }

        // Collect money button
        if (collectableMoney > 0) {
            ItemStack collectMoney = createGuiItem(Material.EMERALD,
                "Collect Money",
                "Amount: " + collectableMoney,
                "",
                NamedTextColor.GREEN + "Click to collect");
            inventory.setItem(COLLECT_MONEY_SLOT, collectMoney);
        } else {
            ItemStack noMoney = createGuiItem(Material.BARRIER,
                "No Money to Collect",
                "Nothing to collect");
            inventory.setItem(COLLECT_MONEY_SLOT, noMoney);
        }

        // Back button
        ItemStack back = createGuiItem(Material.ARROW, "Back to Main Menu");
        inventory.setItem(BACK_SLOT, back);
    }

    private ItemStack createCollectableItemDisplay(CollectableItem collectableItem) {
        try {
            // Try to deserialize the item
            ItemStack bukkitStack = ItemStack.deserializeBytes(collectableItem.itemData());

            ItemMeta meta = bukkitStack.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.addFirst(Component.text("Quantity: " + collectableItem.quantity(), NamedTextColor.YELLOW));
            lore.addFirst(Component.empty());
            lore.addFirst(Component.text("Click to collect this item", NamedTextColor.GREEN));
            meta.lore(lore);
            bukkitStack.setItemMeta(meta);

            return bukkitStack;
        } catch (Exception e) {
            // Fallback if deserialization fails
            return createGuiItem(Material.CHEST,
                "Item: " + collectableItem.itemId(),
                "Quantity: " + collectableItem.quantity(),
                "",
                NamedTextColor.GREEN + "Click to collect");
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

    @Override
    @org.jetbrains.annotations.NotNull
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == COLLECT_ALL_ITEMS_SLOT && !items.isEmpty()) {
            collectAllItems();
        } else if (slot == COLLECT_MONEY_SLOT && collectableMoney > 0) {
            collectMoney();
        } else if (slot == BACK_SLOT) {
            close();
            new AuctionHouseMainGUI(plugin, player, true).open();
        } else {
            // Check if clicked on an individual item
            int displayIndex = getDisplaySlotIndex(slot);
            if (displayIndex >= 0 && displayIndex < items.size()) {
                collectSingleItem(items.get(displayIndex));
            }
        }
    }

    private void collectAllItems() {
        if (items.isEmpty()) return;

        // Check if player has enough inventory space
        int emptySlots = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().equals(Material.AIR)) {
                emptySlots++;
            }
        }

        if (emptySlots < items.size()) {
            player.sendMessage(Component.text("Not enough inventory space! You need " + items.size() + " free slots.", NamedTextColor.RED));
            return;
        }

        // Give all items to player
        for (CollectableItem collectableItem : items) {
            try {
                ItemStack bukkitStack = ItemStack.deserializeBytes(collectableItem.itemData());
                bukkitStack.setAmount(collectableItem.quantity());
                player.getInventory().addItem(bukkitStack);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deserialize item for collection: " + e.getMessage());
            }
        }

        // Mark items as collected
        List<Long> itemIds = items.stream().map(CollectableItem::id).collect(Collectors.toList());
        auctionHouse.markItemsCollected(itemIds).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(Component.text("Collected " + items.size() + " items!", NamedTextColor.GREEN));
                loadCollectables();
            });
        });
    }

    private void collectSingleItem(CollectableItem collectableItem) {
        try {
            ItemStack bukkitStack = ItemStack.deserializeBytes(collectableItem.itemData());
            bukkitStack.setAmount(collectableItem.quantity());
            player.getInventory().addItem(bukkitStack);

            List<Long> itemIds = List.of(collectableItem.id());
            auctionHouse.markItemsCollected(itemIds).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Item collected!", NamedTextColor.GREEN));
                    loadCollectables();
                });
            });
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to collect item!", NamedTextColor.RED));
            plugin.getLogger().warning("Failed to deserialize item for collection: " + e.getMessage());
        }
    }

    private void collectMoney() {
        if (collectableMoney <= 0) return;
        if (!(Bukkit.getPluginManager().isPluginEnabled("Tyche"))) {
            player.sendMessage(Component.text("Economy service is not available!", NamedTextColor.RED));
            return;
        }

        auctionHouse.markMoneyCollected(player.getUniqueId()).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(Component.text("Collected " + collectableMoney + " money!", NamedTextColor.GREEN));
                TychePlugin.getEconomyService().deposit(player.getUniqueId(), OwnerType.PLAYER, "herone", collectableMoney, "Collected from Auction House", player.getUniqueId());
                loadCollectables();
            });
        });
    }

    private int getDisplaySlotIndex(int slot) {
        if (slot < 10 || slot >= 37) return -1;

        int row = (slot - 10) / 9;
        int col = (slot - 10) % 9;

        if (col >= 7) return -1;

        return row * 7 + col;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            unregister();
        }
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void close() {
        player.closeInventory();
        unregister();
    }

    private void unregister() {
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
    }
}

