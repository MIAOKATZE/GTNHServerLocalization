package ru.glitchless.serverlocalization.lang;

import com.sun.net.httpserver.HttpServer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.IFMLSidedHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.event.ClickEvent;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.glitchless.serverlocalization.ServerLocalization;
import ru.glitchless.serverlocalization.chat.ChatTranslationResolver;
import ru.glitchless.serverlocalization.utils.HttpUtils;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Offline checks against the real compiled Minecraft classes; no world or server is started. */
public final class LocalizationRegressionTest {
    private static final Logger LOGGER = LogManager.getLogger("LocalizationRegressionTest");
    private static final Side[] SIDE = { Side.SERVER };
    private static int assertions;

    public static void main(String[] args) throws Exception {
        // FML's ModClassLoader requires its classes to be loaded by the legacy launch wrapper.
        if (!(LocalizationRegressionTest.class.getClassLoader() instanceof LaunchClassLoader)) {
            String[] paths = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
            URL[] urls = new URL[paths.length];
            for (int i = 0; i < paths.length; i++) {
                urls[i] = new java.io.File(paths[i]).toURI().toURL();
            }
            try (LaunchClassLoader loader = new LaunchClassLoader(urls)) {
                loader.addClassLoaderExclusion("com.sun.net.httpserver.");
                Launch.classLoader = loader;
                Launch.blackboard = new java.util.HashMap<>();
                Thread.currentThread().setContextClassLoader(loader);
                loader.loadClass(LocalizationRegressionTest.class.getName())
                        .getMethod("main", String[].class).invoke(null, (Object) args);
            }
            return;
        }
        Loader.injectData("7", "10", "99", "99", "1.7.10", "9.05", new java.io.File("."), Collections.emptyList());
        Field delegate = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        delegate.setAccessible(true);
        Object previous = delegate.get(FMLCommonHandler.instance());
        delegate.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
                IFMLSidedHandler.class.getClassLoader(), new Class<?>[] { IFMLSidedHandler.class },
                (proxy, method, arguments) -> "getSide".equals(method.getName()) ? SIDE[0] : null));
        try {
            parserAndInjection();
            sourceSelection();
            formatCorrections();
            chatComponents();
            downloadCache();
            clientBoundary();
            System.out.println("LocalizationRegressionTest: " + assertions + " assertions passed");
        } finally {
            delegate.set(FMLCommonHandler.instance(), previous);
        }
    }

    private static void parserAndInjection() throws Exception {
        Map<String, String> parsed = LanguageFileReader.read(new StringReader(
                "\uFEFF#comment\nkey:with spaces= value=more\\n\\u4e2d\\\nnext=second\nbad line\n"));
        equal(" value=more\\n\\u4e2d\\", parsed.get("key:with spaces"));
        equal("second", parsed.get("next"));
        equal(2, parsed.size());
        Map<String, String> input = new LinkedHashMap<>();
        input.put("entity.Thaumcraft.EldritchWarden.name", "%d典狱官%s");
        input.put("entity.Thaumcraft.CultistLeader.name", "%d血腥主教%s");
        input.put("info.cofh.command.killall.success", "已清除%d个实体. (%s)");
        input.put("qa.numeric", "%2$d %3$.2f %,d %%");
        StringTranslateHelper.injectTranslations(input, LOGGER);
        equal("A典狱官B", String.format(StatCollector.translateToLocal(
                "entity.Thaumcraft.EldritchWarden.name"), "A", "B"));
        equal("A血腥主教B", String.format(StatCollector.translateToLocal(
                "entity.Thaumcraft.CultistLeader.name"), "A", "B"));
        equal("已清除40个实体. (test)", new ChatComponentTranslation(
                "info.cofh.command.killall.success", 40, "test").getUnformattedText());
        equal("%2$s %3$s %,d %%", StatCollector.translateToLocal("qa.numeric"));
        equal("%d典狱官%s", input.get("entity.Thaumcraft.EldritchWarden.name"));
        equal(StatCollector.translateToLocal("qa.numeric"), StatCollector.translateToFallback("qa.numeric"));
        Properties properties = new Properties();
        properties.setProperty("qa.properties", "%1$d");
        StringTranslateHelper.injectProperties(properties, LOGGER);
        equal("%1$s", StatCollector.translateToLocal("qa.properties"));
        equal("%1$s", StatCollector.translateToFallback("qa.properties"));

        StringTranslateHelper.injectTranslations(Collections.singletonMap("qa.cached", "before %s"), LOGGER);
        ChatComponentTranslation cached = new ChatComponentTranslation("qa.cached", "arg");
        equal("before arg", cached.getUnformattedText());
        long before = StatCollector.getLastTranslationUpdateTimeInMilliseconds();
        StringTranslateHelper.injectTranslations(Collections.singletonMap("qa.cached", "after %s"), LOGGER);
        check(StatCollector.getLastTranslationUpdateTimeInMilliseconds() > before, "timestamp must change");
        equal("after arg", cached.getUnformattedText());
    }

    private static void sourceSelection() throws Exception {
        Path root = Files.createTempDirectory("localization-source-");
        try {
            Path lang = Files.createDirectories(root.resolve("assets/not_the_mod_id/lang"));
            Files.write(lang.resolve("en_US.lang"), "qa.source=English\nqa.english.only=English".getBytes(StandardCharsets.UTF_8));
            Files.write(lang.resolve("zh_CN.lang"), "qa.source=中文".getBytes(StandardCharsets.UTF_8));
            OtherModChanger changer = new OtherModChanger();
            check(changer.loadLanguageFromSource(root.toFile(), "different_mod_id", "zh_CN", LOGGER), "directory target");
            equal("中文", StatCollector.translateToLocal("qa.source"));
            equal("qa.english.only", StatCollector.translateToLocal("qa.english.only"));
            check(!changer.loadLanguageFromSource(root.toFile(), "different_mod_id", "fr_FR", LOGGER), "no directory fallback");

            Path jar = root.resolve("test.jar");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
                for (String[] entry : new String[][] {
                        { "assets/other/lang/zh_CN.lang", "qa.jar=中文" },
                        { "assets/other/lang/ja_JP.lang", "qa.jar=日本語" },
                        { "assets/second/lang/ZH_cn.lang", "qa.second=second" } }) {
                    zip.putNextEntry(new ZipEntry(entry[0]));
                    zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            check(changer.loadLanguageFromSource(jar.toFile(), "different_mod_id", "zh_CN", LOGGER), "jar target");
            equal("中文", StatCollector.translateToLocal("qa.jar"));
            equal("second", StatCollector.translateToLocal("qa.second"));
            check(!changer.loadLanguageFromSource(jar.toFile(), "different_mod_id", "fr_FR", LOGGER), "no jar fallback");
        } finally {
            removeTemporaryTree(root);
        }
    }

    private static void formatCorrections() throws Exception {
        Path root = Files.createTempDirectory("localization-overrides-");
        try {
            Path directory = root.resolve("config/serverlocalization/overrides");
            Map<String, String> broken = new LinkedHashMap<>();
            broken.put("commands.network.syphon.success", "从 %s 处成功抽取 %d LP.");
            broken.put("commands.network.add.success", "为 %s 成功添加 %d LP.");
            broken.put("commands.soulnetwork.subtract.success", "成功从 %s 的灵魂网络中减去 %dLP !");
            broken.put("salisarcana:command.forget-aspect.success", "§9成功：§r %s的要素列表中有%d个要素被变更");
            for (String verb : Arrays.asList("transferred", "received")) {
                broken.put("GT5U.infodata.wormhole_generator." + verb,
                        "%s仓（%,dA %s）在最近%d秒内" + (verb.equals("transferred") ? "传输" : "接收")
                                + "了§b%,d§fEU（相当于%,dA），效率为%.3f%%");
            }
            Map<String, String> corrected = LanguageFormatCorrections.loadCorrections(directory, "zh_CN", broken, LOGGER);
            equal(6, corrected.size());
            Path defaults = directory.resolve("00-defaults.json");
            check(Files.isRegularFile(defaults), "first directory creation exports bundled defaults");
            byte[] originalFile = Files.readAllBytes(defaults);
            Map<String, String> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : broken.entrySet()) {
                normalized.put(entry.getKey(), StringTranslateHelper.normalizeFormat(entry.getValue()));
            }
            equal(corrected, LanguageFormatCorrections.loadCorrections(directory, "ZH_cn", normalized, LOGGER));
            equal(Collections.emptyMap(), LanguageFormatCorrections.loadCorrections(directory, "en_US", broken, LOGGER));
            Map<String, String> future = new LinkedHashMap<>(broken);
            future.put("commands.network.syphon.success", "custom %2$s %1$s");
            future.remove("commands.network.add.success");
            Map<String, String> guarded = LanguageFormatCorrections.loadCorrections(directory, "zh_CN", future, LOGGER);
            check(!guarded.containsKey("commands.network.syphon.success"), "future/user template preserved");
            check(!guarded.containsKey("commands.network.add.success"), "missing key not invented");
            check(Arrays.equals(originalFile, Files.readAllBytes(defaults)), "existing defaults never overwritten");

            StringTranslateHelper.injectTranslations(broken, LOGGER);
            LanguageFormatCorrections.apply("en_US", directory, LOGGER);
            equal("从 %s 处成功抽取 %s LP.", StatCollector.translateToLocal("commands.network.syphon.success"));
            long before = StatCollector.getLastTranslationUpdateTimeInMilliseconds();
            long fallbackBefore = fallbackTimestamp();
            LanguageFormatCorrections.apply("zh_CN", directory, LOGGER);
            check(StatCollector.getLastTranslationUpdateTimeInMilliseconds() > before, "override updates main timestamp");
            check(fallbackTimestamp() > fallbackBefore, "override updates fallback timestamp");
            equal("从 player 处成功抽取 200 LP.", String.format(StatCollector.translateToLocal(
                    "commands.network.syphon.success"), 200, "player"));
            for (String verb : Arrays.asList("transferred", "received")) {
                String key = "GT5U.infodata.wormhole_generator." + verb;
                String template = StatCollector.translateToLocal(key);
                String text = String.format(java.util.Locale.ROOT, template, "输入", 12L, "IV", 1234567L, 34L, 99.12345, 7);
                check(text.contains("最近7秒"), "indexed seconds");
                check(text.contains("1,234,567") && text.contains("99.123%"), "numeric precision/grouping");
                equal(template, StatCollector.translateToFallback(key));
            }
            equal(corrected, LanguageFormatCorrections.loadCorrections(directory, "zh_CN", broken, LOGGER));
            Files.write(defaults, "{\"enabled\":false}".getBytes(StandardCharsets.UTF_8));
            equal(Collections.emptyMap(), LanguageFormatCorrections.loadCorrections(directory, "zh_CN", broken, LOGGER));
            equal("{\"enabled\":false}", new String(Files.readAllBytes(defaults), StandardCharsets.UTF_8));
            Files.delete(defaults);
            equal(Collections.emptyMap(), LanguageFormatCorrections.loadCorrections(directory, "zh_CN", broken, LOGGER));
            check(!Files.exists(defaults), "deleted defaults not restored in existing directory");
            externalRules(root.resolve("user-overrides"));
        } finally {
            removeTemporaryTree(root);
        }
    }

    private static void externalRules(Path directory) throws Exception {
        Files.createDirectories(directory);
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("qa.override", "original %s");
        snapshot.put("qa.after", "after");
        equal(Collections.emptyMap(), LanguageFormatCorrections.loadCorrections(directory, "zh_CN", snapshot, LOGGER));
        check(!Files.exists(directory.resolve("00-defaults.json")), "precreated empty directory remains empty");
        Files.write(directory.resolve("01-broken.json"), "{".getBytes(StandardCharsets.UTF_8));
        com.google.gson.JsonObject missingExpected = rule("zh_CN", "qa.override", "original %d", "bad");
        missingExpected.remove("expected");
        com.google.gson.JsonObject wrongType = rule("zh_CN", "qa.override", "original %d", "bad");
        wrongType.addProperty("expected", 1);
        writeRules(directory.resolve("10-first.json"),
                rule("zh_CN", "qa.override", "original %d", "first"), missingExpected, wrongType,
                com.google.gson.JsonNull.INSTANCE,
                rule("en_US", "qa.override", "original %d", "other locale"),
                rule("zh_CN", "qa.missing", "original", "invented"),
                rule("zh_CN", "qa.after", "after", "valid after malformed entries"));
        writeRules(directory.resolve("99-local.json"),
                rule("zh_CN", "qa.override", "original %d", "second"),
                rule("zh_CN", "qa.override", "original %d", "third %1$.3f"));
        Files.write(directory.resolve("ignored.txt"), "broken".getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("zz-disabled.json"), "{\"enabled\":false}".getBytes(StandardCharsets.UTF_8));
        Map<String, String> corrections = LanguageFormatCorrections.loadCorrections(directory, "zh_CN", snapshot, LOGGER);
        equal("third %1$.3f", corrections.get("qa.override"));
        equal("valid after malformed entries", corrections.get("qa.after"));
        equal(2, corrections.size());
        equal("original %s", snapshot.get("qa.override"));
        // A new process needs no recompile: loading again reads edits/new files from disk.
        writeRules(directory.resolve("zzz-added.json"), rule("zh_CN", "qa.override", "original %d", "edited %1$,d"));
        equal("edited %1$,d", LanguageFormatCorrections.loadCorrections(directory, "zh_CN", snapshot, LOGGER).get("qa.override"));
        snapshot.put("qa.override", "future upstream repair");
        check(!LanguageFormatCorrections.loadCorrections(directory, "zh_CN", snapshot, LOGGER).containsKey("qa.override"),
                "all files guard against future upstream changes");
        Path clientDirectory = directory.resolve("client-only");
        SIDE[0] = Side.CLIENT;
        try {
            LanguageFormatCorrections.apply("zh_CN", clientDirectory, LOGGER);
            check(!Files.exists(clientDirectory), "client does not read/export override files");
        } finally {
            SIDE[0] = Side.SERVER;
        }
    }

    private static com.google.gson.JsonObject rule(String locale, String key, String expected, String replacement) {
        com.google.gson.JsonObject rule = new com.google.gson.JsonObject();
        rule.addProperty("locale", locale);
        rule.addProperty("key", key);
        rule.addProperty("expected", expected);
        rule.addProperty("replacement", replacement);
        return rule;
    }

    private static void writeRules(Path file, com.google.gson.JsonElement... entries) throws Exception {
        com.google.gson.JsonArray rules = new com.google.gson.JsonArray();
        for (com.google.gson.JsonElement entry : entries) {
            rules.add(entry);
        }
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.add("rules", rules);
        Files.write(file, root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static long fallbackTimestamp() throws Exception {
        Map<String, String> fallback = StringTranslateHelper.getFallbackLanguageMap(LOGGER);
        for (Field translatorField : StatCollector.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(translatorField.getModifiers())
                    || !translatorField.getType().getSimpleName().equals("StringTranslate")) {
                continue;
            }
            translatorField.setAccessible(true);
            Object translator = translatorField.get(null);
            boolean isFallback = false;
            Field timestamp = null;
            for (Field field : translator.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (Map.class.isAssignableFrom(field.getType()) && field.get(translator) == fallback) {
                    isFallback = true;
                }
                if (field.getType() == long.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    timestamp = field;
                }
            }
            if (isFallback && timestamp != null) {
                return timestamp.getLong(translator);
            }
        }
        throw new AssertionError("fallback timestamp not found");
    }
    private static void chatComponents() {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("qa.inner", "内:%s");
        templates.put("qa.outer", "%2$s / %1$s / %2$s %%");
        templates.put("qa.bad", "%3$s");
        StringTranslateHelper.injectTranslations(templates, LOGGER);
        IChatComponent argument = new ChatComponentText("name");
        argument.getChatStyle().setColor(EnumChatFormatting.RED).setChatClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/example"));
        ChatComponentTranslation translation = new ChatComponentTranslation("qa.outer", "first",
                new ChatComponentTranslation("qa.inner", argument));
        translation.getChatStyle().setBold(true);
        IChatComponent root = new ChatComponentText("prefix:").appendSibling(translation);
        IChatComponent resolved = ChatTranslationResolver.resolve(root);
        equal("prefix:内:name / first / 内:name %", resolved.getUnformattedText());
        check(resolved != root && root.getSiblings().get(0) == translation, "input tree preserved");
        check(((IChatComponent) root.getSiblings().get(0)) instanceof ChatComponentTranslation, "original translation preserved");
        int styled = 0;
        for (Object item : resolved) {
            IChatComponent part = (IChatComponent) item;
            if (part.getUnformattedTextForChat().equals("name")) {
                equal(EnumChatFormatting.RED, part.getChatStyle().getColor());
                equal("/example", part.getChatStyle().getChatClickEvent().getValue());
                check(part.getChatStyle().getBold(), "inherited bold");
                styled++;
            }
        }
        equal(2, styled);
        ChatComponentTranslation bad = new ChatComponentTranslation("qa.bad", "one");
        check(ChatTranslationResolver.resolve(bad) == bad, "format failure preserves component");
        ChatComponentTranslation unknown = new ChatComponentTranslation("qa.unknown", "one");
        check(ChatTranslationResolver.resolve(unknown) instanceof ChatComponentTranslation, "unknown key stays translatable");
    }

    private static void downloadCache() throws Exception {
        Path root = Files.createTempDirectory("localization-download-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] bytes = "key=中文".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.createContext("/failure", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            equal("key=中文\n", HttpUtils.httpGet(base + "/ok"));
            Path target = root.resolve("lang.lang");
            check(HttpUtils.downloadFile(base + "/failure", target.toFile()) == null, "failed download");
            check(!Files.exists(target), "failure leaves no cache");
            check(HttpUtils.downloadFile(base + "/ok", target.toFile()) != null, "successful download");
            equal("key=中文", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
            check(HttpUtils.downloadFile(base + "/failure", target.toFile()) == null, "failed replacement");
            equal("key=中文", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
            removeTemporaryTree(root);
        }
    }

    private static void clientBoundary() {
        SIDE[0] = Side.CLIENT;
        try {
            check(StringTranslateHelper.getFallbackLanguageMap(LOGGER) == null, "physical client fallback isolated");
            StringTranslateHelper.getLanguageMap(LOGGER).put("commands.network.add.success", "为 %s 成功添加 %s LP.");
            LanguageFormatCorrections.apply("zh_CN", LOGGER);
            equal("为 %s 成功添加 %s LP.", StatCollector.translateToLocal("commands.network.add.success"));
            new ServerLocalization().postInit(null);
            equal("为 %s 成功添加 %s LP.", StatCollector.translateToLocal("commands.network.add.success"));
        } finally {
            SIDE[0] = Side.SERVER;
        }
    }

    private static void removeTemporaryTree(Path root) throws Exception {
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            for (Path path : (Iterable<Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }

    private static void equal(Object expected, Object actual) {
        check(java.util.Objects.equals(expected, actual), "expected=" + expected + ", actual=" + actual);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
