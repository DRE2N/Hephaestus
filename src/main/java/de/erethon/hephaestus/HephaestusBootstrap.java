package de.erethon.hephaestus;

import com.mojang.brigadier.Command;
import de.erethon.hephaestus.items.HItemLibrary;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.set.RegistrySet;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class HephaestusBootstrap implements PluginBootstrap {

    private HItemLibrary itemLibrary;

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        itemLibrary = new HItemLibrary(new File(context.getDataDirectory().toFile(), "items"));
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();
        manager.registerEventHandler(RegistryEvents.ENCHANTMENT.entryAdd().newHandler(e -> e.builder().supportedItems(RegistrySet.keySet(ItemTypeKeys.AIR.registryKey()))));

        manager.registerEventHandler(LifecycleEvents.COMMANDS, e -> {
            final Commands commands = e.registrar();
            commands.register(Commands.literal("hephaestus")
                    .executes(ctx -> Command.SINGLE_SUCCESS)
                    .then(Commands.literal("reload")
                            .executes(ctx -> {
                                ctx.getSource().getSender().sendRichMessage("<green>Reloading...");
                                itemLibrary.reload();
                                ctx.getSource().getSender().sendRichMessage("<green>Reloaded Item Library.");
                                return Command.SINGLE_SUCCESS;
                            }).requires(s -> s.getSender().hasPermission("hephaestus.reload")))
                    .then(Commands.literal("setblockdata")
                            .then(Commands.argument("key", ArgumentTypes.namespacedKey())
                                    .executes(ctx -> {
                                        Player player = (Player) ctx.getSource().getSender();
                                        NamespacedKey key = ctx.getArgument("key", NamespacedKey.class);
                                        Block block = player.getTargetBlockExact(32);
                                        if (block == null) {
                                            player.sendRichMessage("<red>No block in sight.");
                                            return -1;
                                        }
                                        itemLibrary.runIfPresent(key, item -> item.setBlockData(block.getBlockData()));
                                        player.sendRichMessage("<green>Set BlockData for <gray>" + key.toString() + "<green> to" + block.getBlockData().getAsString() + "<green>");
                                        return Command.SINGLE_SUCCESS;
                                    }).requires(s -> s.getSender().hasPermission("hephaestus.setblockdata"))))
                    .then(Commands.literal("register")
                            .then(Commands.argument("key", ArgumentTypes.namespacedKey())
                                    .executes(ctx -> {
                                        ctx.getSource().getSender().sendRichMessage("<gray><i>Parsing Item in hand...");
                                        Player player = (Player) ctx.getSource().getSender();
                                        NamespacedKey key = ctx.getArgument("key", NamespacedKey.class);
                                        if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
                                            player.sendRichMessage("<red>No item in hand.");
                                            return -1;
                                        }
                                        itemLibrary.register(player.getInventory().getItemInMainHand(), key);
                                        ctx.getSource().getSender().sendRichMessage("<green>Registered Item with key <gray>" + key.toString() + "<green>.");
                                        itemLibrary.save();
                                        return Command.SINGLE_SUCCESS;
                                    }).requires(s -> s.getSender().hasPermission("hephaestus.register"))))
                    .then(Commands.literal("give")
                            .then(Commands.argument("key", ArgumentTypes.namespacedKey())
                                    .executes(ctx -> {
                                        Player player = (Player) ctx.getSource().getSender();
                                        NamespacedKey key = ctx.getArgument("key", NamespacedKey.class);
                                        itemLibrary.runIfPresent(key, item -> player.getInventory().addItem(item.rollRandomStack().getBukkitStack()));
                                        player.sendRichMessage("<green>Gave item <gray>" + key.toString() + "<green>.");
                                        return Command.SINGLE_SUCCESS;
                                    }).requires(s -> s.getSender().hasPermission("hephaestus.give"))))
                    .build(), "Main Hephaestus command.", List.of("he", "hp", "h", "hh"));
        });
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new Hephaestus(itemLibrary);
    }
}
