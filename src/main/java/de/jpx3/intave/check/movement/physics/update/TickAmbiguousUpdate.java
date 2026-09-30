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

package de.jpx3.intave.check.movement.physics.update;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import org.jetbrains.annotations.NotNull;

public abstract class TickAmbiguousUpdate implements Comparable<TickAmbiguousUpdate> {
	public abstract void applyTo(SimulationEnvironment environment);

	public abstract CausalConstraint constraint();

	/**
	 * Whether this update may remain pending after the current simulated tick.
	 */
	public boolean canBePostponed(SimulationEnvironment environment) {
		return true;
	}

	/**
	 * Whether an explicit movement tick may finish while this update is still pending.
	 */
	public boolean mustRunBeforeExplicitTick() {
		return false;
	}

	/**
	 * Whether this update is compatible with another update in the same simulated tick.
	 */
	public boolean canRunInSameTickWith(TickAmbiguousUpdate other) {
		return true;
	}

	public boolean expired(SimulationEnvironment environment) {
		return constraint().expired(environment);
	}

	@Override
	public int compareTo(@NotNull TickAmbiguousUpdate o) {
		return Long.compare(this.constraint().sequenceNumber(), o.constraint().sequenceNumber());
	}

	public boolean possible(SimulationEnvironment simulationEnvironment) {
		return constraint().possible(simulationEnvironment);
	}
}
