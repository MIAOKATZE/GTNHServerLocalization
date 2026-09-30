package ru.glitchless.serverlocalization.chat;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import ru.glitchless.serverlocalization.lang.StringTranslateHelper;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * S3 服务器端 {@code ChatComponentTranslation} 解析器：用已注入 zh_CN 的 StringTranslate 主表
 * 把翻译组件树展平为 {@code ChatComponentText}（保留 chatStyle 与兄弟节点），
 * 供 {@code S02PacketChatMixin} 在聊天包构造时调用。
 * <p>
 * 仅用服务端安全方法（{@code getUnformattedText}/{@code createCopy}/{@code createShallowCopy}），
 * 不触碰 {@code getFormattedText} 等客户端专用方法；整个解析可失败回退（模板原样/键名文本），
 * 不向调用方抛出异常。
 */
public final class ChatTranslationResolver {

    private ChatTranslationResolver() {
        // 静态工具类，禁止实例化
    }

    /**
     * 反射缓存：ChatComponentTranslation 的 key 与 args 私有字段（volatile，
     * S02PacketChat 构造发生在网络线程）。dev/MCP 名 key/formatArgs，
     * 生产 SRG 名 field_150716_b/field_150717_c，同一运行时名字固定。
     */
    private static volatile Field keyField;
    private static volatile Field argsField;

    public static IChatComponent resolve(IChatComponent component) {
        if (component instanceof ChatComponentTranslation) {
            return resolveTranslation((ChatComponentTranslation) component);
        }
        if (containsTranslation(component)) {
            return rebuild(component);
        }
        return component;
    }

    /**
     * 翻译组件：模板查表 + args 展平 + String.format（与 vanilla lang 格式同源），
     * 包成 ChatComponentText 并套原组件 chatStyle 与兄弟节点。
     */
    private static IChatComponent resolveTranslation(ChatComponentTranslation component) {
        String key = getKey(component);
        if (key == null) {
            // 反射拿不到 key：原样返回，不做解析
            return component;
        }
        Object[] args = getArgs(component);

        String template = lookupTemplate(key);
        String text;
        try {
            Object[] processed = new Object[args == null ? 0 : args.length];
            for (int i = 0; i < processed.length; i++) {
                processed[i] = resolveArg(args[i]);
            }
            text = String.format(template, processed);
        } catch (Exception e) {
            // %s 数量与 args 不匹配等：回退模板原样
            text = template;
        }

        ChatComponentText result = new ChatComponentText(text);
        result.setChatStyle(component.getChatStyle().createShallowCopy());
        for (Object siblingObj : component.getSiblings()) {
            result.appendSibling(resolve((IChatComponent) siblingObj));
        }
        return result;
    }

    /** 模板优先取 StringTranslate 主表（已注入 zh_CN），缺失回退 StatCollector，仍缺则保留键名。 */
    private static String lookupTemplate(String key) {
        String template = null;
        try {
            Map<String, String> languageMap = StringTranslateHelper.getLanguageMap(null);
            if (languageMap != null) {
                template = languageMap.get(key);
            }
        } catch (Throwable ignored) {
            // 反射初始化失败：走 StatCollector 回退
        }
        if (template == null) {
            String fallback = StatCollector.translateToLocal(key);
            if (fallback != null && !fallback.equals(key)) {
                template = fallback;
            }
        }
        return template != null ? template : key;
    }

    /** args 逐个展平为可格式化对象：组件递归解析，实体/物品取显示名，其余字符串化。 */
    private static Object resolveArg(Object arg) {
        if (arg instanceof IChatComponent) {
            IChatComponent resolved = resolve((IChatComponent) arg);
            return resolved != null ? resolved.getUnformattedText() : String.valueOf(arg);
        }
        if (arg instanceof Entity) {
            return ((Entity) arg).func_145748_c_().getUnformattedText();
        }
        if (arg instanceof ItemStack) {
            return ((ItemStack) arg).getDisplayName();
        }
        return String.valueOf(arg);
    }

    /** 深度检测子树中是否含翻译组件（含兄弟节点的后代）。 */
    private static boolean containsTranslation(IChatComponent component) {
        if (component == null) {
            return false;
        }
        if (component instanceof ChatComponentTranslation) {
            return true;
        }
        for (Object siblingObj : component.getSiblings()) {
            if (containsTranslation((IChatComponent) siblingObj)) {
                return true;
            }
        }
        return false;
    }

    /** 非翻译组件但子树含翻译：createCopy 保住本体与 chatStyle，兄弟节点替换为解析后的子树。 */
    private static IChatComponent rebuild(IChatComponent component) {
        IChatComponent copy = component.createCopy();
        copy.getSiblings().clear();
        for (Object siblingObj : component.getSiblings()) {
            copy.appendSibling(resolve((IChatComponent) siblingObj));
        }
        return copy;
    }

    private static final String[] KEY_NAMES = { "key", "field_150716_b" };
    private static final String[] ARGS_NAMES = { "formatArgs", "args", "field_150717_c" };

    /** 读翻译键；名字链覆盖 dev/MCP 与生产 SRG，首次成功后缓存 Field。 */
    private static String getKey(ChatComponentTranslation component) {
        if (keyField == null) {
            keyField = findField(KEY_NAMES);
            if (keyField == null) {
                return null;
            }
        }
        try {
            return (String) keyField.get(component);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** 读格式化参数；名字链覆盖 dev/MCP 与生产 SRG，首次成功后缓存 Field。 */
    private static Object[] getArgs(ChatComponentTranslation component) {
        if (argsField == null) {
            argsField = findField(ARGS_NAMES);
            if (argsField == null) {
                return null;
            }
        }
        try {
            return (Object[]) argsField.get(component);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static Field findField(String[] names) {
        for (String name : names) {
            try {
                Field field = ChatComponentTranslation.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 继续尝试下一个名字
            }
        }
        return null;
    }
}
