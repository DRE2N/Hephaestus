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
        CraftPlayer craftPlayer = (CraftPlayer) player.getPlayer();
        if (craftPlayer == null) {
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        ClientboundOpenScreenPacket packet = new ClientboundOpenScreenPacket(serverPlayer.containerMenu.containerId, MenuType.GENERIC_9x6, PaperAdventure.asVanilla(displayName));
        serverPlayer.connection.send(packet);
    }
}
