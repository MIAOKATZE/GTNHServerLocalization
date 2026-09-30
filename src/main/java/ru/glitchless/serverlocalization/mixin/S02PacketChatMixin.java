package ru.glitchless.serverlocalization.mixin;

import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.glitchless.serverlocalization.ServerLocalization;
import ru.glitchless.serverlocalization.chat.ChatTranslationResolver;
import ru.glitchless.serverlocalization.config.ServerLocalizationConfig;

/**
 * S3 服务器端聊天翻译解析：gtsr/gtit 等自制 mod 的广播与踢人消息以
 * {@code ChatComponentTranslation} 上线，客户端汉化包缺这些键导致玩家看到英文/键名。
 * 在 {@code S02PacketChat(IChatComponent, boolean)} 构造尾部（EntityPlayerMP.addChatMessage
 * 与 ServerConfigurationManager.sendChatMsgImpl 的唯一汇聚点，单参构造器亦委托至此）
 * 把翻译组件树解析为 {@code ChatComponentText}，所有客户端无论是否装汉化均收到中文。
 * <p>
 * fail-open：开关关闭 / 根组件非翻译组件 / 解析抛错时保持原组件原样上线，
 * 绝不让聊天包构造崩溃服务器。
 */
@Mixin(S02PacketChat.class)
public abstract class S02PacketChatMixin {

    @Shadow
    private IChatComponent field_148919_a;

    @Inject(method = "<init>(Lnet/minecraft/util/IChatComponent;Z)V", at = @At("RETURN"))
    private void serverlocalization$resolveTranslation(IChatComponent component, boolean chat, CallbackInfo ci) {
        try {
            if (!ServerLocalizationConfig.resolveChatTranslations) {
                return;
            }
            if (!(this.field_148919_a instanceof ChatComponentTranslation)) {
                return;
            }
            IChatComponent resolved = ChatTranslationResolver.resolve(this.field_148919_a);
            if (resolved != null) {
                this.field_148919_a = resolved;
            }
        } catch (Throwable t) {
            if (ServerLocalization.logger != null) {
                ServerLocalization.logger.error("Failed to resolve ChatComponentTranslation in S02PacketChat", t);
            }
        }
    }
}
