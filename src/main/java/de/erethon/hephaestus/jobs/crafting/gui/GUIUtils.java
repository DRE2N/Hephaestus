package de.erethon.hephaestus.jobs.crafting.gui;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class GUIUtils {

    public static void updateTitle(Player player, Component displayName) {
        updateTitle(player, displayName, 54);
    }

    public static void updateTitle(Player player, Component displayName, int slots) {
        CraftPlayer craftPlayer = (CraftPlayer) player.getPlayer();
        if (craftPlayer == null) {
            return;
        }
        MenuType<?> menuType = switch (slots) {
            case 9 -> MenuType.GENERIC_9x1;
            case 18 -> MenuType.GENERIC_9x2;
            case 27 -> MenuType.GENERIC_9x3;
            case 36 -> MenuType.GENERIC_9x4;
            case 45 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        ClientboundOpenScreenPacket packet = new ClientboundOpenScreenPacket(serverPlayer.containerMenu.containerId, menuType, PaperAdventure.asVanilla(displayName));
        serverPlayer.connection.send(packet);
    }
}
