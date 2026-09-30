package de.jpx3.intave.config;

import org.bukkit.configuration.file.YamlConfiguration;

public interface ConfigurationLoader {
  YamlConfiguration fetchConfiguration();

  default String invalidConfigurationFile() {
    return null;
  }
}
