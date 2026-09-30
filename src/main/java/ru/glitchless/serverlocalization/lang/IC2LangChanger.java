package ru.glitchless.serverlocalization.lang;

import cpw.mods.fml.common.Loader;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class IC2LangChanger implements ILangChanger {
    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        if (!Loader.isModLoaded("IC2")) {
            logger.info("IC2LangChanger: IC2 not loaded, skipping.");
            return;
        }

        InputStream is = null;
        try {
            is = findLanguageInputStream(lang);
            if (is == null) {
                logger.error("IC2LangChanger: Could not find IC2 language file for language " + lang);
                return;
            }
            loadIC2Localization(is, lang, logger);
            logger.info("Change lang to " + lang + " for ic2 done!");
        } catch (Exception ex) {
            logger.error("IC2LangChanger: Failed to load IC2 localization for language " + lang, ex);
            // 读取失败时优雅跳过，不再抛异常中断流程
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    logger.error(ex);
                }
            }
        }
    }

    /**
     * 按优先级查找 IC2 语言文件输入流。
     */
    private InputStream findLanguageInputStream(String lang) {
        List<String> possiblePaths = new ArrayList<>();
        addPathVariants(possiblePaths, "/assets/ic2/lang_ic2/", lang, ".properties");
        addPathVariants(possiblePaths, "/assets/ic2/lang/", lang, ".properties");
        addPathVariants(possiblePaths, "/assets/ic2/lang/", lang, ".lang");

        for (String path : possiblePaths) {
            InputStream is = IC2LangChanger.class.getResourceAsStream(path);
            if (is != null) {
                return is;
            }
        }
        return null;
    }

    /**
     * 为同一目录添加大小写变体路径。
     */
    private void addPathVariants(List<String> paths, String base, String lang, String extension) {
        paths.add(base + lang.toLowerCase() + extension);
        paths.add(base + lang.toUpperCase() + extension);
        paths.add(base + lang + extension);
    }

    private static void loadIC2Localization(InputStream inputStream, String lang, Logger logger) throws IOException {
        Properties properties = new Properties();
        properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        try {
            // 收集后统一走 StringTranslateHelper 注入（含 fallback 表）
            Map<String, String> collected = new java.util.HashMap<>();
            for (Map.Entry<Object, Object> entries : properties.entrySet()) {
                Object key = entries.getKey();
                Object value = entries.getValue();

                if ((key instanceof String) && (value instanceof String)) {
                    String newKey = (String) key;

                    if ((!newKey.startsWith("achievement.")) &&
                            (!newKey.startsWith("itemGroup.")) &&
                            (!newKey.startsWith("death."))) {

                        newKey = "ic2." + newKey;
                    }
                    collected.put(newKey, (String) value);
                }
            }
            StringTranslateHelper.injectTranslations(collected, logger);
            logger.info("Injected " + collected.size() + " IC2 translations");
        } catch (Exception e) {
            logger.error("Failed to inject IC2 language", e);
            throw new IOException(e);
        }
    }
}
