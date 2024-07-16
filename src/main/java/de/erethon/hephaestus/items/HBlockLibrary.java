package de.erethon.hephaestus.items;

import de.erethon.hephaestus.Hephaestus;
import org.bukkit.GameMode;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class HBlockLibrary implements Listener {

    private final Map<HItem, BlockData> itemToBlock = new HashMap<>();
    private final Map<BlockData, HItem> blockToItem = new HashMap<>();

    public void register(HItem item, BlockData blockData) {
        itemToBlock.put(item, blockData);
        blockToItem.put(blockData, item);
    }

    public BlockData getBlockData(HItem item) {
        return itemToBlock.get(item);
    }

    public HItem getItem(BlockData blockData) {
        return blockToItem.get(blockData);
    }

    @EventHandler
    private void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        HItem hItem = Hephaestus.getStack(item).getItem();
        if (hItem.getBlockData() == null || hItem.getBlockData().matches(event.getBlockPlaced().getBlockData())) {
            return;
        }
        event.getBlockPlaced().setType(hItem.getBlockData().getMaterial());
        event.getBlockPlaced().setBlockData(hItem.getBlockData());
    }

    @EventHandler
    private void onBlockBreak(BlockDropItemEvent event) {
        HItem hItem = getItem(event.getBlockState().getBlockData());
        if (hItem == null) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        event.getItems().clear();
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), hItem.rollRandomStack().getBukkitStack());
    }

}
