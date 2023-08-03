package link.infra.screenshottowebhook;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Properties;

public class Config {
	public static Config INSTANCE = new Config();

	public final String destination;
	public final URI webhookUrl;
	public final String webhookHost;

	public Config() {
		Properties props = new Properties();
		try {
			props.load(Config.class.getClassLoader().getResourceAsStream("screenshottowebhook/config.properties"));
		} catch (IOException ex) {
			throw new RuntimeException("Failed to load configuration", ex);
		}
		destination = Objects.requireNonNull(props.getProperty("destination"), "No destination configured");
		try {
			webhookUrl = new URI(Objects.requireNonNull(props.getProperty("webhookUrl"), "No webhookUrl configured"));
		} catch (URISyntaxException ex) {
			throw new RuntimeException("Failed to parse webhook URL", ex);
		}
		webhookHost = webhookUrl.getHost();
	}
}
