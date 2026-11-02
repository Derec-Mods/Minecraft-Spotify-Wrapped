package io.github.derexxd.minecraftWrapped;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class PayloadBuilder {

    private static final Gson GSON = new Gson();

    public static PayloadResult build(List<PlayerData> players, String source) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (GZIPOutputStream gz = new GZIPOutputStream(buffer)) {
            JsonObject header = new JsonObject();
            header.addProperty("v", 1);
            header.addProperty("snapshot_at", System.currentTimeMillis() / 1000);
            header.addProperty("source", source);
            gz.write((GSON.toJson(header) + "\n").getBytes(StandardCharsets.UTF_8));

            for (PlayerData player : players) {
                JsonObject row = new JsonObject();
                row.addProperty("u", player.uuid);

                JsonObject counters = new JsonObject();
                for (Map.Entry<String, Long> entry : player.counters.entrySet()) {
                    counters.addProperty(entry.getKey(), entry.getValue());
                }
                row.add("c", counters);

                if (player.name != null) {
                    row.addProperty("n", player.name);
                }

                byte[] line = (GSON.toJson(row) + "\n").getBytes(StandardCharsets.UTF_8);
                sha256.update(line);
                gz.write(line);
            }
        }

        return new PayloadResult(buffer.toByteArray(), bytesToHex(sha256.digest()));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}

