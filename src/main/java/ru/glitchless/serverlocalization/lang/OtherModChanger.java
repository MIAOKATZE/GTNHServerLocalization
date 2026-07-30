package ru.glitchless.serverlocalization.lang;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class OtherModChanger implements ILangChanger {
    private String currentLang;

    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        this.currentLang = lang;
        logger.info("OtherModChanger: Starting to load language '" + lang + "' for all mods...");
        int modCount = 0;
        int skippedCount = 0;
        int successCount = 0;

        for (ModContainer container : Loader.instance().getActiveModList()) {
            modCount++;
            try {
                File modFile = container.getSource();
                if (modFile == null || !modFile.exists()) {
                    skippedCount++;
                    continue;
                }

                // Debug: print info for dreamcraft mod
                if ("dreamcraft".equals(container.getModId())) {
                    logger.info("DEBUG: dreamcraft mod - source: " + modFile.getAbsolutePath() + ", exists: " + modFile.exists());
                    logger.info("DEBUG: dreamcraft mod - will try to load language: '" + lang + "'");
                }

                boolean loaded = loadLanguageFromSource(modFile, container.getModId(), lang, logger);
                if (loaded) {
                    successCount++;
                }
            } catch (Exception e) {
                logger.error("Failed to load language for mod " + container.getModId(), e);
                // 单个 mod 加载失败记录日志但继续处理后续 mod
            }
        }

        logger.info("OtherModChanger: Checked " + modCount + " mods, skipped " + skippedCount + ", successfully loaded " + successCount + " language files.");
    }

    /**
     * 从模组来源加载语言文件。支持 jar 与目录两种来源（目录常见于开发环境）。
     */
    private boolean loadLanguageFromSource(File source, String modId, String lang, Logger logger) throws Exception {
        if (source.isDirectory()) {
            return loadLanguageFromDirectory(source, modId, lang, logger);
        } else if (source.getName().endsWith(".jar")) {
            return loadLanguageFromJar(source, modId, lang, logger);
        }
        return false;
    }

    /**
     * 从目录结构加载语言文件。会在 assets/{modId}/lang/ 与 assets/{modIdLower}/lang/ 下搜索。
     */
    private boolean loadLanguageFromDirectory(File dir, String modId, String lang, Logger logger) {
        String modIdLower = modId.toLowerCase();
        String[] assetDirs = { "assets/" + modId + "/lang/", "assets/" + modIdLower + "/lang/" };

        boolean loaded = false;
        for (String assetDir : assetDirs) {
            File langDir = new File(dir, assetDir);
            if (!langDir.exists() || !langDir.isDirectory()) {
                continue;
            }

            // 优先精确匹配目标语言
            File exactMatch = findFileCaseInsensitive(langDir, lang + ".lang");
            if (exactMatch != null) {
                try {
                    loadLanguageFile(exactMatch, logger, modId);
                    loaded = true;
                } catch (Exception e) {
                    logger.error("Failed to load language file: " + exactMatch.getAbsolutePath(), e);
                }
            }

            // 兜底扫描：加载该目录下所有 .lang 文件
            File[] langFiles = langDir.listFiles();
            if (langFiles != null) {
                for (File file : langFiles) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".lang")
                            && !file.equals(exactMatch)) {
                        try {
                            loadLanguageFile(file, logger, modId);
                            loaded = true;
                        } catch (Exception e) {
                            logger.error("Failed to load language file: " + file.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }
        return loaded;
    }

    /**
     * 在指定目录中按大小写不敏感的方式查找文件名。
     */
    private File findFileCaseInsensitive(File dir, String fileName) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().equalsIgnoreCase(fileName)) {
                return file;
            }
        }
        return null;
    }

    /**
     * 从 jar 文件中加载语言文件。
     */
    private boolean loadLanguageFromJar(File jarFile, String modId, String lang, Logger logger) throws Exception {
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            // Try different case variants for the language file
            String langLower = lang.toLowerCase();
            String langUpper = lang.toUpperCase();
            String langProper = langLower.substring(0, 2) + "_" + langUpper.substring(2); // zh_CN
            String modIdLower = modId.toLowerCase();

            String[] possiblePaths = {
                    "assets/" + modId + "/lang/" + langLower + ".lang",      // zh_cn
                    "assets/" + modId + "/lang/" + langUpper + ".lang",      // ZH_CN
                    "assets/" + modId + "/lang/" + langProper + ".lang",     // zh_CN
                    "assets/" + modId + "/lang/" + lang + ".lang",           // original
                    "assets/" + modIdLower + "/lang/" + langLower + ".lang", // lowercase modid
                    "assets/" + modIdLower + "/lang/" + langUpper + ".lang",
                    "assets/" + modIdLower + "/lang/" + langProper + ".lang",
                    "assets/" + modIdLower + "/lang/" + lang + ".lang"
            };

            ZipEntry entry = null;
            String foundPath = null;
            for (String path : possiblePaths) {
                entry = zipFile.getEntry(path);
                if (entry != null) {
                    foundPath = path;
                    break;
                }
            }

            // 兜底扫描：枚举 jar 中 assets/{modId}/lang/ 下的所有 .lang 文件
            List<ZipEntry> fallbackEntries = new ArrayList<>();
            if (entry == null) {
                String[] fallbackPrefixes = {
                        "assets/" + modId + "/lang/",
                        "assets/" + modIdLower + "/lang/"
                };
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.isDirectory()) {
                        continue;
                    }
                    String name = e.getName();
                    for (String prefix : fallbackPrefixes) {
                        if (name.startsWith(prefix) && name.toLowerCase().endsWith(".lang")) {
                            fallbackEntries.add(e);
                            break;
                        }
                    }
                }
            }

            if (entry == null && fallbackEntries.isEmpty()) {
                return false; // No language file found for this mod
            }

            if (entry != null) {
                logger.info("Found language file for mod " + modId + ": " + foundPath);
                try (InputStream is = zipFile.getInputStream(entry)) {
                    injectLanguage(new InputStreamReader(is, "UTF-8"), logger, modId);
                }
            }

            for (ZipEntry fallbackEntry : fallbackEntries) {
                logger.info("Found fallback language file for mod " + modId + ": " + fallbackEntry.getName());
                try (InputStream is = zipFile.getInputStream(fallbackEntry)) {
                    injectLanguage(new InputStreamReader(is, "UTF-8"), logger, modId);
                }
            }

            return true;
        }
    }

    private void loadLanguageFile(File langFile, Logger logger, String modId) throws Exception {
        try (FileInputStream fis = new FileInputStream(langFile);
             InputStreamReader reader = new InputStreamReader(fis, "UTF-8")) {
            injectLanguage(reader, logger, modId);
        }
    }

    private void injectLanguage(InputStreamReader reader, Logger logger, String modId) {
        try {
            Properties properties = new Properties();
            properties.load(reader);

            // Inject all translations through the shared helper
            StringTranslateHelper.injectProperties(properties, logger);

            logger.info("Change lang to " + currentLang + " for " + modId + " done! Injected " + properties.size() + " translations.");
        } catch (Exception e) {
            logger.error("Failed to inject language for " + modId, e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
