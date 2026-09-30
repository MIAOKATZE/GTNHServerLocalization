package ru.glitchless.serverlocalization.lang;

import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GTNHLocalization implements ILangChanger {
    // Pattern for quoted format: S:"key"=value
    private static final Pattern CONFIG_PATTERN_QUOTED = Pattern.compile("^\\s*S:\"([^\"]+)\"=(.*)$");
    // Pattern for unquoted format: S:key=value
    private static final Pattern CONFIG_PATTERN_UNQUOTED = Pattern.compile("^\\s*S:([a-zA-Z0-9_.\\-\\[\\]]+)=(.*)$");
    // Fallback pattern for plain key=value (without S: prefix)
    private static final Pattern CONFIG_PATTERN_PLAIN = Pattern.compile("^\\s*([a-zA-Z0-9_.\\-\\[\\]/:]+)=(.*)$");

    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        logger.info("GTNHLocalization: Starting to load language " + lang + " from GregTech config file...");

        // Search paths in priority order
        String fileName = "GregTech_" + lang + ".lang";
        File[] candidates = {
                new File(fileName),
                new File("config", "GTNewHorizons" + File.separator + fileName),
                new File("config", fileName)
        };

        File configFile = null;
        for (File candidate : candidates) {
            if (candidate.exists()) {
                configFile = candidate;
                break;
            }
        }

        if (configFile == null) {
            logger.info("GTNHLocalization: GregTech config file not found for language " + lang);
            return;
        }

        try {
            logger.info("GTNHLocalization: Found GregTech config file: " + configFile.getAbsolutePath());
            loadConfigFile(configFile, logger);
        } catch (Exception e) {
            logger.error("Failed to load GregTech config file: " + configFile.getAbsolutePath(), e);
        }
    }

    private void loadConfigFile(File configFile, Logger logger) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(configFile), "UTF-8"))) {

            // 收集后统一走 StringTranslateHelper 注入（含 fallback 表）
            Map<String, String> collected = new java.util.HashMap<>();

            String line;
            int count = 0;
            int inLanguagefileBlock = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Check for languagefile block start
                if (line.equals("languagefile {")) {
                    inLanguagefileBlock++;
                    continue;
                }

                // Check for languagefile block end
                if (line.equals("}") && inLanguagefileBlock > 0) {
                    inLanguagefileBlock--;
                    continue;
                }

                String key = null;
                String value = null;

                // Try to match quoted format: S:"key"=value
                Matcher matcherQuoted = CONFIG_PATTERN_QUOTED.matcher(line);
                if (matcherQuoted.matches()) {
                    key = matcherQuoted.group(1);
                    value = matcherQuoted.group(2).trim();
                }

                // Try to match unquoted format: S:key=value
                if (key == null) {
                    Matcher matcherUnquoted = CONFIG_PATTERN_UNQUOTED.matcher(line);
                    if (matcherUnquoted.matches()) {
                        key = matcherUnquoted.group(1);
                        value = matcherUnquoted.group(2).trim();
                    }
                }

                // Fallback: plain key=value
                if (key == null) {
                    Matcher matcherPlain = CONFIG_PATTERN_PLAIN.matcher(line);
                    if (matcherPlain.matches()) {
                        key = matcherPlain.group(1);
                        value = matcherPlain.group(2).trim();
                    }
                }

                if (key != null) {
                    // Remove braces if present in value
                    if (value.startsWith("{") && value.endsWith("}")) {
                        value = value.substring(1, value.length() - 1).trim();
                    }

                    collected.put(key, value);
                    count++;
                }
            }

            StringTranslateHelper.injectTranslations(collected, logger);
            logger.info("GTNHLocalization: Loaded " + count + " translations from " + configFile.getAbsolutePath());
        }
    }
}
