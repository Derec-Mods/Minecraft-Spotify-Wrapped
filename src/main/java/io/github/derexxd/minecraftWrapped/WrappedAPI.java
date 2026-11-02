package io.github.derexxd.minecraftWrapped;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.logging.Logger;

public class WrappedAPI {

    private static final String BASE_URL = "https://wrapped.gg";
    private static final int CONNECT_TIMEOUT = 20_000;
    private static final int READ_TIMEOUT = 60_000;

    public static UploadResult uploadSnapshot(byte[] payload, String idempotencyKey, String apiKey, String source, Logger logger) {
        try {
            HttpURLConnection conn = openConnection(BASE_URL + "/v1/snapshots", apiKey);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-ndjson");
            conn.setRequestProperty("Idempotency-Key", idempotencyKey);
            conn.setRequestProperty("X-Wrapped-Source", source);
            conn.setFixedLengthStreamingMode(payload.length);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int status = conn.getResponseCode();
            String body = readResponse(conn);
            JsonObject json = parseJson(body);

            if (status >= 200 && status < 300) {
                String snapshotId = getStringOrNull(json, "snapshot_id");
                String slug = getStringOrNull(json, "slug");
                boolean duplicate = json != null && json.has("duplicate");
                logger.info("Upload accepted (HTTP " + status + ")");
                return new UploadResult(true, status, snapshotId, slug, duplicate, null);
            }

            String message = getStringOrNull(json, "message");
            String error = getStringOrNull(json, "error");
            String reason = buildErrorMessage(status, error, message);
            logger.warning(reason);
            return new UploadResult(false, status, null, null, false, reason);

        } catch (Exception e) {
            String reason = "Connection failed: " + e.getMessage();
            logger.warning(reason);
            return new UploadResult(false, 0, null, null, false, reason);
        }
    }

    public static boolean uploadIcon(File iconFile, String apiKey, Logger logger) {
        try {
            byte[] iconBytes = Files.readAllBytes(iconFile.toPath());
            if (iconBytes.length > 524_288) {
                logger.warning("server-icon.png is over 512 KB, skipping");
                return false;
            }

            HttpURLConnection conn = openConnection(BASE_URL + "/v1/logo", apiKey);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "image/png");
            conn.setFixedLengthStreamingMode(iconBytes.length);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(iconBytes);
            }

            int status = conn.getResponseCode();
            String body = readResponse(conn);

            if (status >= 200 && status < 300) {
                if (body.contains("\"skipped\"")) {
                    logger.info("Server icon already set in panel, kept existing");
                } else {
                    logger.info("Server icon uploaded");
                }
                return true;
            }

            logger.warning("Icon upload failed (HTTP " + status + ")");
            return false;

        } catch (Exception e) {
            logger.warning("Icon upload failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean validateKeyFormat(String key) {
        if (key == null || !key.startsWith("wgg_live_")) return false;
        if (key.length() < 20 || key.length() > 128) return false;
        return key.matches("[A-Za-z0-9_-]+");
    }

    private static HttpURLConnection openConnection(String url, String apiKey) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("User-Agent", "MinecraftWrapped-Plugin");
        return conn;
    }

    private static String readResponse(HttpURLConnection conn) {
        try {
            var stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) return "";
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            return "";
        }
    }

    private static JsonObject parseJson(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String getStringOrNull(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return null;
        return json.get(key).getAsString();
    }

    private static String buildErrorMessage(int status, String error, String message) {
        return switch (status) {
            case 401, 403 -> "API key was not accepted. Check your key in config.yml or run /wrapped setkey";
            case 413 -> "Payload too large — something other than stats was picked up";
            case 429 -> "Rate limited — too many uploads today. " + (message != null ? message : "Try again tomorrow");
            default -> {
                if (status >= 500) yield "wrapped.gg had a server-side issue (HTTP " + status + "). Try again shortly";
                yield "Upload refused (HTTP " + status + ")" + (message != null ? ": " + message : "");
            }
        };
    }
}

