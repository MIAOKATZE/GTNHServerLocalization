package ru.glitchless.serverlocalization.lang;

import cpw.mods.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow repairs for the six zh_CN argument-order defects reported on 2026-11-07. */
public final class LanguageFormatCorrections {
    private static final Pattern FORMAT = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[a-zA-Z%]");
    private static final Map<String, Repair> REPAIRS = new LinkedHashMap<>();

    static {
        add("commands.network.syphon.success", "从 %s 处成功抽取 %d LP.", "%2$s", "%1$s");
        add("commands.network.add.success", "为 %s 成功添加 %d LP.", "%2$s", "%1$s");
        add("commands.soulnetwork.subtract.success", "成功从 %s 的灵魂网络中减去 %dLP !", "%2$s", "%1$s");
        add("salisarcana:command.forget-aspect.success", "§9成功：§r %s的要素列表中有%d个要素被变更", "%2$s", "%1$s");
        addWormhole("transferred", "传输");
        addWormhole("received", "接收");
    }

    private LanguageFormatCorrections() {}

    private static void add(String key, String broken, String... formats) {
        REPAIRS.put(key, new Repair(broken, formats));
    }

    private static void addWormhole(String key, String verb) {
        add("GT5U.infodata.wormhole_generator." + key,
                "%s仓（%,dA %s）在最近%d秒内" + verb + "了§b%,d§fEU（相当于%,dA），效率为%.3f%%",
                "%1$s", "%2$,d", "%3$s", "%7$d", "%4$,d", "%5$,d", "%6$.3f");
    }

    public static void apply(String lang, Logger logger) {
        if (!"zh_CN".equalsIgnoreCase(lang) || !FMLCommonHandler.instance().getSide().isServer()) {
            return;
        }
        Map<String, String> current = StringTranslateHelper.getLanguageMap(logger);
        Map<String, String> corrections = new LinkedHashMap<>();
        for (String key : REPAIRS.keySet()) {
            String value = current.get(key);
            if (value != null) {
                String corrected = correctTemplate(key, value);
                if (!value.equals(corrected)) {
                    corrections.put(key, corrected);
                }
            }
        }
        // Numeric wormhole arguments are verified at the String.format call site; retain grouping and .3f.
        StringTranslateHelper.injectPreparedTranslations(corrections, logger);
    }

    static String correctTemplate(String key, String value) {
        Repair repair = REPAIRS.get(key);
        // Match both the original pack text and its vanilla-normalized form; leave future/user edits intact.
        if (repair == null || (!repair.broken.equals(value)
                && !StringTranslateHelper.normalizeFormat(repair.broken).equals(value))) {
            return value;
        }
        Matcher matcher = FORMAT.matcher(value);
        StringBuffer corrected = new StringBuffer();
        int index = 0;
        while (matcher.find()) {
            String replacement = "%%".equals(matcher.group()) ? "%%" : repair.formats[index++];
            matcher.appendReplacement(corrected, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(corrected);
        return corrected.toString();
    }

    private static final class Repair {
        final String broken;
        final String[] formats;

        Repair(String broken, String[] formats) {
            this.broken = broken;
            this.formats = formats;
        }
    }
}
