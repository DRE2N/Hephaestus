package de.erethon.hephaestus.jobs.commands;

import de.erethon.hephaestus.jobs.HJob;
import de.erethon.hephaestus.jobs.JobCharacterBridgeUtil;
import de.erethon.hephaestus.jobs.JobManager;
import de.erethon.hephaestus.Hephaestus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JobCommand extends Command implements TabCompleter {

    private JobManager jobManager = Hephaestus.INSTANCE.getJobManager();

    protected JobCommand(@NotNull String name, @NotNull String description, @NotNull String usageMessage, @NotNull List<String> aliases) {
        super(name, description, usageMessage, aliases);
    }

    public JobCommand(@NotNull String name) {
        super(name);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> listJobs(player);
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /job info <jobId>", NamedTextColor.RED));
                    return true;
                }
                showJobInfo(player, args[1]);
            }
            case "select" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /job select <jobId>", NamedTextColor.RED));
                    return true;
                }
                selectJob(player, args[1]);
            }
            case "current" -> showCurrentJob(player);
            case "leave" -> leaveJob(player);
            case "reload" -> {
                if (player.hasPermission("hephaestus.job.reload")) {
                    reloadJobs(player);
                } else {
                    player.sendMessage(Component.text("You don't have permission to reload jobs.", NamedTextColor.RED));
                }
            }
            default -> showHelp(player);
        }
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.translatable("hephaestus.job.command.help.header", NamedTextColor.GOLD));
        player.sendMessage(Component.translatable("hephaestus.job.command.help.list", NamedTextColor.YELLOW));
        player.sendMessage(Component.translatable("hephaestus.job.command.help.info", NamedTextColor.YELLOW));
        player.sendMessage(Component.translatable("hephaestus.job.command.help.select", NamedTextColor.YELLOW));
        player.sendMessage(Component.translatable("hephaestus.job.command.help.current", NamedTextColor.YELLOW));
        player.sendMessage(Component.translatable("hephaestus.job.command.help.leave", NamedTextColor.YELLOW));
        if (player.hasPermission("hephaestus.job.reload")) {
            player.sendMessage(Component.translatable("hephaestus.job.command.help.reload", NamedTextColor.YELLOW));
        }
    }

    private void listJobs(Player player) {
        var jobs = jobManager.getAllJobs();
        if (jobs.isEmpty()) {
            player.sendMessage(Component.translatable("hephaestus.job.command.list.empty", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.translatable("hephaestus.job.command.list.header", NamedTextColor.GOLD));
        for (HJob job : jobs) {
            Component jobLine = Component.text("• ", NamedTextColor.YELLOW)
                .append(job.getTranslatableName().color(NamedTextColor.YELLOW))
                .append(Component.text(" (" + job.getId() + ")", NamedTextColor.GRAY))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(job.getTranslatableDescription().color(NamedTextColor.GRAY));
            player.sendMessage(jobLine);
        }
    }

    private void showJobInfo(Player player, String jobId) {
        HJob job = jobManager.getJob(jobId);
        if (job == null) {
            player.sendMessage(Component.translatable("hephaestus.job.command.info.not_found", Component.text(jobId)));
            return;
        }

        player.sendMessage(Component.text("=== ", NamedTextColor.GOLD)
            .append(job.getTranslatableName().color(NamedTextColor.GOLD))
            .append(Component.text(" ===", NamedTextColor.GOLD)));

        player.sendMessage(Component.translatable("hephaestus.job.command.info.id", Component.text(job.getId())));
        player.sendMessage(Component.translatable("hephaestus.job.command.info.description", job.getTranslatableDescription()));
        player.sendMessage(Component.translatable("hephaestus.job.command.info.max_level", Component.text(job.getMaxLevel())));

        jobManager.getJobPlayerCount(jobId).thenAccept(count -> {
            player.sendMessage(Component.translatable("hephaestus.job.command.info.player_count", Component.text(count)));
        });
    }

    private void selectJob(Player player, String jobId) {
        if (!jobManager.hasJob(jobId)) {
            player.sendMessage(Component.translatable("hephaestus.job.command.select.not_found", Component.text(jobId)));
            return;
        }

        JobCharacterBridgeUtil.setCharacterJob(player, jobId).thenAccept(success -> {
            if (success) {
                HJob job = jobManager.getJob(jobId);
                player.sendMessage(Component.translatable("hephaestus.job.command.select.success", job.getTranslatableName()));
            } else {
                player.sendMessage(Component.translatable("hephaestus.job.command.select.failed", NamedTextColor.RED));
            }
        });
    }

    private void showCurrentJob(Player player) {
        JobCharacterBridgeUtil.getCharacterJobRecord(player).thenAccept(characterJob -> {
            if (characterJob == null || characterJob.job() == null) {
                player.sendMessage(Component.translatable("hephaestus.job.command.current.none", NamedTextColor.YELLOW));
                return;
            }

            HJob job = characterJob.job();
            player.sendMessage(Component.translatable("hephaestus.job.command.current.header", NamedTextColor.GOLD));
            player.sendMessage(Component.translatable("hephaestus.job.command.current.job", job.getTranslatableName()));

            JobCharacterBridgeUtil.getJobLevel(characterJob).thenAccept(level -> {
                player.sendMessage(Component.translatable("hephaestus.job.command.current.level",
                    Component.text(level + "/" + job.getMaxLevel())));
            });
        });
    }

    private void leaveJob(Player player) {
        JobCharacterBridgeUtil.getCharacterJobRecord(player).thenAccept(characterJob -> {
            if (characterJob == null || characterJob.job() == null) {
                player.sendMessage(Component.translatable("hephaestus.job.command.leave.none", NamedTextColor.YELLOW));
                return;
            }

            JobCharacterBridgeUtil.removeCharacterJob(player).thenAccept(success -> {
                if (success) {
                    player.sendMessage(Component.translatable("hephaestus.job.command.leave.success", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.translatable("hephaestus.job.command.leave.failed", NamedTextColor.RED));
                }
            });
        });
    }

    private void reloadJobs(Player player) {
        try {
            jobManager.reloadJobs();
            player.sendMessage(Component.translatable("hephaestus.job.command.reload.success", NamedTextColor.GREEN));
        } catch (Exception e) {
            player.sendMessage(Component.translatable("hephaestus.job.command.reload.failed", NamedTextColor.RED));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = List.of("list", "info", "select", "current", "leave");
            if (sender.hasPermission("hephaestus.job.reload")) {
                commands = new ArrayList<>(commands);
                commands.add("reload");
            }
            completions.addAll(commands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList()));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("select"))) {
            completions.addAll(jobManager.getAllJobs().stream()
                    .map(HJob::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList()));
        }

        return completions;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        return onCommand( sender, this, commandLabel, args);
    }
}
