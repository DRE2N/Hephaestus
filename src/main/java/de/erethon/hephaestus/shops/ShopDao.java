package de.erethon.hephaestus.shops;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;
import java.util.UUID;

/**
 * DAO interface for shop database operations.
 */
public interface ShopDao {

    // Table Creation
    @SqlUpdate("""
        CREATE TABLE IF NOT EXISTS shop_player_stock (
            player_uuid UUID NOT NULL,
            shop_id VARCHAR(255) NOT NULL,
            item_id VARCHAR(255) NOT NULL,
            remaining_stock INTEGER NOT NULL DEFAULT 0,
            last_restock TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (player_uuid, shop_id, item_id)
        )
        """)
    void createShopPlayerStockTable();

    @SqlUpdate("""
        CREATE INDEX IF NOT EXISTS idx_shop_player_stock
        ON shop_player_stock(player_uuid, shop_id)
        """)
    void createShopPlayerStockIndex();

    // Stock Management
    @SqlQuery("""
        SELECT remaining_stock FROM shop_player_stock
        WHERE player_uuid = :playerUuid 
        AND shop_id = :shopId 
        AND item_id = :itemId
        """)
    Optional<Integer> getPlayerStock(
            @Bind("playerUuid") UUID playerUuid,
            @Bind("shopId") String shopId,
            @Bind("itemId") String itemId
    );

    @SqlUpdate("""
        INSERT INTO shop_player_stock (player_uuid, shop_id, item_id, remaining_stock, last_restock)
        VALUES (:playerUuid, :shopId, :itemId, :stock, CURRENT_TIMESTAMP)
        ON CONFLICT (player_uuid, shop_id, item_id) 
        DO UPDATE SET remaining_stock = :stock, last_restock = CURRENT_TIMESTAMP
        """)
    void setPlayerStock(
            @Bind("playerUuid") UUID playerUuid,
            @Bind("shopId") String shopId,
            @Bind("itemId") String itemId,
            @Bind("stock") int stock
    );

    @SqlUpdate("""
        UPDATE shop_player_stock
        SET remaining_stock = remaining_stock - :amount
        WHERE player_uuid = :playerUuid 
        AND shop_id = :shopId 
        AND item_id = :itemId
        """)
    void decreasePlayerStock(
            @Bind("playerUuid") UUID playerUuid,
            @Bind("shopId") String shopId,
            @Bind("itemId") String itemId,
            @Bind("amount") int amount
    );

    @SqlQuery("""
        SELECT last_restock FROM shop_player_stock
        WHERE player_uuid = :playerUuid 
        AND shop_id = :shopId 
        AND item_id = :itemId
        """)
    Optional<java.sql.Timestamp> getLastRestockTime(
            @Bind("playerUuid") UUID playerUuid,
            @Bind("shopId") String shopId,
            @Bind("itemId") String itemId
    );

    @SqlUpdate("""
        UPDATE shop_player_stock
        SET remaining_stock = :stock, last_restock = CURRENT_TIMESTAMP
        WHERE player_uuid = :playerUuid 
        AND shop_id = :shopId 
        AND item_id = :itemId
        """)
    void restockPlayer(
            @Bind("playerUuid") UUID playerUuid,
            @Bind("shopId") String shopId,
            @Bind("itemId") String itemId,
            @Bind("stock") int stock
    );
}

