package de.erethon.hephaestus.events;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import de.erethon.hephaestus.items.HItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class HItemUnequipEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final HItem item;
    private final Player player;
    private final PlayerArmorChangeEvent event;

    public HItemUnequipEvent(HItem item, Player player, PlayerArmorChangeEvent event) {
        this.item = item;
        this.player = player;
        this.event = event;
    }

    public Player getPlayer() {
        return player;
    }

    public HItem getItem() {
        return item;
    }

    public PlayerArmorChangeEvent getEvent() {
        return event;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
}