package de.erethon.hephaestus.auctionhouse;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO interface for auction house database operations.
 */
public interface AuctionHouseDao {

    // Table Creation
    @SqlUpdate("""
        CREATE TABLE IF NOT EXISTS ah_sell_orders (
            id BIGSERIAL PRIMARY KEY,
            seller_uuid UUID NOT NULL,
            item_id VARCHAR(255) NOT NULL,
            upgrades TEXT NOT NULL DEFAULT '',
            item_data BYTEA NOT NULL,
            quantity INTEGER NOT NULL,
            price_per_unit INTEGER NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """)
    void createSellOrdersTable();

    @SqlUpdate("""
        CREATE INDEX IF NOT EXISTS idx_sell_orders_item
        ON ah_sell_orders(item_id, upgrades, price_per_unit)
        """)
    void createSellOrdersIndex();

    @SqlUpdate("""
        CREATE TABLE IF NOT EXISTS ah_buy_orders (
            id BIGSERIAL PRIMARY KEY,
            buyer_uuid UUID NOT NULL,
            item_id VARCHAR(255) NOT NULL,
            upgrades TEXT NOT NULL DEFAULT '',
            quantity INTEGER NOT NULL,
            price_per_unit INTEGER NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """)
    void createBuyOrdersTable();

    @SqlUpdate("""
        CREATE INDEX IF NOT EXISTS idx_buy_orders_item
        ON ah_buy_orders(item_id, upgrades, price_per_unit DESC)
        """)
    void createBuyOrdersIndex();

    @SqlUpdate("""
        CREATE TABLE IF NOT EXISTS ah_collectable_items (
            id BIGSERIAL PRIMARY KEY,
            player_uuid UUID NOT NULL,
            item_id VARCHAR(255) NOT NULL,
            upgrades TEXT NOT NULL DEFAULT '',
            item_data BYTEA NOT NULL,
            quantity INTEGER NOT NULL,
            collected BOOLEAN DEFAULT FALSE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """)
    void createCollectableItemsTable();

    @SqlUpdate("""
        CREATE INDEX IF NOT EXISTS idx_collectable_items_player
        ON ah_collectable_items(player_uuid, collected)
        """)
    void createCollectableItemsIndex();

    @SqlUpdate("""
        CREATE TABLE IF NOT EXISTS ah_collectable_money (
            player_uuid UUID PRIMARY KEY,
            amount BIGINT NOT NULL DEFAULT 0,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """)
    void createCollectableMoneyTable();

    // Sell Orders
    @SqlUpdate("""
        INSERT INTO ah_sell_orders (seller_uuid, item_id, upgrades, item_data, quantity, price_per_unit)
        VALUES (:sellerUuid, :itemId, :upgrades, :itemData, :quantity, :pricePerUnit)
        """)
    @GetGeneratedKeys("id")
    long createSellOrder(
            @Bind("sellerUuid") UUID sellerUuid,
            @Bind("itemId") String itemId,
            @Bind("upgrades") String upgrades,
            @Bind("itemData") byte[] itemData,
            @Bind("quantity") int quantity,
            @Bind("pricePerUnit") int pricePerUnit
    );

    @SqlQuery("""
        SELECT id, seller_uuid, item_id, upgrades, item_data, quantity, price_per_unit, created_at
        FROM ah_sell_orders
        WHERE item_id = :itemId AND upgrades = :upgrades
        ORDER BY price_per_unit ASC, created_at ASC
        LIMIT 1
        """)
    @RegisterConstructorMapper(SellOrder.class)
    Optional<SellOrder> getBestSellOrder(@Bind("itemId") String itemId, @Bind("upgrades") String upgrades);

    @SqlQuery("""
        SELECT id, seller_uuid, item_id, upgrades, item_data, quantity, price_per_unit, created_at
        FROM ah_sell_orders
        WHERE item_id = :itemId AND upgrades = :upgrades
        ORDER BY price_per_unit ASC, created_at ASC
        """)
    @RegisterConstructorMapper(SellOrder.class)
    List<SellOrder> getSellOrders(@Bind("itemId") String itemId, @Bind("upgrades") String upgrades);

    @SqlQuery("""
        SELECT id, seller_uuid, item_id, upgrades, item_data, quantity, price_per_unit, created_at
        FROM ah_sell_orders
        WHERE id = :orderId
        """)
    @RegisterConstructorMapper(SellOrder.class)
    Optional<SellOrder> getSellOrderById(@Bind("orderId") long orderId);

    @SqlUpdate("DELETE FROM ah_sell_orders WHERE id = :orderId")
    void cancelSellOrder(@Bind("orderId") long orderId);

    @SqlUpdate("""
        UPDATE ah_sell_orders
        SET quantity = :quantity
        WHERE id = :orderId
        """)
    void updateSellOrderQuantity(@Bind("orderId") long orderId, @Bind("quantity") int quantity);

    @SqlQuery("""
        SELECT id, seller_uuid, item_id, upgrades, item_data, quantity, price_per_unit, created_at
        FROM ah_sell_orders
        WHERE seller_uuid = :playerUuid
        ORDER BY created_at DESC
        """)
    @RegisterConstructorMapper(SellOrder.class)
    List<SellOrder> getPlayerSellOrders(@Bind("playerUuid") UUID playerUuid);

