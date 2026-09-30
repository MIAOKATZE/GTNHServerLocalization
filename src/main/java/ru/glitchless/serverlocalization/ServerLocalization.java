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

        // GTNH 2.9.0（GT5U 5.09.54.183+）：GT 系机器在 GT preInit 末注册时急切求值机器名，
        // 且 GTLanguageManager 在 StatCollector.canTranslate 命中时不再覆盖语言表 ——
        // 必须赶在 GT preInit 之前（GT5U 自排 mod 列表末尾，本 mod preInit 必在其前）
        // 完成注入，机器名等急切求值文本才能拿到目标语言。
        applyLanguageChangers("preInit");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // preInit 注入后，GT5U preInit/postInit 仍会向语言表写入英文键；
        // postInit 二次幂等注入用于反超覆盖这些后写入的英文。
        applyLanguageChangers("postInit");

        // Test that translations were actually injected
        try {
            Map<String, String> languageList = StringTranslateHelper.getLanguageMap(logger);

            logger.info("=== Translation Test ===");
            logger.info("Total translations in StringTranslate: " + languageList.size());

            String[] testKeys = { "death.fell.accident.generic", "book.pageIndicator", "entity.LavaSlime.name",
                    "dreamcraft.welcome.welcome", "gt.blockmachines.basicmachine.e_furnace.tier.01.name" };
            for (String key : testKeys) {
                String translated = languageList.get(key);
                logger.info("Test '" + key + "': " + (translated != null ? translated : "NOT FOUND"));
            }
            logger.info("If you see Chinese above, server localization is working!");
        } catch (Exception e) {
            logger.error("Failed to test translations", e);
        }
    }

    /**
     * 按注册顺序执行全部 lang changer。preInit 与 postInit 各调用一次：
     * preInit 抢在 GT5U 机器注册之前（急切求值路径），postInit 覆盖后续写入的英文（懒解析路径）。
     */
    private void applyLanguageChangers(String phase) {
        final String lang = ServerLocalizationConfig.lang;
        for (ILangChanger langChanger : langChangerList) {
            try {
                logger.info("[" + phase + "] Applying lang for " + langChanger.getClass().getSimpleName());
                langChanger.changeLanguage(logger, lang);
            } catch (LangNotFoundException ex) {
                logger.error("[" + phase + "] Failed " + langChanger.getClass().getSimpleName(), ex);
            }
        }
    }
}
