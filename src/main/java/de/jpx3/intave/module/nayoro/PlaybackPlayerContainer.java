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

import ac.intave.samples.event.AttackEvent;
import ac.intave.samples.event.FlyStateUpdateEvent;
import ac.intave.samples.event.PlayerFlyToggleEvent;
import ac.intave.samples.event.PlayerInitEvent;
import ac.intave.samples.event.PlayerMoveEvent;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class PlaybackPlayerContainer extends SinkPlayerContainer {
  private final Environment environment;
  private final Map<Class<?>, CheckCustomMetadata> metadata = new HashMap<>();
  private int id;
  private int version;
  private boolean outdated;
  private boolean flying;
  private FlyStateUpdateEvent flightState;
  private double posX;
  private double posY;
  private double posZ;
  private float yaw;
  private float pitch;
  private float lastYaw;
  private float lastPitch;

  private long lastAttack;
  private int lastAttackedEntityId = -1;

  public PlaybackPlayerContainer(Environment environment) {
    this.environment = environment;
  }

  @Override
  public int id() {
    return id;
  }

  @Override
  public int version() {
    return version;
  }

  @Override
  public boolean outdatedClient() {
    return outdated;
  }

  @Override
  public boolean flying() {
    return flying;
  }

  FlyStateUpdateEvent flightState() {
    return flightState;
  }

  @Override
  public <T extends CheckCustomMetadata> T meta(Class<T> metaClass) {
    // noinspection unchecked
    return (T) metadata.computeIfAbsent(metaClass, k -> {
      try {
        return metaClass.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public Rotation rotation() {
    return new Rotation(yaw, pitch);
  }

  @Override
  public float yaw() {
    return yaw;
  }

  @Override
  public float pitch() {
    return pitch;
  }

  @Override
  public Rotation lastRotation() {
    return new Rotation(lastYaw, lastPitch);
  }

  @Override
  public float lastYaw() {
    return lastYaw;
  }

  @Override
  public float lastPitch() {
    return lastPitch;
  }

  @Override
  public Position position() {
    return new Position(posX, posY, posZ);
  }

  @Override
  public double x() {
    return posX;
  }

  @Override
  public double y() {
    return posY;
  }

  @Override
  public double z() {
    return posZ;
  }

  @Override
  public boolean cursorUponEntity(int id, float expansion) {
    return environment.inSight(id);
  }

  @Override
  public boolean notTeleportedIn(int ticks) {
    return false;
  }

  @Override
  public boolean inGameMode(GameMode gameMode) {
    return flightState != null && gameMode.name().equals(flightState.gameMode());
  }

  @Override
  public boolean recentlyAttacked(long millis) {
    return lastAttack + millis > environment.currentTime();
  }

  @Override
  public boolean recentlySwitchedEntity(long millis) {
    return false;
  }

  @Override
  public int lastAttackedEntity() {
    return lastAttackedEntityId;
  }

  @Override
  public float perfectYaw() {
    if (lastAttackedEntityId == -1) {
      return 0;
    }
    Position position = environment.positionOf(lastAttackedEntityId);
    if (position == null) {
      return 0;
    }
    return resolveYawRotation(position, posX, posZ);
  }

  @Override
  public float perfectPitch() {
    if (lastAttackedEntityId == -1) {
      return 0;
    }
    Position position = environment.positionOf(lastAttackedEntityId);
    if (position == null) {
      return 0;
    }
    return resolvePitchRotation(position, posX, posY, posZ);
  }

  private static float resolveYawRotation(
    Position entityPosition,
    double posX, double posZ
  ) {
    double diffX = entityPosition.getX() - posX;
    double diffZ = entityPosition.getZ() - posZ;
    return (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
  }

  private static float resolvePitchRotation(
    Position entityPosition,
    double posX, double posY, double posZ
  ) {
    double diffX = entityPosition.getX() - posX;
    double diffY = entityPosition.getY() + 1.62f - (posY + 1.62f);
    double diffZ = entityPosition.getZ() - posZ;
    double d3 = Math.sqrt(diffX * diffX + diffZ * diffZ);
    return (float) (-Math.atan2(diffY, d3) * 180.0 / Math.PI);
  }

  @Override
  public Environment environment() {
    return environment;
  }

  @Override
  public UUID uuid() {
    return UUID.fromString("00000000-0000-0000-0000-000000000000");
  }

  @Override
  public String name() {
    return "PlaybackPlayer";
  }

  @Override
  public void debug(String message) {
    System.out.println("[intave/nayoro/debug] " + message);
  }

  @Override
  public void nerf(AttackNerfStrategy strategy, String originCode) {
    System.out.println("[intave/nayoro/nerf] " + strategy.name() + ": " + originCode);
  }

  @Override
  public void applyIfUserPresent(Consumer<? super User> action) {
    // ignore
  }

  @Override
  public void visit(AttackEvent event) {
    lastAttack = environment.currentTime();//System.currentTimeMillis();
//    if (event.source() != id) {
//      return;
//    }
    System.out.println("Attack: " + event.target());
    lastAttackedEntityId = event.target();
    visitAny(event);
  }

  @Override
  public void visit(PlayerInitEvent event) {
    id = event.id();
    version = event.clientVersion();
    outdated = event.serverVersion() > version;
    if (event.isFlying() != null) {
      flying = event.isFlying();
    }

    ac.intave.samples.share.Position position = event.position();
    posX = position.x();
    posY = position.y();
    posZ = position.z();

    ac.intave.samples.share.Rotation rotation = event.rotation();
    yaw = rotation.yaw();
    pitch = rotation.pitch();

    lastYaw = yaw;
    lastPitch = pitch;

    visitAny(event);
  }

  @Override
  public void visit(PlayerFlyToggleEvent event) {
    flying = event.isFlying();
    visitAny(event);
  }

  @Override
  public void visit(FlyStateUpdateEvent event) {
    // Each event is a full snapshot: null clears an earlier observation.
    flightState = event;
    flying = Boolean.TRUE.equals(event.serverFlying() != null
      ? event.serverFlying() : event.clientFlying());
    visitAny(event);
  }

  @Override
  public void visit(PlayerMoveEvent event) {
    lastYaw = yaw;
    lastPitch = pitch;

    ac.intave.samples.share.Position movement = event.position();
    this.posX = movement.x();
    this.posY = movement.y();
    this.posZ = movement.z();

    ac.intave.samples.share.Rotation movementRotation = event.rotation();
    this.yaw = movementRotation.yaw();
    this.pitch = movementRotation.pitch();
    visitAny(event);
  }
}
