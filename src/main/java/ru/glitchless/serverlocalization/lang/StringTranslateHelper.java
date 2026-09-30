package ru.glitchless.serverlocalization.lang;

import cpw.mods.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统一访问 Minecraft 1.7.10 {@code net.minecraft.util.StringTranslate} 的工具类。
 *
 * <p>该类负责：
 * <ul>
 *     <li>通过反射获取 StringTranslate 单例与内部语言映射表字段；</li>
 *     <li>缓存反射得到的 {@link Class}、{@link Field}，避免每个加载器重复反射；</li>
 *     <li>提供安全的翻译注入方法；</li>
 *     <li>对首次初始化打印 INFO 日志，避免日志重复刷屏。</li>
 * </ul>
 *
 * <p>实现为静态工具类，使用 Bill Pugh Singleton 变体进行线程安全的延迟初始化。
 */
public final class StringTranslateHelper {

    private StringTranslateHelper() {
        // 静态工具类，禁止实例化
    }

    /**
     * 反射缓存信息。通过静态内部类延迟初始化，天然线程安全。
     */
    private static final class ReflectionCache {
        final Class<?> stringTranslateClass;
        final Object instance;
        final Field instanceField;
        final Field languageListField;
        /**
         * FML 在 {@code StatCollector} 上 patch 的 fallback 翻译器实例；
         * GT5U 2.9.0 起会向其 languageList 写入英文，服务器端需同步注入目标语言。
         * 反射失败或字段缺失时为 null，注入逻辑自动降级为仅主表。
         */
        final Object fallbackInstance;
        final Field fallbackInstanceField;

        ReflectionCache(Class<?> stringTranslateClass, Object instance, Field instanceField, Field languageListField,
                Object fallbackInstance, Field fallbackInstanceField) {
            this.stringTranslateClass = stringTranslateClass;
            this.instance = instance;
            this.instanceField = instanceField;
            this.languageListField = languageListField;
            this.fallbackInstance = fallbackInstance;
            this.fallbackInstanceField = fallbackInstanceField;
        }
    }

    private static final class Holder {
        static final ReflectionCache CACHE;

        static {
            try {
                CACHE = initializeCache();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static ReflectionCache getCache() {
        return Holder.CACHE;
    }

    /**
     * 用于保证初始化日志只打印一次。
     */
    private static final AtomicBoolean INITIALIZED_LOGGED = new AtomicBoolean(false);

    /**
     * 初始化反射缓存。首次调用时通过 {@link #getLanguageMap(Logger)} 打印一次 INFO 日志。
     */
    private static ReflectionCache initializeCache() throws Exception {
        Class<?> clazz = Class.forName("net.minecraft.util.StringTranslate");

        // 1. 获取 StringTranslate 实例字段
        Field instanceField = findInstanceField(clazz);
        if (instanceField == null) {
            throw new RuntimeException("Could not find StringTranslate instance field");
        }
        instanceField.setAccessible(true);
        Object instance = instanceField.get(null);
        if (instance == null) {
            throw new RuntimeException("StringTranslate instance is null");
        }

        // 2. 获取语言映射表字段
        Field languageListField = findLanguageListField(clazz, instance);
        if (languageListField == null) {
            throw new RuntimeException("Could not find language map field in StringTranslate");
        }
        languageListField.setAccessible(true);

        // 3. 获取 FML patch 的 fallback 翻译器（可缺失，不阻断初始化）
        Object fallbackInstance = null;
        Field fallbackInstanceField = findFallbackTranslatorField(clazz, instance);
        if (fallbackInstanceField != null) {
            fallbackInstanceField.setAccessible(true);
            fallbackInstance = fallbackInstanceField.get(null);
        }

        return new ReflectionCache(clazz, instance, instanceField, languageListField, fallbackInstance,
                fallbackInstanceField);
    }

    /**
     * 查找 {@code net.minecraft.util.StatCollector} 上 FML patch 的 fallbackTranslator 静态字段。
     * 优先使用已知名称（MCP/SRG），失败时按类型回退查找静态 StringTranslate 字段（排除与主实例相同的那个）。
     * 找不到时返回 null，调用方按"无 fallback 表"降级。
     */
    private static Field findFallbackTranslatorField(Class<?> stringTranslateClass, Object mainInstance) {
        Class<?> statCollectorClass;
        try {
            statCollectorClass = Class.forName("net.minecraft.util.StatCollector");
        } catch (ClassNotFoundException e) {
            return null;
        }

        String[] possibleNames = { "fallbackTranslator", "field_150828_b" };
        for (String name : possibleNames) {
            try {
                Field field = statCollectorClass.getDeclaredField(name);
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && stringTranslateClass.equals(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value != null) {
                        return field;
                    }
                }
            } catch (NoSuchFieldException e) {
                // 继续尝试下一个
            } catch (IllegalAccessException e) {
                // shouldn't happen after setAccessible, continue
            }
        }

        // 按类型回退：静态 StringTranslate 字段中取一个与主实例不同的（即 fallback）
        try {
            for (Field field : statCollectorClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && stringTranslateClass.equals(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value != null && value != mainInstance) {
                        return field;
                    }
                }
            }
        } catch (IllegalAccessException e) {
            return null;
        }
        return null;
    }

