package de.erethon.hephaestus.auctionhouse;

import de.erethon.bedrock.database.BedrockDBConnection;
import de.erethon.bedrock.database.EDatabaseManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Database manager for the auction house system.
 * Handles all database operations for buy orders, sell orders, and collectables.
 */
public class AuctionHouseDatabaseManager extends EDatabaseManager {

    public AuctionHouseDatabaseManager(BedrockDBConnection connection) {
        super(connection, new ThreadPoolExecutor(4, 8, 60L, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>()));
    }

    @Override
    protected CompletableFuture<Void> initializeSchema() {
        return CompletableFuture.runAsync(() -> {
            try {
                jdbi.useHandle(handle -> {
                    AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
                    dao.createSellOrdersTable();
                    dao.createSellOrdersIndex();
                    dao.createBuyOrdersTable();
                    dao.createBuyOrdersIndex();
                    dao.createCollectableItemsTable();
                    dao.createCollectableItemsIndex();
                    dao.createCollectableMoneyTable();
                });
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize auction house database schema", e);
            }
        }, asyncExecutor);
    }

    @Override
    protected void registerCustomMappers() {
        // No custom mappers needed for now
    }

    // Sell Orders
    public CompletableFuture<Long> createSellOrder(UUID sellerUuid, String itemId, String upgrades, byte[] itemData, int quantity, int pricePerUnit) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.createSellOrder(sellerUuid, itemId, upgrades, itemData, quantity, pricePerUnit);
        });
    }

    public CompletableFuture<Optional<SellOrder>> getBestSellOrder(String itemId, String upgrades) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getBestSellOrder(itemId, upgrades);
        });
    }

    public CompletableFuture<List<SellOrder>> getSellOrders(String itemId, String upgrades) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getSellOrders(itemId, upgrades);
        });
    }

    public CompletableFuture<Optional<SellOrder>> getSellOrderById(long orderId) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getSellOrderById(orderId);
        });
    }

    public CompletableFuture<Boolean> cancelSellOrder(long orderId) {
        return executeAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            dao.cancelSellOrder(orderId);
        }).thenApply(v -> true);
    }

    public CompletableFuture<Boolean> updateSellOrderQuantity(long orderId, int newQuantity) {
        return executeAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            dao.updateSellOrderQuantity(orderId, newQuantity);
        }).thenApply(v -> true);
    }

    public CompletableFuture<List<SellOrder>> getPlayerSellOrders(UUID playerUuid) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getPlayerSellOrders(playerUuid);
        });
    }

    public CompletableFuture<List<String>> getDistinctListedItemIds() {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getDistinctListedItemIds();
        });
    }

    // Buy Orders
    public CompletableFuture<Long> createBuyOrder(UUID buyerUuid, String itemId, String upgrades, int quantity, int pricePerUnit) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.createBuyOrder(buyerUuid, itemId, upgrades, quantity, pricePerUnit);
        });
    }

    public CompletableFuture<Optional<BuyOrder>> getBestBuyOrder(String itemId, String upgrades) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getBestBuyOrder(itemId, upgrades);
        });
    }

    public CompletableFuture<List<BuyOrder>> getBuyOrders(String itemId, String upgrades) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getBuyOrders(itemId, upgrades);
        });
    }

    public CompletableFuture<Optional<BuyOrder>> getBuyOrderById(long orderId) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getBuyOrderById(orderId);
        });
    }

    public CompletableFuture<Boolean> cancelBuyOrder(long orderId) {
        return executeAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            dao.cancelBuyOrder(orderId);
        }).thenApply(v -> true);
    }

    public CompletableFuture<Boolean> updateBuyOrderQuantity(long orderId, int newQuantity) {
        return executeAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            dao.updateBuyOrderQuantity(orderId, newQuantity);
        }).thenApply(v -> true);
    }

    public CompletableFuture<List<BuyOrder>> getPlayerBuyOrders(UUID playerUuid) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getPlayerBuyOrders(playerUuid);
        });
    }

    // Collectable Items
    public CompletableFuture<Void> addCollectableItem(UUID playerUuid, String itemId, String upgrades, byte[] itemData, int quantity) {
        return executeAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            dao.addCollectableItem(playerUuid, itemId, upgrades, itemData, quantity);
        });
    }

    public CompletableFuture<List<CollectableItem>> getCollectableItems(UUID playerUuid) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getCollectableItems(playerUuid);
        });
    }

    public CompletableFuture<Void> markItemsCollected(List<Long> itemIds) {
        return executeAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            dao.markItemsCollected(itemIds);
        });
    }

    // Collectable Money
    public CompletableFuture<Void> addCollectableMoney(UUID playerUuid, long amount) {
        return executeAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            dao.addCollectableMoney(playerUuid, amount);
        });
    }

    public CompletableFuture<Long> getCollectableMoney(UUID playerUuid) {
        return queryAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            return dao.getCollectableMoney(playerUuid);
        });
    }

    public CompletableFuture<Void> markMoneyCollected(UUID playerUuid) {
        return executeAsync(handle -> {
            AuctionHouseDao dao = handle.attach(AuctionHouseDao.class);
            dao.markMoneyCollected(playerUuid);
        });
    }
}

