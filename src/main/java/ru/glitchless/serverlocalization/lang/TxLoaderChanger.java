package ru.glitchless.serverlocalization.lang;

import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

public class TxLoaderChanger implements ILangChanger {
    private static final String TXLOADER_LOAD_PATH = "config/txloader/load";
    private static final String TXLOADER_FORCELOAD_PATH = "config/txloader/forceload";

    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        logger.info("TxLoaderChanger: Starting to load language " + lang + " from txloader directories...");

        // Load from both directories
        loadFromDirectory(TXLOADER_LOAD_PATH, lang, logger);
        loadFromDirectory(TXLOADER_FORCELOAD_PATH, lang, logger);
    }

    private void loadFromDirectory(String path, String lang, Logger logger) {
        File txLoaderDir = new File(path);
        if (!txLoaderDir.exists()) {
            logger.info("TxLoaderChanger: " + path + " directory not found, skipping.");
            return;
        }

        // Recursively find all .lang files
        findAndLoadLangFiles(txLoaderDir, lang, logger);
    }

    private void findAndLoadLangFiles(File dir, String lang, Logger logger) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                findAndLoadLangFiles(file, lang, logger);
            } else if (file.getName().equals(lang + ".lang") ||
                       file.getName().equalsIgnoreCase(lang + ".lang")) {
                try {
                    loadLanguageFile(file, logger);
                } catch (Exception e) {
                    logger.error("Failed to load language file: " + file.getAbsolutePath(), e);
                }
            }
        }
    }

    private void loadLanguageFile(File langFile, Logger logger) throws Exception {
        try (FileInputStream fis = new FileInputStream(langFile);
             InputStreamReader reader = new InputStreamReader(fis, "UTF-8")) {

            Properties properties = new Properties();
            properties.load(reader);

            // Inject all translations through the shared helper
            StringTranslateHelper.injectProperties(properties, logger);

            logger.info("TxLoaderChanger: Loaded " + properties.size() + " translations from " + langFile.getAbsolutePath());
        }
    }
}
