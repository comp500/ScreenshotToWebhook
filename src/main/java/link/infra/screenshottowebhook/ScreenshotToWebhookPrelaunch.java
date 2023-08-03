package link.infra.screenshottowebhook;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class ScreenshotToWebhookPrelaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		MixinExtrasBootstrap.init();
	}
}
