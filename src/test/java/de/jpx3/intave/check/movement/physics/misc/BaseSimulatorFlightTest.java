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

package de.jpx3.intave.check.movement.physics.misc;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BaseSimulatorFlightTest {
  private static final double EPSILON = 1.0E-8D;
  private static final Position POSITION = Position.of(0.5D, 50.0D, 0.5D);
  private static final Rotation ROTATION = Rotation.zero();
  private static final float FLY_SPEED = 0.05F;
  private static final double FLYING_STEP = (double) (FLY_SPEED * 3.0F);

  @BeforeEach
  void setUp() {
    MinecraftVersion.setCurrent(new MinecraftVersion("26.1.2"));
  }

  @Test
  void airborneFlightUsesFlySpeedAndSprintMultiplier() {
    TestContext context = context(VER_1_21_4, true, false);

    Motion walking = simulate(context, MovementConfiguration.blank().pressingW()).actualMotion();
    Motion sprinting = simulate(
      context, MovementConfiguration.blank().pressingW().withSprinting()
    ).actualMotion();

    assertEquals((double) (0.98F * FLY_SPEED), walking.motionZ, EPSILON);
    assertEquals((double) (0.98F * FLY_SPEED * 2.0F), sprinting.motionZ, EPSILON);
  }

  @Test
  void flightPermissionWithoutActiveFlightUsesNormalAirMovement() {
    TestContext permitted = context(VER_1_21_4, false, false);
    TestContext flying = context(VER_1_21_4, true, false);

    double permittedMotion = simulate(
      permitted, MovementConfiguration.blank().pressingW()
    ).actualMotion().motionZ;
    double flyingMotion = simulate(
      flying, MovementConfiguration.blank().pressingW()
    ).actualMotion().motionZ;

    assertEquals((double) (0.98F * 0.02F), permittedMotion, EPSILON);
    assertEquals((double) (0.98F * FLY_SPEED), flyingMotion, EPSILON);
  }

  @Test
  void flightCancelsSneakInputSlowdownStartingIn19() {
    TestContext legacy = context(VER_1_8, true, false);
    legacy.environment.setSneaking(true);
    TestContext modern = context(VER_1_9, true, false);
    modern.environment.setSneaking(true);

    double legacyMotion = simulate(
      legacy, MovementConfiguration.blank().pressingW()
    ).actualMotion().motionZ;
    double modernMotion = simulate(
      modern, MovementConfiguration.blank().pressingW()
    ).actualMotion().motionZ;
    float slowedInput = (float) ((double) 0.98F * 0.3D);
    float restoredInput = (float) ((double) slowedInput / 0.3D);

    assertEquals((double) (0.98F * 0.3F * FLY_SPEED), legacyMotion, EPSILON);
    assertEquals((double) (restoredInput * FLY_SPEED), modernMotion, EPSILON);
  }

  @Test
  void flightInputAddsVerticalSpeedAndStoresSixtyPercentAfterTravel() {
    TestContext context = context(VER_1_21_4, true, false);
    Motion initial = new Motion(0.0D, 0.2D, 0.0D);
    MovementConfiguration configuration = MovementConfiguration.blank().withJump();
    Simulation simulation = simulate(context, initial, configuration);
    Motion travelInput = simulation.actualMotion();

    assertEquals(0.2D + FLYING_STEP, travelInput.motionY, EPSILON);

    context.environment.setSimulationResult(simulation.result());
    context.environment.addFallDistance(4.0D);
    SimulationEnvironment branch = context.environment.mutableView();
    Motion stored = Simulators.PLAYER.simulateAfterTick(
      context.user, branch, configuration, POSITION, travelInput
    );

    assertEquals(travelInput.motionY * 0.6D, stored.motionY, EPSILON);
    assertEquals(4.0D, branch.fallDistance(), EPSILON);
  }

  @Test
  void flightUsesAirTravelPathInsideWater() {
    assertFlightUsesAirTravelPathInsideFluid(true);
  }

  @Test
  void flightUsesAirTravelPathInsideLava() {
    assertFlightUsesAirTravelPathInsideFluid(false);
  }

  @Test
  void flightFallDistanceResetExistsFrom19Through1211() {
    assertEquals(3.0D, fallDistanceAfterFlightTravel(VER_1_8), EPSILON);
    assertEquals(0.0D, fallDistanceAfterFlightTravel(VER_1_9), EPSILON);
    assertEquals(0.0D, fallDistanceAfterFlightTravel(VER_1_21), EPSILON);
    assertEquals(3.0D, fallDistanceAfterFlightTravel(VER_1_21_2), EPSILON);
  }

  @Test
  void startingFlightOnGroundAlsoPerformsJumpStartingIn1205() {
    TestContext oldClient = context(VER_1_20_3, true, true);
    oldClient.user.meta().abilities().setStartedFlying(true);
    TestContext newClient = context(VER_1_20_5, true, true);
    newClient.user.meta().abilities().setStartedFlying(true);
    MovementConfiguration jump = MovementConfiguration.blank().withJump();

    double oldMotion = simulate(oldClient, jump).actualMotion().motionY;
    double newMotion = simulate(newClient, jump).actualMotion().motionY;

    assertEquals(FLYING_STEP, oldMotion, EPSILON);
    assertEquals(0.42D + FLYING_STEP, newMotion, EPSILON);
  }

  @Test
  void collisionDoesNotChangeStoredVerticalFlightSpeed() {
    TestContext context = context(VER_1_21_4, true, false);
    Motion travelInput = new Motion(0.0D, 0.3D, 0.0D);
    SimulationResult collision = new SimulationResult(
      travelInput.copy(), Motion.newEmpty(), null,
      false, false, true, false, false, false, false, 0.0D
    );
    context.environment.collidedVertically = true;
    context.environment.setSimulationResult(collision);

    Motion stored = Simulators.PLAYER.simulateAfterTick(
      context.user,
      context.environment.mutableView(),
      MovementConfiguration.blank(),
      POSITION,
      travelInput
    );

    assertEquals(0.18D, stored.motionY, EPSILON);
  }

  @Test
  void late116FlightDisablesSneakEdgeBackoff() {
    TestContext early = context(VER_1_16, true, true, new MockFullBlockStaticPlane());
    early.environment.setSneaking(true);
    TestContext late = context(VER_1_16_4, true, true, new MockFullBlockStaticPlane());
    late.environment.setSneaking(true);

    SimulationResult earlyResult = Colliders.collision(
      early.user, early.environment.mutableView(), new Motion(0.2D, 0.0D, 0.0D),
      false, POSITION.getX(), POSITION.getY(), POSITION.getZ()
    );
    SimulationResult lateResult = Colliders.collision(
      late.user, late.environment.mutableView(), new Motion(0.2D, 0.0D, 0.0D),
      false, POSITION.getX(), POSITION.getY(), POSITION.getZ()
    );

    assertEquals(0.0D, earlyResult.offsetMotion().motionX, EPSILON);
    assertEquals(0.2D, lateResult.offsetMotion().motionX, EPSILON);
  }

  private static Simulation simulate(
    TestContext context,
    MovementConfiguration configuration
  ) {
    return simulate(context, Motion.newEmpty(), configuration);
  }

  private static double fallDistanceAfterFlightTravel(int protocolVersion) {
    TestContext context = context(protocolVersion, true, false);
    context.environment.addFallDistance(3.0D);
    Motion input = new Motion(0.0D, 0.2D, 0.0D);
    context.environment.setSimulationResult(SimulationResult.untouched(input));
    SimulationEnvironment branch = context.environment.mutableView();
    Simulators.PLAYER.simulateAfterTick(
      context.user,
      branch,
      MovementConfiguration.blank(),
      POSITION,
      input
    );
    return branch.fallDistance();
  }

  private static void assertFlightUsesAirTravelPathInsideFluid(boolean water) {
    TestContext context = context(VER_26_3, true, false);
    context.environment.setInWater(water);
    context.environment.setInLava(!water);
    Motion initial = new Motion(0.12D, 0.2D, 0.36D);
    MovementConfiguration configuration = MovementConfiguration.blank()
      .pressingW()
      .withSprinting();

    Simulation simulation = simulate(context, initial, configuration);
    Motion travelMotion = simulation.actualMotion();
    double flyingAcceleration = (double) (0.98F * (FLY_SPEED * 2.0F));

    assertEquals(initial.motionX, travelMotion.motionX, EPSILON);
    assertEquals(initial.motionY, travelMotion.motionY, EPSILON);
    assertEquals(initial.motionZ + flyingAcceleration, travelMotion.motionZ, EPSILON);

    context.environment.setSimulationResult(simulation.result());
    Motion storedMotion = Simulators.PLAYER.simulateAfterTick(
      context.user,
      context.environment.mutableView(),
      configuration,
      POSITION,
      travelMotion
    );

    assertEquals(travelMotion.motionX * (double) 0.91F, storedMotion.motionX, EPSILON);
    assertEquals(travelMotion.motionY * 0.6D, storedMotion.motionY, EPSILON);
    assertEquals(travelMotion.motionZ * (double) 0.91F, storedMotion.motionZ, EPSILON);
  }

  private static Simulation simulate(
    TestContext context,
    Motion motion,
    MovementConfiguration configuration
  ) {
    return Simulators.PLAYER.simulateTick(
      context.user,
      motion,
      context.environment.mutableView(),
      configuration
    );
  }

  private static TestContext context(
    int protocolVersion,
    boolean flying,
    boolean lastOnGround
  ) {
    return context(
      protocolVersion, flying, lastOnGround,
      MockFullBlockStaticPlane.createWithHorizontalPlaneAt(49)
    );
  }

  private static TestContext context(
    int protocolVersion,
    boolean flying,
    boolean lastOnGround,
    BlockCache blockCache
  ) {
    World world = FakeWorldFactory.createWorld(
      (methodName, _) -> switch (methodName) {
        case "isChunkLoaded", "isChunkInUse" -> true;
        case "isThundering", "hasStorm" -> false;
        default -> null;
      }
    );
    Location location = POSITION.toLocation(world);
    Player player = FakePlayerFactory.createPlayer(
      (methodName, _) -> switch (methodName) {
        case "getWorld" -> world;
        case "getLocation" -> location;
        case "getUniqueId" -> UUID.randomUUID();
        default -> null;
      }
    );
    User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
      case "blockCache" -> blockCache;
      case "protocolVersion" -> protocolVersion;
      default -> null;
    });
    UserRepository.manuallyRegisterUser(player, user);
    user.meta().abilities().setAllowFlying(true);
    user.meta().abilities().setFlying(flying);
    user.meta().abilities().setFlySpeed(FLY_SPEED);

    MovementMetadata environment = user.meta().movement();
    environment.updateMovement(POSITION, ROTATION);
    environment.setVerifiedLastPosition(POSITION, "flight test seed");
    environment.setLastPosition(POSITION);
    environment.setPose(Pose.STANDING);
    environment.setInWater(false);
    environment.setInLava(false);
    environment.onGround = lastOnGround;
    environment.setLastOnGround(lastOnGround);
    environment.setJumpMotion(0.42D);
    environment.setStepHeight(0.6F);
    return new TestContext(user, environment);
  }

  private record TestContext(User user, MovementMetadata environment) {
  }
}
