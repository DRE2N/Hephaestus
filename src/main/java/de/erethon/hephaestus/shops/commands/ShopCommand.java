package de.erethon.hephaestus.shops.commands;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.shops.Shop;
import de.erethon.hephaestus.shops.ShopManager;
import de.erethon.hephaestus.shops.gui.ShopGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to open shops for players.
 * Usage: /shop <player> <shopId>
 */
public class ShopCommand extends Command {

    private final Hephaestus plugin = Hephaestus.INSTANCE;

    public ShopCommand(String name) {
        super(name);
        setDescription("Open a shop for a player");
        setUsage("/shop <player> <shopId> or /shop reload");
        setPermission("hephaestus.shop");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        ShopManager shopManager = plugin.getShopManager();

        if (shopManager == null) {
            sender.sendMessage(Component.text("Shop system is not initialized!", NamedTextColor.RED));
            return true;
        }

        // Handle reload subcommand
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("hephaestus.shop.reload")) {
                sender.sendMessage(Component.text("You don't have permission to reload shops!", NamedTextColor.RED));
                return true;
            }

            shopManager.reloadShops();
            sender.sendMessage(Component.text("Shops reloaded!", NamedTextColor.GREEN));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Component.text("Available shops:", NamedTextColor.GOLD));
            for (Shop shop : shopManager.getShops().values()) {
                sender.sendMessage(Component.text("  - " + shop.getId() + " (" + shop.getDisplayName() + ")", NamedTextColor.YELLOW));
            }
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /shop <player> <shopId>", NamedTextColor.RED));
            sender.sendMessage(Component.text("       /shop list - List all shops", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("       /shop reload - Reload shops", NamedTextColor.GRAY));
            return true;
        }

        String playerName = args[0];
        String shopId = args[1];

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
            return true;
        }

        Shop shop = shopManager.getShop(shopId);
        if (shop == null) {
            sender.sendMessage(Component.text("Shop not found: " + shopId, NamedTextColor.RED));
            sender.sendMessage(Component.text("Use /shop list to see available shops", NamedTextColor.GRAY));
            return true;
        }

        // Open shop for player
        new ShopGUI(plugin, target, shop).open();

        sender.sendMessage(Component.text("Opened shop '" + shop.getDisplayName() + "' for " + target.getName(), NamedTextColor.GREEN));
        target.sendMessage(Component.text("Welcome to " + shop.getDisplayName() + "!", NamedTextColor.GOLD));

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        List<String> completions = new ArrayList<>();
        ShopManager shopManager = plugin.getShopManager();

        if (shopManager == null) {
            return completions;
        }

        if (args.length == 1) {
            completions.add("reload");
            completions.add("list");
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));

            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            completions.addAll(shopManager.getShops().keySet());

            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        return completions;
    }
}

