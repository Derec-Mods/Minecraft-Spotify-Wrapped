package io.github.derexxd.minecraftWrapped;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.Map;

public class MinecraftWrapped extends JavaPlugin {

    private BukkitTask uploadTask;

    @Override
    public void onEnable() {
        this.getComponentLogger().info(Component.empty());
        this.getComponentLogger().info(Component.text("  |_______|                             ", NamedTextColor.GREEN));
        this.getComponentLogger().info(
                Component.text(
                        "  | Derex |     Minecraft Wrapped v" + getDescription().getVersion(),
                        NamedTextColor.GREEN
                )
        );
        this.getComponentLogger().info(
                Component.text(
                        "  |_______|     Running on " + Bukkit.getName() + " - " + Bukkit.getVersion(),
                        NamedTextColor.GREEN
                )
        );
        this.getComponentLogger().info(Component.empty());

        saveDefaultConfig();

        WrappedCommand cmd = new WrappedCommand(this);
        getCommand("wrapped").setExecutor(cmd);
        getCommand("wrapped").setTabCompleter(cmd);

        String key = getConfig().getString("api-key", "");
        if (WrappedAPI.validateKeyFormat(key)) {
            startScheduledUpload();
            int interval = getConfig().getInt("upload-interval-minutes", 360);
            getLogger().info("Enabled — uploading every " + interval + " minutes");
        } else {
            getLogger().warning("No valid API key configured.");
            getLogger().warning("Run /wrapped setkey in-game or from console to set up.");
        }
    }

    @Override
    public void onDisable() {
        if (uploadTask != null) {
            uploadTask.cancel();
            uploadTask = null;
        }
    }

    void startScheduledUpload() {
        if (uploadTask != null) {
            uploadTask.cancel();
        }

        int intervalMinutes = getConfig().getInt("upload-interval-minutes", 360);
        long intervalTicks = intervalMinutes * 60L * 20L;

        uploadTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::runUpload, 1200L, intervalTicks);
    }

    private void runUpload() {
        String key = getConfig().getString("api-key", "");
        if (!WrappedAPI.validateKeyFormat(key)) {
            getLogger().warning("Skipping scheduled upload — no valid API key");
            return;
        }

        File statsFolder = StatsCollector.findStatsFolder(getLogger());
        if (statsFolder == null) return;

        Map<String, String> usercache = StatsCollector.loadUsercache(getLogger());
        List<PlayerData> players = StatsCollector.collectStats(statsFolder, usercache, getLogger());
        if (players.isEmpty()) return;

        try {
            String source = getConfig().getString("source", "plugin");
            PayloadResult payload = PayloadBuilder.build(players, source);
            UploadResult result = WrappedAPI.uploadSnapshot(payload.data, payload.hash, key, source, getLogger());

            if (result.success) {
                if (result.duplicate) {
                    getLogger().info("Stats unchanged since last upload");
                } else {
                    getLogger().info("Uploaded stats for " + players.size() + " players");
                    if (result.slug != null) {
                        getLogger().info("View at https://wrapped.gg/" + result.slug);
                    }
                }

                if (getConfig().getBoolean("upload-icon", true)) {
                    File icon = new File("server-icon.png");
                    if (icon.exists()) {
                        WrappedAPI.uploadIcon(icon, key, getLogger());
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("Scheduled upload failed: " + e.getMessage());
        }
    }
}
