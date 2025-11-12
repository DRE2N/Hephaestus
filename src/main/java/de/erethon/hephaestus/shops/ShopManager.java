package de.erethon.hephaestus.shops;

import de.erethon.hephaestus.Hephaestus;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for the shop system.
 * Handles loading shops from config files and managing shop transactions.
 */
public class ShopManager {

    private final Hephaestus plugin;
    private final ShopDatabaseManager databaseManager;
    private final Map<String, Shop> shops = new HashMap<>();
    private final File shopsDirectory;

    public ShopManager(Hephaestus plugin, ShopDatabaseManager databaseManager, File shopsDirectory) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.shopsDirectory = shopsDirectory;

        if (!shopsDirectory.exists()) {
            shopsDirectory.mkdirs();
            createExampleShop();
        }

        loadShops();
    }

    private void loadShops() {
        shops.clear();

        if (!shopsDirectory.exists() || !shopsDirectory.isDirectory()) {
            plugin.getLogger().warning("Shops directory not found: " + shopsDirectory.getPath());
            return;
        }

        File[] files = shopsDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                Shop shop = Shop.loadFromFile(file);
                shops.put(shop.getId(), shop);
                plugin.getLogger().info("Loaded shop: " + shop.getDisplayName() + " (" + shop.getId() + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load shop from file: " + file.getName());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Loaded " + shops.size() + " shops");
    }

    public void reloadShops() {
        loadShops();
    }

    public Shop getShop(String shopId) {
        return shops.get(shopId);
    }

    public Map<String, Shop> getShops() {
        return new HashMap<>(shops);
    }

    /**
     * Gets the remaining stock for a player for a specific item in a shop.
     * Handles restock if needed.
     */
    public CompletableFuture<Integer> getPlayerStock(UUID playerUuid, String shopId, String itemId, Shop.ShopItem shopItem) {
        if (shopItem.hasInfiniteStock()) {
            return CompletableFuture.completedFuture(Integer.MAX_VALUE);
        }

        return databaseManager.getLastRestockTime(playerUuid, shopId, itemId).thenCompose(lastRestockOpt -> {
            long currentTime = System.currentTimeMillis();

            if (lastRestockOpt.isEmpty()) {
                // First time accessing this item, initialize stock
                return databaseManager.setPlayerStock(playerUuid, shopId, itemId, shopItem.getRestockAmount())
                    .thenApply(v -> shopItem.getRestockAmount());
            }

            long lastRestock = lastRestockOpt.get().getTime();
            long timeSinceRestock = currentTime - lastRestock;

            if (timeSinceRestock >= shopItem.getRestockTime()) {
                // Time to restock
                return databaseManager.restockPlayer(playerUuid, shopId, itemId, shopItem.getRestockAmount())
                    .thenApply(v -> shopItem.getRestockAmount());
            }

            // Return current stock
            return databaseManager.getPlayerStock(playerUuid, shopId, itemId);
        });
    }

    /**
     * Decreases the player's remaining stock for an item.
     */
    public CompletableFuture<Void> decreasePlayerStock(UUID playerUuid, String shopId, String itemId, int amount) {
        return databaseManager.decreasePlayerStock(playerUuid, shopId, itemId, amount);
    }

    private void createExampleShop() {
        File exampleFile = new File(shopsDirectory, "example.yml");
        try {
            exampleFile.createNewFile();
            String content = """
                # Shop Display Name
                name: "Example Shop"
                
                # Items in the shop
                items:
                  # Item ID from HItem system
                  minecraft:diamond:
                    # Type: BUY (player buys), SELL (player sells), or BOTH
                    type: BOTH
                    # Price when player buys from shop
                    buyPrice: 100
                    # Price when player sells to shop
                    sellPrice: 50
                    # Amount player can buy per restock period (-1 for infinite)
                    restockAmount: 64
                    # Restock time in minutes (60 = 1 hour)
                    restockTime: 60
                    
                  minecraft:emerald:
                    type: BOTH
                    buyPrice: 50
                    sellPrice: 25
                    restockAmount: 128
                    restockTime: 60
                    
                  minecraft:iron_ingot:
                    type: BUY
                    buyPrice: 10
                    sellPrice: 0
                    restockAmount: -1  # Infinite stock
                    restockTime: 0
                """;
            java.nio.file.Files.writeString(exampleFile.toPath(), content);
            plugin.getLogger().info("Created example shop config at: " + exampleFile.getPath());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create example shop config");
            e.printStackTrace();
        }
    }

    public ShopDatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}

