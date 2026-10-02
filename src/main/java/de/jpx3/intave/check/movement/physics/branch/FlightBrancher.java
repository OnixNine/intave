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

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;

import java.util.Collection;

/**
 * A client flight-disable packet is sent before the movement packet both when
 * flight is manually disabled before travel and when landing disables flight
 * after travel. Both states are therefore valid for exactly that movement tick.
 */
final class FlightBrancher extends MovementSearchBrancher {
  @Override
  public void branch(
    MovementSearchInput input,
    MovementSearchBranch inputBranch,
    Collection<MovementSearchBranch> outputBranches
  ) {
    SimulationEnvironment environment = inputBranch.modifiedImmutableView(input);
    if (!environment.flying() || !environment.flyingDisablePending()) {
      outputBranches.add(inputBranch);
      return;
    }

    outputBranches.add(inputBranch.withFlying(true));
    outputBranches.add(inputBranch.withFlying(false));
  }
}
