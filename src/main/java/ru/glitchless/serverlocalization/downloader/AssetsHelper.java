package ru.glitchless.serverlocalization.downloader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.Logger;
import ru.glitchless.serverlocalization.proxy.ISideProxy;
import ru.glitchless.serverlocalization.utils.HttpUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssetsHelper {
    private static final String VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final JsonParser parser = new JsonParser();

    private static String getMinecraftVersion() {
        return ISideProxy.getInstance().getMinecraftVersion();
    }

    private static String getVersionUrl(Logger logger) throws Exception {
        final String version = getMinecraftVersion();
        logger.info("Start download assets for Minecraft " + version);
        final String versionList = HttpUtils.httpGet(VERSION_MANIFEST);
        final JsonObject versionInformation = parser.parse(versionList).getAsJsonObject();
        final JsonArray versions = versionInformation.getAsJsonArray("versions");

        final Map<String, String> versionsToUrlMap = new HashMap<>();
        // and then, for each version...
        for (JsonElement element : versions) {
            JsonObject versionObject = element.getAsJsonObject();
            String id = versionObject.get("id").getAsString();
            versionsToUrlMap.put(id, versionObject.get("url").getAsString());
        }

        final String versionUrl = versionsToUrlMap.get(version);
        if (versionUrl == null) {
            logger.error("Not found version " + version + " in " + versionList);
            throw new RuntimeException("Not found version " + version + " assets");
        }
        return versionUrl;
    }

    private static String getAssetsUrl(Logger logger) throws Exception {
        final String versionUrl = getVersionUrl(logger);
        logger.info("Version url:" + versionUrl);
        final String versionJson = HttpUtils.httpGet(versionUrl);
        final JsonObject version = parser.parse(versionJson).getAsJsonObject();
        final JsonObject assetsIndex = version.getAsJsonObject("assetIndex");
        return assetsIndex.get("url").getAsString();
    }

    private static Map<String, Asset> getAssets(Logger logger) throws Exception {
        final String assetsUrl = getAssetsUrl(logger);
        logger.info("Assets url:" + assetsUrl);
        final String assetsJson = HttpUtils.httpGet(assetsUrl);
        final JsonObject assets = parser.parse(assetsJson).getAsJsonObject();
        final JsonObject assetsMap = assets.getAsJsonObject("objects");

        logger.info("Found " + assetsMap.entrySet().size() + " assets");

        final Map<String, Asset> toExit = new HashMap<>();

        for (Map.Entry<String, JsonElement> objectEntry : assetsMap.entrySet()) {
            toExit.put(objectEntry.getKey(), new Asset(objectEntry.getValue().getAsJsonObject(), objectEntry.getKey()));
        }
        return toExit;
    }

    @Nullable
    public static File getLangFile(Logger logger, String lang) {
        final String langPath = "minecraft/lang/" + lang.toLowerCase() + ".lang";
        final File assetsFile = ISideProxy.getInstance().getFile("assets/" + langPath);

        logger.info("Looking for language file: " + langPath);
        logger.info("Expected file path: " + assetsFile.getAbsolutePath());

        if (assetsFile.exists()) {
            logger.info("Language file already exists: " + assetsFile.getAbsolutePath());
            return assetsFile;
        }

        logger.info("Language file not found locally, trying to download from assets...");

        final Map<String, Asset> assets;
        try {
            assets = getAssets(logger);
        } catch (Exception ex) {
            logger.error("Failed to get assets list", ex);
            return null;
        }

        Asset langAsset = assets.get(langPath);
        if (langAsset == null) {
            // Try case-insensitive matching
            String langLower = lang.toLowerCase();
            logger.info("Exact match failed, trying case-insensitive search for: " + langLower);
            Pattern pattern = Pattern.compile("minecraft/lang/(.+)\\.lang", Pattern.CASE_INSENSITIVE);
            List<String> allLangFile = new ArrayList<>();
            for (String path : assets.keySet()) {
                final Matcher matcher = pattern.matcher(path);
                if (!matcher.find()) {
                    continue;
                }
                String langCode = matcher.group(1);
                allLangFile.add(langCode);
                if (langCode.equalsIgnoreCase(langLower) || langCode.equalsIgnoreCase(lang)) {
                    logger.info("Found match: " + path);
                    langAsset = assets.get(path);
                    break;
                }
            }
            if (langAsset == null) {
                logger.error("Can't find " + lang + " file. All available lang files: " + Arrays.toString(allLangFile.toArray()));
                return null;
            }
        }

        logger.info("Downloading language file from: " + langAsset.getUrl());
        File downloadedFile = HttpUtils.downloadFile(langAsset.getUrl(), assetsFile);
        if (downloadedFile != null) {
            logger.info("Successfully downloaded language file to: " + downloadedFile.getAbsolutePath());
        } else {
            logger.error("Failed to download language file");
        }
        return downloadedFile;
    }
}
