package link.infra.screenshottowebhook;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ScreenshotToWebhook implements ClientModInitializer {
	private static boolean uploadAutomatically = false;
	private static final Minecraft client = Minecraft.getInstance();
	private static final HttpClient httpClient = HttpClient.newBuilder().build();
	private static final Set<File> seenFiles = new HashSet<>();

	public static final Logger LOGGER = LogManager.getLogger(ScreenshotToWebhook.class);

	private static void addChatMessage(Component component) {
		client.gui.getChat().addMessage(component);
		client.getNarrator().sayNow(component);
	}

	private static void printUploadAutomaticallyStatus() {
		if (uploadAutomatically) {
			addChatMessage(Component.translatable("screenshot.screenshottowebhook.autouploadenabled", Config.INSTANCE.destination));
		} else {
			addChatMessage(Component.translatable("screenshot.screenshottowebhook.autouploaddisabled", Config.INSTANCE.destination));
		}
	}

	private static boolean upload(File screenshotPath) {
		String boundary = "screenshottowebhook" + new Random().nextLong();
		String filename = screenshotPath.getName();
		String contentType = URLConnection.getFileNameMap().getContentTypeFor(filename);
		HttpRequest req;
		try {
			req = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.concat(
					HttpRequest.BodyPublishers.ofString("--" + boundary + "\r\n" +
						"Content-Disposition: form-data; name=\"screenshot\"; filename=\"" + filename + "\"\r\n" +
						"Content-Type: " + contentType + "\r\n" +
						"Content-Transfer-Encoding: binary\r\n\r\n"),
					HttpRequest.BodyPublishers.ofFile(screenshotPath.toPath()),
					HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--")
				))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.header("User-Agent", "ScreenshotToWebhook/1.0.0")
				.uri(Config.INSTANCE.webhookUrl).build();
		} catch (FileNotFoundException ex) {
			addChatMessage(Component.translatable("screenshot.screenshottowebhook.notfound", screenshotPath.toString()).withStyle(ChatFormatting.RED));
			return false;
		}

		httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
			.thenApply(res -> {
				if (res.statusCode() == 200 || res.statusCode() == 204) {
					MutableComponent autoUploadText;
					if (uploadAutomatically) {
						autoUploadText = Component.translatable("screenshot.screenshottowebhook.stopautoupload").withStyle(s -> s
							.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/screenshottowebhook:autoupload false")));
					} else {
						autoUploadText = Component.translatable("screenshot.screenshottowebhook.autoupload").withStyle(s -> s
							.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/screenshottowebhook:autoupload true")));
					}
					return Component.translatable("screenshot.screenshottowebhook.uploaddone", Config.INSTANCE.destination)
						.append(" ").append(autoUploadText.withStyle(ChatFormatting.GOLD))
						.withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							Component.translatable("screenshot.screenshottowebhook.uploadingto", Config.INSTANCE.webhookHost))));
				} else {
					MutableComponent retryText = Component.empty();
					if (uploadAutomatically) {
						retryText = Component.literal(" ").append(Component.translatable("screenshot.screenshottowebhook.retry").withStyle(s -> s
							.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("screenshot.screenshottowebhook.uploadingto", Config.INSTANCE.webhookHost)))
							.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/screenshottowebhook:upload " +
									StringArgumentType.escapeIfRequired(screenshotPath.toString())))
							.withColor(ChatFormatting.GOLD)));
					}
					return Component.translatable("screenshot.screenshottowebhook.failedcode", res.statusCode())
						.withStyle(ChatFormatting.RED).append(retryText);
				}
			})
			.exceptionally(ex -> {
				LOGGER.error("Failed to upload screenshot to " + Config.INSTANCE.webhookUrl, ex);
				return Component.translatable("screenshot.screenshottowebhook.failed", ex).withStyle(ChatFormatting.RED);
			})
			.thenAcceptAsync(ScreenshotToWebhook::addChatMessage, client);
		return true;
	}

	public static MutableComponent handleScreenshotSaveMessage(MutableComponent existingMessage, File screenshotPath) {
		seenFiles.add(screenshotPath);
		if (uploadAutomatically) {
			upload(screenshotPath);
			return existingMessage;
		} else {
			var conf = Config.INSTANCE;
			return existingMessage.append(" ")
				.append(Component.translatable("screenshot.screenshottowebhook.upload", conf.destination)
					.withStyle(s -> s
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("screenshot.screenshottowebhook.uploadingto", conf.webhookHost)))
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/screenshottowebhook:upload " +
							StringArgumentType.escapeIfRequired(screenshotPath.toString())))
						.withColor(ChatFormatting.GOLD))
				);
		}
	}

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("screenshottowebhook:upload")
				.then(
					ClientCommandManager.argument("file", StringArgumentType.string()).executes(ctx -> {
						String pathStr = StringArgumentType.getString(ctx, "file");
						File screenshotPath = new File(pathStr);
						// Security: validate that the given path has been seen as a screenshot
						// - this command could be executed from a server-provided click action
						if (!seenFiles.contains(screenshotPath)) {
							ctx.getSource().sendError(Component.translatable("screenshot.screenshottowebhook.failedvalidpath"));
							return 0;
						}
						return upload(screenshotPath) ? 1 : 0;
					})
				)
			);
			dispatcher.register(ClientCommandManager.literal("screenshottowebhook:autoupload")
				.then(
					ClientCommandManager.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
						uploadAutomatically = BoolArgumentType.getBool(ctx, "enabled");
						printUploadAutomaticallyStatus();
						return 1;
					})
				)
			);
		});
	}
}
