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

import ac.intave.samples.event.FlyStateUpdateEvent;
import ac.intave.samples.event.PlayerFlyToggleEvent;
import ac.intave.samples.event.PlayerInitEvent;
import ac.intave.samples.share.Position;
import ac.intave.samples.share.Rotation;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PlaybackFlightTest {
  @Test
  void appliesSnapshotsWithoutMergingClientAndServerObservations() {
    PlaybackPlayerContainer playback = new PlaybackPlayerContainer(null);
    FlyStateUpdateEvent denied = new FlyStateUpdateEvent(false, 0.05F, "SURVIVAL", true, false);
    denied.accept(playback);
    assertFalse(playback.flying());
    assertTrue(playback.flightState().clientFlying());
    assertFalse(playback.flightState().serverFlying());
    assertFalse(playback.flightState().allowFlight());
    assertEquals(0.05F, playback.flightState().flySpeed());
    assertTrue(playback.inGameMode(GameMode.SURVIVAL));

    new FlyStateUpdateEvent(true, 0.1F, "CREATIVE", false, true).accept(playback);
    assertTrue(playback.flying());
    assertFalse(playback.flightState().clientFlying());
    assertTrue(playback.inGameMode(GameMode.CREATIVE));
    assertFalse(playback.inGameMode(GameMode.SURVIVAL));
  }

  @Test
  void unknownSnapshotValuesClearEarlierStateAndDoNotInferFlight() {
    PlaybackPlayerContainer playback = new PlaybackPlayerContainer(null);
    playback.visit(new PlayerFlyToggleEvent(true));
    new FlyStateUpdateEvent(true, 0.1F, "SPECTATOR", null, null).accept(playback);
    assertFalse(playback.flying());

    new FlyStateUpdateEvent(null, null, null, true, null).accept(playback);
    assertTrue(playback.flying());
    assertNull(playback.flightState().allowFlight());
    assertNull(playback.flightState().flySpeed());
    assertNull(playback.flightState().serverFlying());
    assertFalse(playback.inGameMode(GameMode.SPECTATOR));

    new FlyStateUpdateEvent().accept(playback);
    assertFalse(playback.flying());
    assertNull(playback.flightState().clientFlying());
  }

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
