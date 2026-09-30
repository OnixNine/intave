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

package de.jpx3.intave.user.meta;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.test.FakePlayerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AbilityMetadataFlightTest {
  @Test
  void reportsOnlyAcknowledgedFlightTransitions() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    AbilityMetadata abilities = new AbilityMetadata(
      FakePlayerFactory.createPlayer((ignored, arguments) -> null)
    );

    assertFalse(abilities.acknowledgedFlying());
    assertFalse(abilities.acknowledgeFlying(false));
    assertTrue(abilities.acknowledgeFlying(true));
    assertTrue(abilities.acknowledgedFlying());
    assertTrue(abilities.flying());
    assertFalse(abilities.acknowledgeFlying(true));
    assertTrue(abilities.acknowledgeFlying(false));
    assertFalse(abilities.acknowledgedFlying());
    assertFalse(abilities.flying());
  }
}
