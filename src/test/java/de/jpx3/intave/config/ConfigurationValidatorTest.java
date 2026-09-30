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
import de.jpx3.intave.resource.Resources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigurationValidatorTest {
  @Test
  void acceptsValidYaml() {
    Resource resource = Resources.memoryResource();
    resource.write("commands:\n  wave:\n    - 'say hello'");

    assertTrue(ConfigurationValidator.isValid(resource));
  }

  @Test
  void rejectsInvalidYaml() {
    Resource resource = Resources.memoryResource();
    resource.write("commands:\n  warning:\n    - \"title {player} title {\"text\":\"Warning\"}\"");

    assertFalse(ConfigurationValidator.isValid(resource));
  }
}
