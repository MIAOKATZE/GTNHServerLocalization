package ru.glitchless.serverlocalization.utils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class HttpUtils {
    private HttpUtils() {}

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "MinecraftServerLocalization/1.7.10");
        return connection;
    }

    public static String httpGet(String url) throws Exception {
        HttpURLConnection connection = open(url);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append('\n');
            }
            return response.toString();
        } finally {
            connection.disconnect();
        }
    }

    /** A failed download must never leave a partial file that the next startup accepts as cached language. */
    @Nullable
    public static File downloadFile(String url, File dest) {
        HttpURLConnection connection = null;
        Path temporary = null;
        try {
            Path target = dest.toPath().toAbsolutePath();
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".serverlocalization-", ".tmp");
            connection = open(url);
            try (InputStream input = connection.getInputStream()) {
                long bytes = Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                long expected = connection.getContentLengthLong();
                if (bytes == 0 || (expected >= 0 && bytes != expected)) {
                    throw new IOException("Incomplete language download: " + bytes + " / " + expected);
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return dest;
        } catch (IOException e) {
            System.err.println("[serverlocalization] Failed to download language: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A stale temp file is never used as a language cache.
                }
            }
        }
    }
}
