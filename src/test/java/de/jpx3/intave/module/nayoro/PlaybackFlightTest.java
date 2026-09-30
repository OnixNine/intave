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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.PlayerFlyToggleEvent;
import ac.intave.samples.event.PlayerInitEvent;
import ac.intave.samples.share.Position;
import ac.intave.samples.share.Rotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PlaybackFlightTest {
  @Test
  void appliesInitialFlightStateAndSubsequentToggles() {
    PlaybackPlayerContainer playback = new PlaybackPlayerContainer(null);
    playback.visit(new PlayerInitEvent(1, 769, 769, new Position(0, 64, 0), new Rotation(0, 0), true));
    assertTrue(playback.flying());
    playback.visit(new PlayerFlyToggleEvent(false));
    assertFalse(playback.flying());
    playback.visit(new PlayerFlyToggleEvent(true));
    assertTrue(playback.flying());
  }

  @Test
  void legacyInitialStateDefaultsToNotFlying() {
    PlaybackPlayerContainer playback = new PlaybackPlayerContainer(null);
    playback.visit(new PlayerInitEvent(1, 47, 47, new Position(0, 64, 0), new Rotation(0, 0)));
    assertFalse(playback.flying());
  }
}