    @SqlQuery("""
        SELECT DISTINCT item_id
        FROM ah_sell_orders
        ORDER BY item_id
        """)
    List<String> getDistinctListedItemIds();

    // Buy Orders
    @SqlUpdate("""
        INSERT INTO ah_buy_orders (buyer_uuid, item_id, upgrades, quantity, price_per_unit)
        VALUES (:buyerUuid, :itemId, :upgrades, :quantity, :pricePerUnit)
        """)
    @GetGeneratedKeys("id")
    long createBuyOrder(
            @Bind("buyerUuid") UUID buyerUuid,
            @Bind("itemId") String itemId,
            @Bind("upgrades") String upgrades,
            @Bind("quantity") int quantity,
            @Bind("pricePerUnit") int pricePerUnit
    );

    @SqlQuery("""
        SELECT id, buyer_uuid, item_id, upgrades, quantity, price_per_unit, created_at
        FROM ah_buy_orders
        WHERE item_id = :itemId AND upgrades = :upgrades
        ORDER BY price_per_unit DESC, created_at ASC
        LIMIT 1
        """)
    @RegisterConstructorMapper(BuyOrder.class)
    Optional<BuyOrder> getBestBuyOrder(@Bind("itemId") String itemId, @Bind("upgrades") String upgrades);

    @SqlQuery("""
        SELECT id, buyer_uuid, item_id, upgrades, quantity, price_per_unit, created_at
        FROM ah_buy_orders
        WHERE item_id = :itemId AND upgrades = :upgrades
        ORDER BY price_per_unit DESC, created_at ASC
        """)
    @RegisterConstructorMapper(BuyOrder.class)
    List<BuyOrder> getBuyOrders(@Bind("itemId") String itemId, @Bind("upgrades") String upgrades);

    @SqlQuery("""
        SELECT id, buyer_uuid, item_id, upgrades, quantity, price_per_unit, created_at
        FROM ah_buy_orders
        WHERE id = :orderId
        """)
    @RegisterConstructorMapper(BuyOrder.class)
    Optional<BuyOrder> getBuyOrderById(@Bind("orderId") long orderId);

    @SqlUpdate("DELETE FROM ah_buy_orders WHERE id = :orderId")
    void cancelBuyOrder(@Bind("orderId") long orderId);

    @SqlUpdate("""
        UPDATE ah_buy_orders
        SET quantity = :quantity
        WHERE id = :orderId
        """)
    void updateBuyOrderQuantity(@Bind("orderId") long orderId, @Bind("quantity") int quantity);

    @SqlQuery("""
        SELECT id, buyer_uuid, item_id, upgrades, quantity, price_per_unit, created_at
        FROM ah_buy_orders
        WHERE buyer_uuid = :playerUuid
        ORDER BY created_at DESC
        """)
    @RegisterConstructorMapper(BuyOrder.class)
    List<BuyOrder> getPlayerBuyOrders(@Bind("playerUuid") UUID playerUuid);

    // Collectable Items
    @SqlUpdate("""
        INSERT INTO ah_collectable_items (player_uuid, item_id, upgrades, item_data, quantity)
        VALUES (:playerUuid, :itemId, :upgrades, :itemData, :quantity)
        """)
    void addCollectableItem(
            @Bind("playerUuid") UUID playerUuid,
            @Bind("itemId") String itemId,
            @Bind("upgrades") String upgrades,
            @Bind("itemData") byte[] itemData,
            @Bind("quantity") int quantity
    );

    @SqlQuery("""
        SELECT id, player_uuid, item_id, upgrades, item_data, quantity, created_at
        FROM ah_collectable_items
        WHERE player_uuid = :playerUuid AND collected = FALSE
        ORDER BY created_at DESC
        """)
    @RegisterConstructorMapper(CollectableItem.class)
    List<CollectableItem> getCollectableItems(@Bind("playerUuid") UUID playerUuid);

    @SqlUpdate("""
        UPDATE ah_collectable_items
        SET collected = TRUE
        WHERE id IN (<itemIds>)
        """)
    void markItemsCollected(@BindList("itemIds") List<Long> itemIds);

    // Collectable Money
    @SqlUpdate("""
        INSERT INTO ah_collectable_money (player_uuid, amount, updated_at)
        VALUES (:playerUuid, :amount, CURRENT_TIMESTAMP)
        ON CONFLICT (player_uuid)
        DO UPDATE SET
            amount = ah_collectable_money.amount + :amount,
            updated_at = CURRENT_TIMESTAMP
        """)
    void addCollectableMoney(@Bind("playerUuid") UUID playerUuid, @Bind("amount") long amount);

    @SqlQuery("""
        SELECT COALESCE(amount, 0)
        FROM ah_collectable_money
        WHERE player_uuid = :playerUuid
        """)
    long getCollectableMoney(@Bind("playerUuid") UUID playerUuid);

    @SqlUpdate("""
        UPDATE ah_collectable_money
        SET amount = 0, updated_at = CURRENT_TIMESTAMP
        WHERE player_uuid = :playerUuid
        """)
    void markMoneyCollected(@Bind("playerUuid") UUID playerUuid);
}

