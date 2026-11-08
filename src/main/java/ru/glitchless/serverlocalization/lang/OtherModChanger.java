package ru.glitchless.serverlocalization.lang;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class OtherModChanger implements ILangChanger {
    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        int successCount = 0;
        Set<File> visited = new HashSet<>();
        for (ModContainer container : Loader.instance().getActiveModList()) {
            try {
                File source = container.getSource();
                if (source == null || !source.exists() || !visited.add(source.getCanonicalFile())) {
                    continue;
                }
                if (loadLanguageFromSource(source, container.getModId(), lang, logger)) {
                    successCount++;
                }
            } catch (Exception e) {
                logger.error("Failed to load language for mod " + container.getModId(), e);
            }
        }
        logger.info("OtherModChanger: Loaded language '" + lang + "' from " + successCount + " mod sources.");
    }

    // A jar can own several asset namespaces, none of which must match its FML mod id.
    boolean loadLanguageFromSource(File source, String modId, String lang, Logger logger) throws Exception {
        if (source.isDirectory()) {
            File[] namespaces = new File(source, "assets").listFiles(File::isDirectory);
            if (namespaces == null) {
                return false;
            }
            Arrays.sort(namespaces, Comparator.comparing(File::getName));
            boolean loaded = false;
            for (File namespace : namespaces) {
                File[] files = new File(namespace, "lang").listFiles(
                        file -> file.isFile() && file.getName().equalsIgnoreCase(lang + ".lang"));
                if (files == null) {
                    continue;
                }
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File file : files) {
                    try (InputStream input = new FileInputStream(file)) {
                        injectLanguage(input, logger);
                    }
                    loaded = true;
                }
            }
            return loaded;
        }
        if (!source.getName().endsWith(".jar")) {
            return false;
        }
        try (ZipFile zip = new ZipFile(source)) {
            List<ZipEntry> matches = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String[] path = entry.getName().split("/");
                if (!entry.isDirectory() && path.length == 4 && path[0].equals("assets")
                        && path[2].equals("lang") && path[3].equalsIgnoreCase(lang + ".lang")) {
                    matches.add(entry);
                }
            }
            matches.sort(Comparator.comparing(ZipEntry::getName));
            for (ZipEntry entry : matches) {
                try (InputStream input = zip.getInputStream(entry)) {
                    injectLanguage(input, logger);
                }
            }
            return !matches.isEmpty();
        }
    }

    private void injectLanguage(InputStream input, Logger logger) throws Exception {
        StringTranslateHelper.injectTranslations(
                LanguageFileReader.read(new InputStreamReader(input, StandardCharsets.UTF_8)), logger);
    }
}
