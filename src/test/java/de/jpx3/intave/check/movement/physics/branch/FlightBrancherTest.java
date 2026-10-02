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

import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class FlightBrancherTest {
  @Test
  void pendingDisableBranchesCurrentTickIntoBothClientOrders() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setFlying(true);
    environment.setFlyingDisablePending(true);

    List<MovementSearchBranch> branches = branches(environment);

    assertEquals(2, branches.size());
    Set<Boolean> flyingStates = branches.stream()
      .map(branch -> branch.modifiedImmutableView(environment).flying())
      .collect(Collectors.toSet());
    assertEquals(new HashSet<>(Arrays.asList(true, false)), flyingStates);
  }

  @Test
  void normalAllowedFlightDoesNotCreateAnExtraBranch() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setFlying(true);
    environment.setFlyingDisablePending(false);

    assertEquals(1, branches(environment).size());
  }

  @Test
  void pendingDisableIsConsumedAfterTheAmbiguousTick() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setFlying(true);
    environment.setFlyingDisablePending(true);

    environment.tickComplete(true, false, false);

    assertFalse(environment.flying());
    assertFalse(environment.flyingDisablePending());
    assertEquals(1, branches(environment).size());
  }

  private static List<MovementSearchBranch> branches(
    SimulationEnvironment environment
  ) {
    MovementSearchInput input = MovementSearchInput.forTick(
      null, null, environment, false
    );
    List<MovementSearchBranch> output = new ArrayList<>();
    new FlightBrancher().branch(input, MovementSearchBranch.blank(input), output);
    return output;
  }
}
