package link.infra.screenshottowebhook.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.platform.NativeImage;
import link.infra.screenshottowebhook.ScreenshotToWebhook;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public class ScreenshotMixin {
	@Inject(method = "method_22691", at = @At("HEAD"))
	private static void screenshottowebhook_captureScreenshotPath(NativeImage nativeImage, File file, Consumer<Component> consumer, CallbackInfo ci, @Share("file") LocalRef<File> fileShare) {
		fileShare.set(file);
	}

	@ModifyExpressionValue(method = "method_22691",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
	private static MutableComponent screenshottowebhook_screenshotSaveMessage(MutableComponent existingMessage, @Share("file") LocalRef<File> fileShare) {
		return ScreenshotToWebhook.handleScreenshotSaveMessage(existingMessage, fileShare.get());
	}
}
