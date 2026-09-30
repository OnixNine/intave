package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ConfigSelectionResolver {
  private String invalidConfigurationFile;

  public ConfigSelection resolve() {
    invalidConfigurationFile = null;
    File dataFolder = IntavePlugin.singletonInstance().dataFolder();
    File settingsFile = new File(dataFolder, "settings.yml");
    if (settingsFile.exists()) {
      return ConfigSelection.LEGACY;
    }
    Resource configResource = Resources.resourceFromFile(new File(dataFolder, "config.yml"));
    Resource configResourceInClasspath = Resources.resourceFromJarOrBuild("config.yml");
    if (!configResource.available()) {
      try (InputStream read = configResourceInClasspath.read()) {
        configResource.write(read);
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
      configResource = configResourceInClasspath;
    }
    if (!ConfigurationValidator.isValid(configResource)) {
      invalidConfigurationFile = "config.yml";
    }
    YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(configResource.read()));
    String configType = config.getString("config", "LEGACY");
    ConfigSelection from = ConfigSelection.from(configType);
    if (from == null) {
      throw new RuntimeException("Invalid config type: " + configType);
    }
    return from;
  }

  public String invalidConfigurationFile() {
    return invalidConfigurationFile;
  }
}
