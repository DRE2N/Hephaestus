package de.erethon.hephaestus.events;

import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.jobs.crafting.JobRecipe;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class HJobCraftItemEvent extends Event {

    private static  final HandlerList handlers = new HandlerList();

    private final Player player;
    private final JobRecipe recipe;
    private final String resultItemId;
    private final int resultLevel;

    public HJobCraftItemEvent(Player player, JobRecipe recipe, String resultItemId, int resultLevel) {
        this.player = player;
        this.recipe = recipe;
        this.resultItemId = resultItemId;
        this.resultLevel = resultLevel;
    }

    public Player getPlayer() {
        return player;
    }

    public JobRecipe getRecipe() {
        return recipe;
    }

    public String getResultItemId() {
        return resultItemId;
    }

    public int getResultLevel() {
        return resultLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
