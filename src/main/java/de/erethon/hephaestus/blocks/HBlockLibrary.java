package de.erethon.hephaestus.blocks;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

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
    private void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        if (event.hasItem()) {
            ItemStack item = event.getItem();
            if (item != null && !item.getType().isItem()) {
                return;
            }
            HItem hItem = Hephaestus.getStack(item).getItem();
            if (event.getClickedBlock() == null || hItem.getBlockData() == null) {
                return;
            }
            BlockFace face = event.getBlockFace();
            Block clickedBlock = event.getClickedBlock();
            Player player = event.getPlayer();
            if (!clickedBlock.getType().isSolid()) { // Allow placing blocks in grass and so on
                clickedBlock.getDrops(event.getItem(), player).forEach(drop -> clickedBlock.getWorld().dropItemNaturally(clickedBlock.getLocation(), drop));
                clickedBlock.setBlockData(hItem.getBlockData());
            } else {
                Block newBlock = clickedBlock.getWorld().getBlockAt(event.getClickedBlock().getLocation().add(face.getDirection()));
                // TODO: Need to make up-stacking less laggy and suffocating
                if (!newBlock.getType().isAir() || player.getWorld().hasCollisionsIn(player.getBoundingBox())) {
                    return;
                }
                // Would collide using the block
                BoundingBox box = newBlock.getBoundingBox();
                if (box.overlaps(player.getBoundingBox())) {
                    return;
                }
                newBlock.setType(hItem.getBlockData().getMaterial());
                newBlock.setBlockData(getBlockData(hItem));
            }
            player.swingHand(event.getHand());
            player.playSound(clickedBlock.getLocation(), hItem.getPlacementSound(), 1, 1);
            if (player.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
        }
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

    @EventHandler
    private void onBreakStart(BlockDamageEvent event) {
        Player player = event.getPlayer();
        HItem hItem = getItem(event.getBlock().getBlockData());
        if (hItem == null) {
            return;
        }
        AttributeModifier modifier = new AttributeModifier(NamespacedKey.fromString("hephaestus:break_speed"), hItem.getBreakSpeedModifier(), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        if (!player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getModifiers().contains(modifier)) {
            player.getAttribute(Attribute.BLOCK_BREAK_SPEED).addTransientModifier(modifier);
        } else {
            // Can't directly modify the existing modifier sadly
            player.getAttribute(Attribute.BLOCK_BREAK_SPEED).removeModifier(modifier);
            player.getAttribute(Attribute.BLOCK_BREAK_SPEED).addTransientModifier(modifier);
        }
    }

    @EventHandler
    private void onBreakAbort(BlockDamageAbortEvent event) {
        Player player = event.getPlayer();
        HItem hItem = getItem(event.getBlock().getBlockData());
        if (hItem == null) {
            return;
        }
        // Amount does not matter, remove only cares about the key
        AttributeModifier modifier = new AttributeModifier(NamespacedKey.fromString("hephaestus:break_speed"), 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        player.getAttribute(Attribute.BLOCK_BREAK_SPEED).removeModifier(modifier);

    }

}
