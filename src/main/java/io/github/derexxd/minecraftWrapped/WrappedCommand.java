package io.github.derexxd.minecraftWrapped;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class WrappedCommand implements CommandExecutor, TabCompleter {

    private final MinecraftWrapped plugin;

    public WrappedCommand(MinecraftWrapped plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setkey" -> handleSetKey(sender, args);
            case "upload" -> handleUpload(sender, false);
            case "test" -> handleUpload(sender, true);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Stream.of("setkey", "upload", "test", "reload", "status")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("  wrapped", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text(".gg", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text("  companion plugin", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(""));
        sendCommandHint(sender, "/wrapped setkey <key>", "Set your API key");
        sendCommandHint(sender, "/wrapped upload", "Push stats to wrapped.gg now");
        sendCommandHint(sender, "/wrapped test", "Dry run — see what would be sent");
        sendCommandHint(sender, "/wrapped reload", "Reload the config file");
        sendCommandHint(sender, "/wrapped status", "Show current plugin status");
        sender.sendMessage(Component.text(""));
    }

    private void sendCommandHint(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(
                Component.text("  ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(cmd, NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.suggestCommand(cmd.split(" <")[0] + " "))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to fill in", NamedTextColor.GRAY))))
                        .append(Component.text(" — " + desc, NamedTextColor.GRAY))
        );
    }

    private void handleSetKey(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(""));
            sender.sendMessage(Component.text("  To set up your Wrapped, you need an API key.", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(""));
            sender.sendMessage(
                    Component.text("  ▸ ", NamedTextColor.DARK_GRAY)
                            .append(Component.text("Get your key from wrapped.gg/panel", NamedTextColor.GOLD)
                                    .decorate(TextDecoration.UNDERLINED)
                                    .clickEvent(ClickEvent.openUrl("https://wrapped.gg/panel"))
                                    .hoverEvent(HoverEvent.showText(Component.text("Opens wrapped.gg/panel in your browser", NamedTextColor.GRAY))))
            );
            sender.sendMessage(Component.text(""));
            sender.sendMessage(
                    Component.text("  Then run: ", NamedTextColor.GRAY)
                            .append(Component.text("/wrapped setkey wgg_live_yourkey", NamedTextColor.WHITE)
                                    .clickEvent(ClickEvent.suggestCommand("/wrapped setkey "))
                                    .hoverEvent(HoverEvent.showText(Component.text("Click to fill in the command", NamedTextColor.GRAY))))
            );
            sender.sendMessage(Component.text(""));
            return;
        }

        String key = args[1];
        if (!WrappedAPI.validateKeyFormat(key)) {
            sender.sendMessage(Component.text("  ✗ ", NamedTextColor.RED)
                    .append(Component.text("That doesn't look right. Keys start with ", NamedTextColor.RED))
                    .append(Component.text("wgg_live_", NamedTextColor.WHITE)));
            return;
        }

        plugin.getConfig().set("api-key", key);
        plugin.saveConfig();
        plugin.reloadConfig();
        sender.sendMessage(Component.text("  ✓ ", NamedTextColor.GREEN)
                .append(Component.text("API key saved! ", NamedTextColor.GREEN))
                .append(Component.text("Run ", NamedTextColor.GRAY))
                .append(Component.text("/wrapped upload", NamedTextColor.WHITE)
                        .clickEvent(ClickEvent.suggestCommand("/wrapped upload"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to run", NamedTextColor.GRAY))))
                .append(Component.text(" to send your first snapshot.", NamedTextColor.GRAY)));
    }

    private void handleUpload(CommandSender sender, boolean dryRun) {
        String key = plugin.getConfig().getString("api-key", "");
        if (!dryRun && !WrappedAPI.validateKeyFormat(key)) {
            sender.sendMessage(Component.text("  ✗ ", NamedTextColor.RED)
                    .append(Component.text("No valid API key set. Run ", NamedTextColor.RED))
                    .append(Component.text("/wrapped setkey", NamedTextColor.WHITE)
                            .clickEvent(ClickEvent.suggestCommand("/wrapped setkey "))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to set up", NamedTextColor.GRAY)))));
            return;
        }

        sender.sendMessage(Component.text("  ⟳ ", NamedTextColor.YELLOW)
                .append(Component.text(dryRun ? "Running dry test..." : "Reading stats and uploading...", NamedTextColor.YELLOW)));

        String source = plugin.getConfig().getString("source", "plugin");
        boolean uploadIcon = plugin.getConfig().getBoolean("upload-icon", true);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File statsFolder = StatsCollector.findStatsFolder(plugin.getLogger());
            if (statsFolder == null) {
                sender.sendMessage(Component.text("  ✗ ", NamedTextColor.RED)
                        .append(Component.text("Could not find a stats folder.", NamedTextColor.RED)));
                return;
            }

            Map<String, String> usercache = StatsCollector.loadUsercache(plugin.getLogger());
            List<PlayerData> players = StatsCollector.collectStats(statsFolder, usercache, plugin.getLogger());
            if (players.isEmpty()) {
                sender.sendMessage(Component.text("  ✗ ", NamedTextColor.RED)
                        .append(Component.text("No player stats found.", NamedTextColor.RED)));
                return;
            }

            try {
                PayloadResult payload = PayloadBuilder.build(players, source);
                long sizeKB = (payload.data.length + 1023) / 1024;

                if (dryRun) {
                    sender.sendMessage(Component.text(""));
                    sender.sendMessage(Component.text("  Dry run complete", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
                    sender.sendMessage(Component.text("  Players: ", NamedTextColor.GRAY).append(Component.text(players.size(), NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("  Payload: ", NamedTextColor.GRAY).append(Component.text(sizeKB + " KB (gzipped)", NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("  Hash:    ", NamedTextColor.GRAY).append(Component.text(payload.hash.substring(0, 16) + "...", NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("  Would send to: ", NamedTextColor.GRAY).append(Component.text("wrapped.gg/v1/snapshots", NamedTextColor.GOLD)));
                    sender.sendMessage(Component.text(""));
                    return;
                }

                UploadResult result = WrappedAPI.uploadSnapshot(payload.data, payload.hash, key, source, plugin.getLogger());

                if (result.success) {
                    if (result.duplicate) {
                        sender.sendMessage(Component.text("  ✓ ", NamedTextColor.GREEN)
                                .append(Component.text("Identical to your last upload — nothing changed.", NamedTextColor.GRAY)));
                    } else {
                        sender.sendMessage(Component.text("  ✓ ", NamedTextColor.GREEN)
                                .append(Component.text("Uploaded stats for " + players.size() + " players! (" + sizeKB + " KB)", NamedTextColor.GREEN)));
                    }

                    if (result.slug != null) {
                        sender.sendMessage(Component.text("  ▸ ", NamedTextColor.DARK_GRAY)
                                .append(Component.text("https://wrapped.gg/" + result.slug, NamedTextColor.GOLD)
                                        .decorate(TextDecoration.UNDERLINED)
                                        .clickEvent(ClickEvent.openUrl("https://wrapped.gg/" + result.slug))
                                        .hoverEvent(HoverEvent.showText(Component.text("Open your Wrapped page", NamedTextColor.GRAY)))));
                    }

                    if (uploadIcon) {
                        File icon = new File("server-icon.png");
                        if (icon.exists()) {
                            WrappedAPI.uploadIcon(icon, key, plugin.getLogger());
                        }
                    }
                } else {
                    sender.sendMessage(Component.text("  ✗ ", NamedTextColor.RED)
                            .append(Component.text(result.error, NamedTextColor.RED)));
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("  ✗ ", NamedTextColor.RED)
                        .append(Component.text("Error: " + e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(Component.text("  ✓ ", NamedTextColor.GREEN)
                .append(Component.text("Config reloaded.", NamedTextColor.GREEN)));
    }

    private void handleStatus(CommandSender sender) {
        String key = plugin.getConfig().getString("api-key", "");
        boolean keyValid = WrappedAPI.validateKeyFormat(key);
        int intervalMin = plugin.getConfig().getInt("upload-interval-minutes", 360);

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("  wrapped", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text(".gg", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text("  status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(""));

        if (keyValid) {
            String masked = key.substring(0, 13) + "..." + key.substring(key.length() - 4);
            sender.sendMessage(Component.text("  API Key:  ", NamedTextColor.GRAY)
                    .append(Component.text(masked, NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("  API Key:  ", NamedTextColor.GRAY)
                    .append(Component.text("not set", NamedTextColor.RED))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("/wrapped setkey", NamedTextColor.WHITE)
                            .clickEvent(ClickEvent.suggestCommand("/wrapped setkey "))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to set up", NamedTextColor.GRAY)))));
        }

        sender.sendMessage(Component.text("  Interval: ", NamedTextColor.GRAY)
                .append(Component.text("every " + intervalMin + " minutes", NamedTextColor.WHITE)));

        File statsFolder = StatsCollector.findStatsFolder(plugin.getLogger());
        if (statsFolder != null) {
            File[] files = statsFolder.listFiles((dir, name) -> name.endsWith(".json"));
            int count = files != null ? files.length : 0;
            sender.sendMessage(Component.text("  Stats:    ", NamedTextColor.GRAY)
                    .append(Component.text(count + " player files found", NamedTextColor.WHITE)));
        } else {
            sender.sendMessage(Component.text("  Stats:    ", NamedTextColor.GRAY)
                    .append(Component.text("stats folder not found", NamedTextColor.RED)));
        }

        sender.sendMessage(Component.text(""));
    }
}

