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

import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.GameMode;

import java.util.UUID;
import java.util.function.Consumer;

public interface PlayerContainer {
  Environment environment();
  UUID uuid();
  String name();
  int id();
  int version();
  boolean outdatedClient();
  boolean flying();
  <T extends CheckCustomMetadata> T meta(Class<T> metaClass);

  default Rotation rotation() {
    return new Rotation(yaw(), pitch());
  }
  float yaw();
  float pitch();

  default Rotation lastRotation() {
    return new Rotation(lastYaw(), lastPitch());
  }
  float lastYaw();
  float lastPitch();

  default Position position() {
    return new Position(x(), y(), z());
  }
  double x();
  double y();
  double z();

  boolean cursorUponEntity(int id, float expansion);
  boolean notTeleportedIn(int ticks);
  boolean inGameMode(GameMode gameMode);
  boolean recentlyAttacked(long millis);
  boolean recentlySwitchedEntity(long millis);
  int lastAttackedEntity();
  float perfectYaw();
  float perfectPitch();

  void debug(String message);
  void nerf(AttackNerfStrategy strategy, String originCode);
  void applyIfUserPresent(Consumer<? super User> action);
}
