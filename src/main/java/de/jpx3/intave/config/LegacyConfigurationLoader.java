package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;

public class LegacyConfigurationLoader implements ConfigurationLoader {
  private final IntavePlugin plugin;
  private String invalidConfigurationFile;

  public LegacyConfigurationLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public YamlConfiguration fetchConfiguration() {
    invalidConfigurationFile = null;
    File dataFolder = IntavePlugin.singletonInstance().dataFolder();
    File settingsFile = new File(dataFolder, "settings.yml");
    Resource config = Resources.resourceFromFile(settingsFile);
    if (!ConfigurationValidator.isValid(config)) {
      invalidConfigurationFile = "settings.yml";
    }
    return YamlConfiguration.loadConfiguration(new InputStreamReader(config.read()));
  }

  @Override
  public String invalidConfigurationFile() {
    return invalidConfigurationFile;
  }
}
