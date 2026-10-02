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

package de.jpx3.intave.check.movement.physics.branch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.share.Input;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_20_5;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JumpBrancherFlightTest {
  @BeforeEach
  void setUp() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test
  void restrictedTickSearchStillBranchesAirborneFlightAscent() {
    User user = user(VER_1_20_5);
    MockSimulationEnvironment environment = airborneEnvironment(user);

    List<MovementSearchBranch> groundedBranches = branches(user, environment);
    assertFalse(groundedBranches.stream().anyMatch(MovementSearchBranch::isJumping));

    user.meta().abilities().setAllowFlying(true);
    user.meta().abilities().setFlying(true);
    List<MovementSearchBranch> flightBranches = branches(user, environment);
    assertTrue(flightBranches.stream().anyMatch(MovementSearchBranch::isJumping));
    assertTrue(flightBranches.stream().anyMatch(branch -> !branch.isJumping()));
  }

  @Test
  void modernInputPacketMakesActiveFlightJumpExact() {
    User user = user(VER_1_21_2);
    MockSimulationEnvironment environment = airborneEnvironment(user);
    user.meta().abilities().setAllowFlying(true);
    user.meta().abilities().setFlying(true);

    user.meta().movement().input = new Input(
      false, false, false, false, true, false, false
    );
    List<MovementSearchBranch> jumpingBranches = branches(user, environment);
    assertEquals(1, jumpingBranches.size());
    assertTrue(jumpingBranches.get(0).isJumping());

    user.meta().movement().input = Input.none();
    List<MovementSearchBranch> idleBranches = branches(user, environment);
    assertEquals(1, idleBranches.size());
    assertFalse(idleBranches.get(0).isJumping());
  }

  private static List<MovementSearchBranch> branches(
    User user,
    MockSimulationEnvironment environment
  ) {
    MovementSearchInput input = MovementSearchInput.forTick(
      user, Simulators.PLAYER, environment, false
    );
    List<MovementSearchBranch> output = new ArrayList<>();
    JumpBrancher.restricted().branch(input, MovementSearchBranch.blank(input), output);
    return output;
  }

  private static MockSimulationEnvironment airborneEnvironment(User user) {
    MockSimulationEnvironment environment = new MockSimulationEnvironment(user);
    environment.setLastOnGround(false);
    environment.setInWater(false);
    environment.setInLava(false);
    environment.setJumpMotion(0.42D);
    return environment;
  }

  private static User user(int protocolVersion) {
    World world = FakeWorldFactory.createWorld((name, args) -> null);
    UUID id = UUID.randomUUID();
    return UserFactory.createTestUserFor(
      FakePlayerFactory.createPlayer((name, args) -> switch (name) {
        case "getWorld" -> world;
        case "getLocation" -> new Location(world, 0.0D, 64.0D, 0.0D);
        case "getUniqueId" -> id;
        default -> null;
      }),
      (ignored, key) -> switch (key) {
        case "blockCache" -> new MockFullBlockStaticPlane();
        case "protocolVersion" -> protocolVersion;
        default -> null;
      }
    );
  }
}
