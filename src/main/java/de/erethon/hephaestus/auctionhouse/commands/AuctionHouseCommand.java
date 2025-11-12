package de.erethon.hephaestus.auctionhouse.commands;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import de.erethon.hephaestus.auctionhouse.BuyOrder;
import de.erethon.hephaestus.auctionhouse.CollectableItem;
import de.erethon.hephaestus.auctionhouse.SellOrder;
import de.erethon.hephaestus.auctionhouse.gui.AuctionHouseMainGUI;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Command for interacting with the auction house.
 * Usage:
 * - /ah - Open auction house GUI (browse only)
 * - /ah open - Open auction house GUI at a trading post (with collection access)
 * - /ah sell <price> - Sell the item in your hand
 * - /ah buy <itemId> <quantity> <price> - Create a buy order
 * - /ah list <itemId> - List all orders for an item
 * - /ah myorders - List your active orders
 * - /ah cancel sell <orderId> - Cancel a sell order
 * - /ah cancel buy <orderId> - Cancel a buy order
 * - /ah collect - View collectables (items and money to collect)
 */
public class AuctionHouseCommand extends Command {

    private final Hephaestus plugin;

    public AuctionHouseCommand(Hephaestus plugin, AuctionHouseManager auctionHouse) {
        super("ah");
        this.plugin = plugin;
        setDescription("Auction house command");
        setUsage("/ah");
        setAliases(List.of("auctionhouse", "market"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        // Player or self
        if (!(sender instanceof Player p)) {
            Player player = Bukkit.getPlayerExact(args[0]);
            if (player == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            // Console opened for a player - open with collection access
            new AuctionHouseMainGUI(plugin, player, true).open();
            return true;
        }
        else {
            // Player opened for self - open without collection access
            new AuctionHouseMainGUI(plugin, p, false).open();
        }
        return true;
    }
}

