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

package de.jpx3.intave.module.tracker.player;

import ac.intave.samples.event.PlayerVitalsEvent;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.meta.AbilityMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class PlayerVitalsTrackerTest {
  @Test
  void invalidatesCachedMaxHealthWhenTheAttributeChanges() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    AbilityMetadata abilities = new AbilityMetadata(
      FakePlayerFactory.createPlayer((ignored, arguments) -> null)
    );

    assertEquals(20.0, abilities.maxHealth());
    abilities.modifyBaseValue("generic.maxHealth", 24.0);
    assertEquals(24.0, abilities.maxHealth());
  }

  @Test
  void invalidatesCachedArmorWhenTheAttributesChange() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    AbilityMetadata abilities = new AbilityMetadata(
      FakePlayerFactory.createPlayer((ignored, arguments) -> null)
    );

    assertEquals(0.0, abilities.armor());
    assertEquals(0.0, abilities.armorToughness());
    abilities.modifyBaseValue("generic.armor", 8.0);
    abilities.modifyBaseValue("generic.armorToughness", 3.0);
    assertEquals(8.0, abilities.armor());
    assertEquals(3.0, abilities.armorToughness());
  }

  @Test
  void combinesAcknowledgedHealthAndExperienceUpdates() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    AbilityMetadata abilities = new AbilityMetadata(
      FakePlayerFactory.createPlayer((ignored, arguments) -> null)
    );
    abilities.synchronizeExperience(0.25F, 4, 80);

    abilities.synchronizeVitals(13.5F, 7, 1.5F);
    PlayerVitalsEvent afterHealth = PlayerVitalsTracker.event(abilities);
    assertEquals(13.5F, afterHealth.health());
    assertEquals(7, afterHealth.foodLevel());
    assertEquals(1.5F, afterHealth.saturation());
    assertEquals(0.25F, afterHealth.xpProgress());
    assertEquals(4, afterHealth.xpLevel());
    assertEquals(80, afterHealth.totalXp());
    assertEquals(20.0, afterHealth.maxHealth());
    assertEquals(0.0, afterHealth.armor());
    assertEquals(0.0, afterHealth.armorToughness());
    assertNull(afterHealth.absorption());
    assertNull(afterHealth.exhaustion());

    abilities.synchronizeExperience(0.75F, 12, 325);
    PlayerVitalsEvent afterExperience = PlayerVitalsTracker.event(abilities);
    assertEquals(13.5F, afterExperience.health());
    assertEquals(7, afterExperience.foodLevel());
    assertEquals(1.5F, afterExperience.saturation());
    assertEquals(0.75F, afterExperience.xpProgress());
    assertEquals(12, afterExperience.xpLevel());
    assertEquals(325, afterExperience.totalXp());
  }
}
