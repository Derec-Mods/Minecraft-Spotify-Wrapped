package io.github.derexxd.minecraftWrapped;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class StatsCollector {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public static File findStatsFolder(Logger logger) {
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();

        File newPath = new File(worldFolder, "players" + File.separator + "stats");
        if (newPath.isDirectory()) {
            logger.info("Found stats folder (26.x+ layout): " + newPath.getPath());
            return newPath;
        }

        File legacyPath = new File(worldFolder, "stats");
        if (legacyPath.isDirectory()) {
            logger.info("Found stats folder (legacy layout): " + legacyPath.getPath());
            return legacyPath;
        }

        logger.warning("Could not find a stats folder in " + worldFolder.getPath());
        return null;
    }

    public static Map<String, String> loadUsercache(Logger logger) {
        Map<String, String> names = new HashMap<>();
        File usercache = new File("usercache.json");
        if (!usercache.exists()) {
            logger.warning("usercache.json not found — players will show as UUIDs on wrapped.gg");
            return names;
        }

        try (FileReader reader = new FileReader(usercache)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.has("uuid") && entry.has("name")) {
                    names.put(entry.get("uuid").getAsString(), entry.get("name").getAsString());
                }
            }
            logger.info("Loaded " + names.size() + " player names from usercache.json");
        } catch (Exception e) {
            logger.warning("Failed to read usercache.json: " + e.getMessage());
        }

        return names;
    }

    public static List<PlayerData> collectStats(File statsFolder, Map<String, String> usercache, Logger logger) {
        List<PlayerData> players = new ArrayList<>();
        File[] files = statsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            logger.warning("No stat files found in " + statsFolder.getPath());
            return players;
        }

        int skipped = 0;
        for (File file : files) {
            String uuid = file.getName().replace(".json", "");
            if (!UUID_PATTERN.matcher(uuid).matches()) {
                skipped++;
                continue;
            }

            try {
                String content = Files.readString(file.toPath());
                JsonObject doc = JsonParser.parseString(content).getAsJsonObject();
                Map<String, Long> counters = parseCounters(doc);
                if (counters.isEmpty()) {
                    continue;
                }

                String name = usercache.get(uuid);
                players.add(new PlayerData(uuid, name, counters));
            } catch (Exception e) {
                logger.warning("Failed to read stats for " + uuid + ": " + e.getMessage());
            }
        }

        if (skipped > 0) {
            logger.warning("Skipped " + skipped + " files not named with a valid UUID (pre-1.7.6 format)");
        }
        logger.info("Collected stats for " + players.size() + " players from " + files.length + " files");
        return players;
    }

    private static Map<String, Long> parseCounters(JsonObject doc) {
        Map<String, Long> counters = new LinkedHashMap<>();

        if (doc.has("stats") && doc.get("stats").isJsonObject()) {
            JsonObject stats = doc.getAsJsonObject("stats");
            for (Map.Entry<String, JsonElement> catEntry : stats.entrySet()) {
                String category = stripMinecraft(catEntry.getKey());
                if (!catEntry.getValue().isJsonObject()) continue;
                JsonObject entries = catEntry.getValue().getAsJsonObject();
                for (Map.Entry<String, JsonElement> statEntry : entries.entrySet()) {
                    String stat = stripMinecraft(statEntry.getKey());
                    try {
                        long value = statEntry.getValue().getAsLong();
                        if (value != 0) {
                            counters.put(category + "/" + stat, value);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } else {
            for (Map.Entry<String, JsonElement> entry : doc.entrySet()) {
                if (!entry.getKey().startsWith("stat.")) continue;
                JsonElement val = entry.getValue();
                if (val.isJsonObject() && val.getAsJsonObject().has("value")) {
                    val = val.getAsJsonObject().get("value");
                }
                if (val.isJsonPrimitive() && val.getAsJsonPrimitive().isNumber()) {
                    long value = val.getAsLong();
                    if (value != 0) {
                        counters.put(entry.getKey(), value);
                    }
                }
            }
        }

        return counters;
    }

    private static String stripMinecraft(String key) {
        return key.startsWith("minecraft:") ? key.substring(10) : key;
    }
}

