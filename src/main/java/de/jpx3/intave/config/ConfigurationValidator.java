/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.config;

import de.jpx3.intave.resource.Resource;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;

import static java.nio.charset.StandardCharsets.UTF_8;

final class ConfigurationValidator {
  private ConfigurationValidator() {
  }

  static boolean isValid(Resource resource) {
    YamlConfiguration configuration = new YamlConfiguration();
    try (InputStreamReader reader = new InputStreamReader(resource.read(), UTF_8)) {
      configuration.load(reader);
      return true;
    } catch (IOException | InvalidConfigurationException exception) {
      return false;
    }
  }
}
