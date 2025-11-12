package de.erethon.hephaestus.shops;

import de.erethon.bedrock.database.BedrockDBConnection;
import de.erethon.bedrock.database.EDatabaseManager;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Database manager for the shop system.
 * Handles all database operations for shop stock management.
 */
public class ShopDatabaseManager extends EDatabaseManager {

    public ShopDatabaseManager(BedrockDBConnection connection) {
        super(connection, new ThreadPoolExecutor(2, 4, 60L, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>()));
    }

    @Override
    protected CompletableFuture<Void> initializeSchema() {
        return CompletableFuture.runAsync(() -> {
            try {
                jdbi.useHandle(handle -> {
                    ShopDao dao = handle.attach(ShopDao.class);
                    dao.createShopPlayerStockTable();
                    dao.createShopPlayerStockIndex();
                });
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize shop database schema", e);
            }
        }, asyncExecutor);
    }

    @Override
    protected void registerCustomMappers() {
        // No custom mappers needed for now
    }

    // Stock Management
    public CompletableFuture<Integer> getPlayerStock(UUID playerUuid, String shopId, String itemId) {
        return queryAsync(handle -> {
            ShopDao dao = handle.attach(ShopDao.class);
            return dao.getPlayerStock(playerUuid, shopId, itemId).orElse(0);
        });
    }

    public CompletableFuture<Void> setPlayerStock(UUID playerUuid, String shopId, String itemId, int stock) {
        return executeAsync(handle -> {
            ShopDao dao = handle.attach(ShopDao.class);
            dao.setPlayerStock(playerUuid, shopId, itemId, stock);
        });
    }

    public CompletableFuture<Void> decreasePlayerStock(UUID playerUuid, String shopId, String itemId, int amount) {
        return executeAsync(handle -> {
            ShopDao dao = handle.attach(ShopDao.class);
            dao.decreasePlayerStock(playerUuid, shopId, itemId, amount);
        });
    }

    public CompletableFuture<Optional<java.sql.Timestamp>> getLastRestockTime(UUID playerUuid, String shopId, String itemId) {
        return queryAsync(handle -> {
            ShopDao dao = handle.attach(ShopDao.class);
            return dao.getLastRestockTime(playerUuid, shopId, itemId);
        });
    }

    public CompletableFuture<Void> restockPlayer(UUID playerUuid, String shopId, String itemId, int stock) {
        return executeAsync(handle -> {
            ShopDao dao = handle.attach(ShopDao.class);
            dao.restockPlayer(playerUuid, shopId, itemId, stock);
        });
    }
}