    /**
     * 查找 StringTranslate 的单例字段。优先使用已知名称，失败时按类型查找静态字段。
     */
    private static Field findInstanceField(Class<?> clazz) {
        String[] possibleNames = { "field_74817_a", "instance", "theStringTranslate" };
        for (String name : possibleNames) {
            try {
                Field field = clazz.getDeclaredField(name);
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value != null && clazz.isInstance(value)) {
                        return field;
                    }
                }
            } catch (NoSuchFieldException e) {
                // 继续尝试下一个
            } catch (IllegalAccessException e) {
                // shouldn't happen after setAccessible, continue
            }
        }

        // 按类型回退：查找类型为 StringTranslate 的静态字段
        for (Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && clazz.equals(field.getType())) {
                return field;
            }
        }
        return null;
    }

    /**
     * 查找语言映射表字段。优先使用已知名称，失败时按 Map 类型回退查找。
     */
    private static Field findLanguageListField(Class<?> clazz, Object instance) {
        String[] possibleNames = {
                "field_74816_c",
                "field_150511_e",
                "translateTable",
                "languageList",
                "nameToLanguageMap"
        };

        for (String name : possibleNames) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof Map) {
                    return field;
                }
            } catch (NoSuchFieldException e) {
                // 继续尝试下一个
            } catch (IllegalAccessException e) {
                // shouldn't happen after setAccessible, continue
            }
        }

        // 按类型回退：查找任意 Map 类型的字段
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(instance);
                if (value instanceof Map) {
                    return field;
                }
            } catch (IllegalAccessException e) {
                // 跳过该字段
            }
        }
        return null;
    }

    /**
     * 获取 Minecraft 内部的语言映射表（StringTranslate 中持有的 Map）。
     * 初始化时打印一次 INFO 日志说明所使用的反射字段。
     *
     * @param logger 用于输出首次初始化日志；若已初始化则不会重复输出
     * @return 当前语言映射表
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getLanguageMap(Logger logger) {
        ReflectionCache cache = getCache();
        // 仅在第一次成功初始化后打印一次 INFO 日志，避免每个加载器重复输出
        if (logger != null && INITIALIZED_LOGGED.compareAndSet(false, true)) {
            logger.info("StringTranslateHelper: using instance field '" + cache.instanceField.getName()
                    + "' and language map field '" + cache.languageListField.getName() + "'"
                    + (cache.fallbackInstanceField != null
                            ? ", fallback field '" + cache.fallbackInstanceField.getName() + "'"
                            : ", no fallback translator field found"));
        }
        try {
            return (Map<String, String>) cache.languageListField.get(cache.instance);
        } catch (IllegalAccessException e) {
            if (logger != null) {
                logger.error("Failed to get language map", e);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 FML patch 的 fallback 翻译器（{@code StatCollector.fallbackTranslator}）的语言映射表。
     * 仅在物理服务器端使用；反射目标缺失（或非服务器端）时返回 null，调用方降级为仅主表注入。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getFallbackLanguageMap(Logger logger) {
        if (!FMLCommonHandler.instance().getSide().isServer()) {
            return null;
        }
        ReflectionCache cache = getCache();
        if (cache.fallbackInstance == null) {
            return null;
        }
        try {
            return (Map<String, String>) cache.languageListField.get(cache.fallbackInstance);
        } catch (IllegalAccessException e) {
            if (logger != null) {
                logger.error("Failed to get fallback language map", e);
            }
            return null;
        }
    }

    /**
     * 将翻译映射批量注入到 StringTranslate 的语言表中；服务器端同步注入 fallback 表。
     *
     * @param translations 待注入的翻译键值对
     * @param logger       用于输出日志
     */
    public static void injectTranslations(Map<String, String> translations, Logger logger) {
        Map<String, String> languageMap = getLanguageMap(logger);
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            languageMap.put(entry.getKey(), entry.getValue());
        }
        if (logger != null) {
            logger.info("StringTranslateHelper: injected " + translations.size() + " translations");
        }
        Map<String, String> fallbackMap = getFallbackLanguageMap(logger);
        if (fallbackMap != null) {
            fallbackMap.putAll(translations);
            if (logger != null) {
                logger.info("StringTranslateHelper: injected " + translations.size()
                        + " translations into fallback table");
            }
        }
    }

    /**
     * 将 {@link Properties} 中的键值对注入到 StringTranslate 的语言表中；服务器端同步注入 fallback 表。
     *
     * @param properties 待注入的属性
     * @param logger     用于输出日志
     */
    public static void injectProperties(Properties properties, Logger logger) {
        Map<String, String> languageMap = getLanguageMap(logger);
        int count = 0;
        for (String key : properties.stringPropertyNames()) {
            languageMap.put(key, properties.getProperty(key));
            count++;
        }
        if (logger != null) {
            logger.info("StringTranslateHelper: injected " + count + " translations from properties");
        }
        Map<String, String> fallbackMap = getFallbackLanguageMap(logger);
        if (fallbackMap != null) {
            int fallbackCount = 0;
            for (String key : properties.stringPropertyNames()) {
                fallbackMap.put(key, properties.getProperty(key));
                fallbackCount++;
            }
            if (logger != null) {
                logger.info("StringTranslateHelper: injected " + fallbackCount
                        + " translations into fallback table");
            }
        }
    }
}
