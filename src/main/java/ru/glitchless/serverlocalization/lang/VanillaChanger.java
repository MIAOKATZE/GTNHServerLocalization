package ru.glitchless.serverlocalization.lang;

import org.apache.logging.log4j.Logger;
import ru.glitchless.serverlocalization.downloader.AssetsHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

public class VanillaChanger implements ILangChanger {
    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        final File assetFile = AssetsHelper.getLangFile(logger, lang);
        if (assetFile == null) {
            throw new LangNotFoundException(lang);
        }

        final InputStream is;
        try {
            is = new FileInputStream(assetFile);
        } catch (FileNotFoundException e) {
            throw new LangNotFoundException(lang);
        }

        try {
            // Use UTF-8 encoding for Minecraft language files
            injectLanguage(new InputStreamReader(is, "UTF-8"), lang, logger);
        } catch (Exception e) {
            logger.error("Failed to read language file with UTF-8 encoding", e);
            throw new LangNotFoundException(lang);
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                logger.error(ex);
            }
        }
        logger.info("Change lang to " + lang + " for vanilla done!");
    }

    private void injectLanguage(Reader reader, String lang, Logger logger) {
        try {
            Properties properties = new Properties();

            // Load properties using the UTF-8 reader
            properties.load(reader);
            logger.info("Properties loaded, size: " + properties.size());

            // Log first few properties for debugging
            int count = 0;
            for (String key : properties.stringPropertyNames()) {
                if (count++ < 5) {
                    logger.info("Example translation: " + key + " = " + properties.getProperty(key));
                }
            }

            // Inject all translations into StringTranslate through the shared helper
            StringTranslateHelper.injectProperties(properties, logger);
        } catch (Exception e) {
            logger.error("Failed to inject language", e);
            throw new RuntimeException(e);
        }
    }
}
