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
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.meta.MovementMetadata;

public final class MotionAddUpdate extends TickAmbiguousUpdate {
	private final Motion motion;
	private CausalConstraint causalConstraint;

	private MotionAddUpdate(Motion motion, CausalConstraint causalConstraint) {
		this.motion = motion;
		this.causalConstraint = causalConstraint;
	}

	@Override
	public void applyTo(SimulationEnvironment environment) {
		environment.setBaseMotion(
			environment.mutableBaseMotionCopy().add(motion)
		);
	}

	@Override
	public CausalConstraint constraint() {
		return causalConstraint;
	}

	public void canNotRunAfterThisTick(
		SimulationEnvironment environment
	) {
		long currentTick = environment.currentTick();
		causalConstraint = causalConstraint.notAfter(currentTick);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof MotionAddUpdate) {
			MotionAddUpdate other = (MotionAddUpdate) obj;
			return motion.equals(other.motion) && causalConstraint.equals(other.causalConstraint);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return motion.hashCode() ^ causalConstraint.hashCode();
	}

	@Override
	public String toString() {
		return "MotionAddUpdate{motion=" + motion + ", at="+causalConstraint+"}";
	}

	public static MotionAddUpdate openEnded(
		Motion motion, MovementMetadata metadata
	) {
		long sequenceNumber = metadata.newSequenceNumber();
		return new MotionAddUpdate(
			motion.copy(),
			CausalConstraint.openEnded(
				metadata.currentTick(), sequenceNumber
			)
		);
	}
}
