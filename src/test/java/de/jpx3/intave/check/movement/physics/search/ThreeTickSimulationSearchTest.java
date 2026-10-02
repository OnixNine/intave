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

package de.jpx3.intave.check.movement.physics.search;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.PostTickSimulation;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
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

import java.util.List;
import java.util.UUID;

import static de.jpx3.intave.check.movement.physics.search.PostTickMotionType.SIMULATED_MOTION;
import static de.jpx3.intave.check.movement.physics.search.PostTickMotionType.SENT_OFFSET_MOTION;
import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThreeTickSimulationSearchTest {
  private static final Position POSITION = Position.of(0.5, 50.0, 0.5);
  private static final Motion MOTION = new Motion(0.1, 0.2, 0.3);

  @BeforeEach
  void setUp() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test
  void setbackPositionAdvancesRequestedTicksWithoutChangingLiveState() {
    for (int ticksAhead : new int[] {0, 1, 3}) {
      assertSetbackPosition(ticksAhead);
    }
  }

  private void assertSetbackPosition(int ticksAhead) {
    User user = user(VER_1_8);
    MovementMetadata environment = user.meta().movement();
    environment.updateMovement(POSITION, Rotation.zero());
    environment.setVerifiedLastPosition(POSITION, "post-tick teleport test");
    environment.setLastPosition(POSITION);
    environment.setBaseMotion(new Motion(9, 8, 7));
    environment.gravity = 0.08;
    environment.setLastOnGround(false);
    Motion input = new Motion(0.1, 0.2, 0.3);
    Simulation simulation = Simulation.of(user, MovementConfiguration.blank(),
      environment.immutableView(), SimulationResult.untouched(input)).reusableCopy();

    PositionMoveRotation next = simulation.setbackPosition(user, ticksAhead).change();

    double x = POSITION.getX() + 0.1, y = POSITION.getY() + 0.2, z = POSITION.getZ() + 0.3;
    double motionX = 0.1 * (double) 0.91F;
    double motionY = (0.2 - 0.08) * (double) 0.98F;
    double motionZ = 0.3 * (double) 0.91F;
    for (int tick = 0; tick < ticksAhead; tick++) {
      x += motionX;
      y += motionY;
      z += motionZ;
      motionX *= (double) 0.91F;
      motionY = (motionY - 0.08) * (double) 0.98F;
      motionZ *= (double) 0.91F;
    }
    assertEquals(x, next.position().getX(), 1.0E-12);
    assertEquals(y, next.position().getY(), 1.0E-12);
    assertEquals(z, next.position().getZ(), 1.0E-12);
    assertEquals(motionX, next.motion().motionX(), 1.0E-12);
    assertEquals(motionY, next.motion().motionY(), 1.0E-12);
    assertEquals(motionZ, next.motion().motionZ(), 1.0E-12);
    assertEquals(new Motion(0.1, 0.2, 0.3), input);
    assertEquals(new Motion(9, 8, 7), environment.mutableBaseMotionCopy());
    assertEquals(POSITION, environment.position());
  }

  @Test
  void retainsSprintProvenanceWhenAfterTickMotionDoesNotReadSprinting() {
    User user = user();
    MovementMetadata environment = user.meta().movement();
    environment.updateMovement(POSITION, Rotation.zero());
    environment.setVerifiedLastPosition(POSITION, "after-tick candidate test seed");
    environment.setLastPosition(POSITION);
    environment.setSimulationResult(SimulationResult.untouched(MOTION.copy()));
    environment.setLastMovementConfiguration(MovementConfiguration.blank());
    environment.hasSprintSpeed = true;

    ThreeTickSimulationSearch search = new ThreeTickSimulationSearch(false);
    List<PostTickSimulation> candidates = search.afterTickMotionCandidates(
      user,
      environment,
      new SprintIndependentAfterTickSimulator(),
      POSITION,
      SIMULATED_MOTION
    );

    assertEquals(2, candidates.size());
    assertTrue(candidates.stream().allMatch(candidate -> candidate.motion().equals(MOTION)));
    assertTrue(candidates.stream().anyMatch(PostTickSimulation::priorSprinting));
    assertTrue(candidates.stream().anyMatch(candidate -> !candidate.priorSprinting()));
  }

  @Test
  void activeFlightUsesSimulatedTravelMotionForPostTickVelocity() {
    User user = user();
    MovementMetadata environment = user.meta().movement();
    Motion sentOffset = new Motion(0.4D, 0.5D, 0.6D);
    environment.setLastPosition(POSITION);
    environment.updateMovement(POSITION.add(sentOffset), Rotation.zero());
    environment.setVerifiedLastPosition(POSITION, "flight after-tick candidate test seed");
    environment.setSimulationResult(SimulationResult.untouched(MOTION.copy()));
    environment.setLastMovementConfiguration(MovementConfiguration.blank());
    user.meta().abilities().setAllowFlying(true);
    user.meta().abilities().setFlying(true);

    List<PostTickSimulation> candidates = new ThreeTickSimulationSearch(false)
      .afterTickMotionCandidates(
        user,
        environment,
        new SprintIndependentAfterTickSimulator(),
        environment.position(),
        SENT_OFFSET_MOTION
      );

    assertEquals(1, candidates.size());
    assertTrue(candidates.stream().allMatch(candidate -> candidate.motion().equals(MOTION)));
  }

  @Test
  void flightDisablePendingRetainsBothPostTickFlightStates() {
    for (boolean selectedFlying : new boolean[] {true, false}) {
      User user = user(VER_26_3);
      MovementMetadata environment = user.meta().movement();
      environment.setLastPosition(POSITION);
      environment.updateMovement(POSITION.add(MOTION), Rotation.zero());
      environment.setVerifiedLastPosition(POSITION, "flight transition candidate test seed");
      environment.setSimulationResult(SimulationResult.untouched(MOTION.copy()));
      environment.setLastMovementConfiguration(MovementConfiguration.blank());
      user.meta().abilities().setAllowFlying(true);
      user.meta().abilities().setFlying(true);
      user.meta().abilities().disabledFlying = true;
      environment.setFlying(selectedFlying);

      List<PostTickSimulation> candidates = new ThreeTickSimulationSearch(false)
        .afterTickMotionCandidates(
          user,
          environment,
          new FlightStateAfterTickSimulator(),
          environment.position(),
          SENT_OFFSET_MOTION
        );

      assertEquals(2, candidates.size());
      assertTrue(candidates.stream().anyMatch(candidate -> candidate.motion().motionY() == 1.0D));
      assertTrue(candidates.stream().anyMatch(candidate -> candidate.motion().motionY() == -1.0D));
    }
  }

  @Test
  void branchesTheBlockInsideCheckOnlyForSharedProtocol773() {
    List<PostTickSimulation> sharedProtocolCandidates = afterTickCandidates(
      user(VER_1_21_9), new BlockInsideVersionAfterTickSimulator()
    );
    assertEquals(2, sharedProtocolCandidates.size());
    assertTrue(sharedProtocolCandidates.stream().anyMatch(candidate -> candidate.motion().equals(MOTION)));
    assertTrue(sharedProtocolCandidates.stream().anyMatch(candidate -> candidate.motion().motionX() == MOTION.motionX() + 1.0));

    List<PostTickSimulation> protocol772Candidates = afterTickCandidates(
      user(VER_1_21_7), new BlockInsideVersionAfterTickSimulator()
    );
    assertEquals(1, protocol772Candidates.size());
    assertTrue(protocol772Candidates.get(0).motion().equals(MOTION));
  }

  private static List<PostTickSimulation> afterTickCandidates(User user, Simulator simulator) {
    MovementMetadata environment = user.meta().movement();
    environment.updateMovement(POSITION, Rotation.zero());
    environment.setVerifiedLastPosition(POSITION, "after-tick candidate test seed");
    environment.setLastPosition(POSITION);
    environment.setSimulationResult(SimulationResult.untouched(MOTION.copy()));
    environment.setLastMovementConfiguration(MovementConfiguration.blank());
    return new ThreeTickSimulationSearch(false).afterTickMotionCandidates(
      user, environment, simulator, POSITION, SIMULATED_MOTION
    );
  }

  private static User user() {
    return user(VER_1_21_5);
  }

  private static User user(int protocolVersion) {
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
        case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
        case "isDead" -> false;
        default -> null;
      }
    );
    User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
      case "blockCache" -> new MockFullBlockStaticPlane();
      case "protocolVersion" -> protocolVersion;
      default -> null;
    });
    UserRepository.manuallyRegisterUser(player, user);
    return user;
  }

  private static final class SprintIndependentAfterTickSimulator extends Simulator {
    @Override
    public Motion simulatePreTick(
      User user,
      Motion baseMotion,
      SimulationEnvironment environment
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Simulation simulateTick(
      User user,
      Motion motion,
      SimulationEnvironment environment,
      MovementConfiguration configuration
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Motion simulateAfterTick(
      User user,
      SimulationEnvironment environment,
      MovementConfiguration configuration,
      Position position,
      Motion motion
    ) {
      return motion.copy();
    }

  }

  private static final class BlockInsideVersionAfterTickSimulator extends Simulator {
    @Override
    public Motion simulatePreTick(User user, Motion baseMotion, SimulationEnvironment environment) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Simulation simulateTick(
      User user,
      Motion motion,
      SimulationEnvironment environment,
      MovementConfiguration configuration
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Motion simulateAfterTick(
      User user,
      SimulationEnvironment environment,
      MovementConfiguration configuration,
      Position position,
      Motion motion
    ) {
      Motion output = motion.copy();
      if (configuration.usesAlternateBlockInsideCheck()) {
        output.motionX += 1.0;
      }
      return output;
    }

  }

  private static final class FlightStateAfterTickSimulator extends Simulator {
    @Override
    public Motion simulatePreTick(User user, Motion baseMotion, SimulationEnvironment environment) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Simulation simulateTick(
      User user,
      Motion motion,
      SimulationEnvironment environment,
      MovementConfiguration configuration
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Motion simulateAfterTick(
      User user,
      SimulationEnvironment environment,
      MovementConfiguration configuration,
      Position position,
      Motion motion
    ) {
      Motion output = motion.copy();
      output.motionY = environment.flying() ? 1.0D : -1.0D;
      return output;
    }
  }
}
