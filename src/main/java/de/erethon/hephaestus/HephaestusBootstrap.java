package de.erethon.hephaestus;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.erethon.hephaestus.items.HItemLibrary;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.items.upgrades.HItemUpgrade;
import de.erethon.hephaestus.items.upgrades.HRolledUpgrade;
import de.erethon.hephaestus.utils.HUpgradeResult;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.VanillaArgumentProviderImpl;
import io.papermc.paper.command.brigadier.argument.range.IntegerRangeProvider;
import io.papermc.paper.command.brigadier.argument.resolvers.ArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.set.RegistrySet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.GiveCommand;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class HephaestusBootstrap implements PluginBootstrap {

    private HItemLibrary itemLibrary;

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        itemLibrary = new HItemLibrary(new File(context.getDataDirectory().toFile(), "items"), new File(context.getDataDirectory().toFile(), "upgrades"));
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
                            }).requires(s -> s.getSender().hasPermission("hephaestus.reload"))
                    )
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
                                    }).requires(s -> s.getSender().hasPermission("hephaestus.setblockdata"))
                                    .suggests((ctx, builder) -> {
                                        String input = builder.getRemaining().toLowerCase();
                                        itemLibrary.getKeys().forEach(key -> {
                                            String keyString = key.toString();
                                            if (keyString.toLowerCase().startsWith(input)) {
                                                builder.suggest(keyString);
                                            }
                                        });
                                        return builder.buildFuture();
                                    })
                            )
                    )
                    .then(Commands.literal("upgrade")
                            .then(Commands.argument("id", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        ctx.getSource().getSender().sendRichMessage("<gray><i>Upgrading Item in hand...");
                                        Player player = (Player) ctx.getSource().getSender();
                                        ItemStack stack = player.getInventory().getItemInMainHand();
                                        if (stack.getType() == Material.AIR) {
                                            player.sendRichMessage("<red>No item in hand.");
                                            return -1;
                                        }
                                        String id = ctx.getArgument("id", String.class);
                                        HItemUpgrade upgrade = itemLibrary.getUpgrade(id);
                                        if (upgrade == null) {
                                            player.sendRichMessage("<red>Upgrade not found.");
                                            return -1;
                                        }
                                        HUpgradeResult result = HItemStack.getFromStack(stack).rollAndAddUpgrade(id);
                                        ctx.getSource().getSender().sendRichMessage("<green>Result: <gray>" + result.toString());
                                        return Command.SINGLE_SUCCESS;
                                    }).requires(s -> s.getSender().hasPermission("hephaestus.upgrade"))
                                    .suggests((ctx, builder) -> {
                                        String input = builder.getRemaining().toLowerCase();
                                        itemLibrary.getUpgradeKeys().forEach(upgradeKey -> {
                                            if (upgradeKey.toLowerCase().startsWith(input)) {
                                                builder.suggest(upgradeKey);
                                            }
                                        });
                                        return builder.buildFuture();
                                    })
                            )
                    )
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
                                    }).requires(s -> s.getSender().hasPermission("hephaestus.register"))
                            )
                    )
                    .build(), "Main Hephaestus command.", List.of("he", "hp", "h", "hh"));



                    commands.register(Commands.literal("give")
                        .then(Commands.argument("key", ArgumentTypes.namespacedKey())
                                .executes(ctx -> {
                                    Player player = (Player) ctx.getSource().getSender();
                                    NamespacedKey key = ctx.getArgument("key", NamespacedKey.class);
                                    itemLibrary.runIfPresent(key, item -> player.getInventory().addItem(item.rollRandomStack().getBukkitStack()));
                                    player.sendRichMessage("<green>Gave item <gray>" + key.toString() + "<green>.");
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(ctx -> {
                                            Player targetPlayer = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
                                            NamespacedKey key = ctx.getArgument("key", NamespacedKey.class);
                                            itemLibrary.runIfPresent(key, item -> targetPlayer.getInventory().addItem(item.rollRandomStack().getBukkitStack()));
                                            ctx.getSource().getSender().sendRichMessage("<green>Gave item <gray>" + key.toString() + "<green> to " + targetPlayer.getName() + ".");
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    Player targetPlayer = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
                                                    NamespacedKey key = ctx.getArgument("key", NamespacedKey.class);
                                                    int count = ctx.getArgument("count", Integer.class);
                                                    itemLibrary.runIfPresent(key, item -> {
                                                        ItemStack stack = item.rollRandomStack().getBukkitStack();
                                                        stack.setAmount(Math.min(stack.getMaxStackSize(), count));
                                                        targetPlayer.getInventory().addItem(stack);
                                                    });
                                                    ctx.getSource().getSender().sendRichMessage(String.format("<green>Gave %d of item <gray>%s <green>to %s.", count, key.toString(), targetPlayer.getName()));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                                .suggests((ctx, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    itemLibrary.getKeys().forEach(key -> {
                                        String keyString = key.toString();
                                        if (keyString.toLowerCase().startsWith(input)) {
                                            builder.suggest(keyString);
                                        }
                                    });
                                    return builder.buildFuture();
                                })
                                .requires(s -> s.getSender().hasPermission("hephaestus.give")))
                    .build(), "Give an item to a player.", List.of("i", "g"));
        });
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new Hephaestus(itemLibrary);
    }
}
