package ru.glitchless.serverlocalization.chat;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import ru.glitchless.serverlocalization.lang.StringTranslateHelper;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves translations before packet serialization, preserving component arguments and their styles. */
public final class ChatTranslationResolver {
    private static final Pattern STRING_VARIABLE_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private ChatTranslationResolver() {}

    public static IChatComponent resolve(IChatComponent component) {
        if (component == null || !containsTranslation(component)) {
            return component;
        }
        try {
            return resolveTree(component);
        } catch (RuntimeException e) {
            // An unknown key or malformed template must retain the original client-side fallback.
            return component;
        }
    }

    private static IChatComponent resolveTree(IChatComponent component) {
        IChatComponent result;
        if (component instanceof ChatComponentTranslation) {
            ChatComponentTranslation translation = (ChatComponentTranslation) component;
            String template = lookupTemplate(translation.getKey());
            if (template == null) {
                return component.createCopy();
            }
            result = format(template, translation.getFormatArgs());
            result.setChatStyle(component.getChatStyle().createShallowCopy());
        } else {
            result = component.createCopy();
            result.getSiblings().clear();
        }
        for (Object sibling : component.getSiblings()) {
            result.appendSibling(resolveTree((IChatComponent) sibling));
        }
        return result;
    }

    // Match vanilla's %s / %N$s / %% grammar, retaining component objects instead of flattening their text.
    private static IChatComponent format(String template, Object[] args) {
        ChatComponentText result = new ChatComponentText("");
        Matcher matcher = STRING_VARIABLE_PATTERN.matcher(template);
        int offset = 0;
        int nextArg = 0;
        while (matcher.find()) {
            appendLiteral(result, template.substring(offset, matcher.start()));
            String token = matcher.group();
            if ("%%".equals(token)) {
                result.appendText("%");
            } else if ("s".equals(matcher.group(2))) {
                int index = matcher.group(1) == null ? nextArg++ : Integer.parseInt(matcher.group(1)) - 1;
                if (index < 0 || index >= args.length) {
                    throw new IllegalArgumentException("Missing translation argument");
                }
                result.appendSibling(resolveArg(args[index]));
            } else {
                throw new IllegalArgumentException("Unsupported translation format: " + token);
            }
            offset = matcher.end();
        }
        appendLiteral(result, template.substring(offset));
        return result;
    }

    private static void appendLiteral(IChatComponent result, String literal) {
        if (!literal.isEmpty()) {
            // Vanilla validates text between placeholders with String.format as well.
            result.appendText(String.format(literal));
        }
    }

    private static String lookupTemplate(String key) {
        try {
            Map<String, String> languageMap = StringTranslateHelper.getLanguageMap(null);
            String template = languageMap.get(key);
            if (template != null) {
                return template;
            }
        } catch (RuntimeException ignored) {
            // Continue with vanilla if reflection cannot access the table.
        }
        String fallback = StatCollector.translateToLocal(key);
        return fallback != null && !fallback.equals(key) ? fallback : null;
    }

    private static IChatComponent resolveArg(Object arg) {
        if (arg instanceof IChatComponent) {
            // Copies are essential: appendSibling changes the argument's style parent.
            return resolveTree((IChatComponent) arg);
        }
        if (arg instanceof Entity) {
            return resolveTree(((Entity) arg).func_145748_c_());
        }
        if (arg instanceof ItemStack) {
            return new ChatComponentText(((ItemStack) arg).getDisplayName());
        }
        return new ChatComponentText(String.valueOf(arg));
    }

    private static boolean containsTranslation(IChatComponent component) {
        if (component instanceof ChatComponentTranslation) {
            return true;
        }
        for (Object sibling : component.getSiblings()) {
            if (containsTranslation((IChatComponent) sibling)) {
                return true;
            }
        }
        return false;
    }
}
