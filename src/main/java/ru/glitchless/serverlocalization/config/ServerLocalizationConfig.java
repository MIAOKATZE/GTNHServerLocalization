package ru.glitchless.serverlocalization.config;

import net.minecraftforge.common.config.Configuration;
import ru.glitchless.serverlocalization.ServerLocalization;
import java.io.File;

public class ServerLocalizationConfig {
    public static String lang = "zh_CN";
    public static boolean resolveChatTranslations = true;

    public static void init(File configFile) {
        Configuration config = new Configuration(configFile);
        try {
            config.load();
            lang = config.getString("lang", Configuration.CATEGORY_GENERAL, "zh_CN", "server lang");
            resolveChatTranslations = config.getBoolean("resolveChatTranslations", Configuration.CATEGORY_GENERAL, true,
                "Resolve ChatComponentTranslation server-side before sending (GTNH 2.9.0 + custom mods)");
        } catch (Exception e) {
            ServerLocalization.logger.error("Failed to load config", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
