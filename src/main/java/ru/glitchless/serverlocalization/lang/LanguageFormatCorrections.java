package ru.glitchless.serverlocalization.lang;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.Logger;
import ru.glitchless.serverlocalization.config.ServerLocalizationConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact-match overrides applied after all language loaders, without vanilla format normalization. */
public final class LanguageFormatCorrections {
    private static final String DEFAULT_FILE = "00-defaults.json";
    private static final String DEFAULT_RESOURCE = "/assets/serverlocalization/overrides/" + DEFAULT_FILE;

    private LanguageFormatCorrections() {}

    public static void apply(String lang, Logger logger) {
        apply(lang, ServerLocalizationConfig.overridesDirectory.toPath(), logger);
    }

    static void apply(String lang, Path directory, Logger logger) {
        if (!FMLCommonHandler.instance().getSide().isServer()) {
            return;
        }
        Map<String, String> snapshot = new LinkedHashMap<>(StringTranslateHelper.getLanguageMap(logger));
        StringTranslateHelper.injectPreparedTranslations(loadCorrections(directory, lang, snapshot, logger), logger);
    }

    /** Later files/entries win, but every rule is checked against the same unmodified language snapshot. */
    static Map<String, String> loadCorrections(Path directory, String lang, Map<String, String> snapshot, Logger logger) {
        Map<String, String> corrections = new LinkedHashMap<>();
        try {
            initializeDirectory(directory);
            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json")) {
                        files.add(path);
                    }
                }
            }
            files.sort(Comparator.comparing(path -> path.getFileName().toString()));
            for (Path file : files) {
                loadFile(file, lang, snapshot, corrections, logger);
            }
        } catch (IOException | RuntimeException ex) {
            warn(logger, directory, "directory", ex);
        }
        return corrections;
    }

    private static void initializeDirectory(Path directory) throws IOException {
        Files.createDirectories(directory.toAbsolutePath().getParent());
        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException ex) {
            // Existing directories are user-owned: deleted defaults and existing files are never restored.
            return;
        }
        try (InputStream defaults = LanguageFormatCorrections.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (defaults == null) {
                throw new IOException("Missing bundled resource " + DEFAULT_RESOURCE);
            }
            Files.copy(defaults, directory.resolve(DEFAULT_FILE));
        }
    }

    private static void loadFile(Path file, String lang, Map<String, String> snapshot,
            Map<String, String> corrections, Logger logger) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            JsonElement enabled = root.get("enabled");
            if (enabled != null) {
                if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException("enabled must be a boolean");
                }
                if (!enabled.getAsBoolean()) {
                    return;
                }
            }
            JsonElement rules = root.get("rules");
            if (rules == null || !rules.isJsonArray()) {
                throw new IllegalArgumentException("rules must be an array");
            }
            int index = 0;
            for (JsonElement element : rules.getAsJsonArray()) {
                int entry = index++;
                try {
                    JsonObject rule = element.getAsJsonObject();
                    String locale = requiredString(rule, "locale");
                    String key = requiredString(rule, "key");
                    String expected = requiredString(rule, "expected");
                    String replacement = requiredString(rule, "replacement");
                    String current = snapshot.get(key);
                    if (locale.equalsIgnoreCase(lang) && current != null
                            && (expected.equals(current) || StringTranslateHelper.normalizeFormat(expected).equals(current))) {
                        corrections.put(key, replacement);
                    }
                } catch (RuntimeException ex) {
                    warn(logger, file, "rules[" + entry + "]", ex);
                }
            }
        } catch (IOException | RuntimeException ex) {
            warn(logger, file, "file", ex);
        }
    }

    private static String requiredString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static void warn(Logger logger, Path file, String entry, Exception ex) {
        if (logger != null) {
            logger.warn("Language override " + file + " (" + entry + "): " + ex.getMessage());
        }
    }
}
