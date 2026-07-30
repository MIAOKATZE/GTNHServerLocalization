package ru.glitchless.serverlocalization;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;
import ru.glitchless.serverlocalization.config.ServerLocalizationConfig;
import ru.glitchless.serverlocalization.lang.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod(modid = ServerLocalization.MODID,
        name = ServerLocalization.NAME,
        version = ServerLocalization.VERSION,
        acceptableRemoteVersions = "*")
public class ServerLocalization {
    public static final String MODID = "serverlocalization";
    public static final String NAME = "Server Localization";
    /**
     * 版本号由 Gradle 构建时生成的 {@link Tags} 类提供，兼容 GTNH convention。
     */
    public static final String VERSION = Tags.VERSION;

    public static Logger logger;
    private static List<ILangChanger> langChangerList = new ArrayList<>();

    public static void addLangChanger(ILangChanger langChanger) {
        langChangerList.add(langChanger);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        ServerLocalizationConfig.init(event.getSuggestedConfigurationFile());
        addLangChanger(new VanillaChanger());
        addLangChanger(new OtherModChanger());
        addLangChanger(new TxLoaderChanger());
        addLangChanger(new GTNHLocalization());
        addLangChanger(new IC2LangChanger());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        final String lang = ServerLocalizationConfig.lang;
        for (ILangChanger langChanger : langChangerList) {
            try {
                logger.info("Applying lang for " + langChanger.getClass().getSimpleName());
                langChanger.changeLanguage(logger, lang);
            } catch (LangNotFoundException ex) {
                logger.error("Failed " + langChanger.getClass().getSimpleName(), ex);
            }
        }

        // Test that translations were actually injected
        try {
            Map<String, String> languageList = StringTranslateHelper.getLanguageMap(logger);

            logger.info("=== Translation Test ===");
            logger.info("Total translations in StringTranslate: " + languageList.size());

            String[] testKeys = {"death.fell.accident.generic", "book.pageIndicator", "entity.LavaSlime.name", "dreamcraft.welcome.welcome"};
            for (String key : testKeys) {
                String translated = languageList.get(key);
                logger.info("Test '" + key + "': " + (translated != null ? translated : "NOT FOUND"));
            }
            logger.info("If you see Chinese above, server localization is working!");
        } catch (Exception e) {
            logger.error("Failed to test translations", e);
        }
    }
}
