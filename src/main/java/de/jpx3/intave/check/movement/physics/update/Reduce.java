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

public final class Reduce extends TickAmbiguousUpdate {
	private final boolean lastActiveInTick;
	private final boolean mandatory;
	private final CausalConstraint constraint;

	public Reduce(boolean lastActiveInTick, CausalConstraint constraint) {
		this(lastActiveInTick, true, constraint);
	}

	public Reduce(boolean lastActiveInTick, boolean mandatory, CausalConstraint constraint) {
		this.lastActiveInTick = lastActiveInTick;
		this.mandatory = mandatory;
		this.constraint = constraint;
	}

	public boolean lastActiveInTick() {
		return lastActiveInTick;
	}

	public boolean mandatory() {
		return mandatory;
	}

	@Override
	public void applyTo(SimulationEnvironment environment) {
		Motion motion = environment.mutableBaseMotionCopy();
		motion.motionX *= 0.6D;
		motion.motionZ *= 0.6D;
		environment.setBaseMotion(motion);
	}

	@Override
	public CausalConstraint constraint() {
		return constraint;
	}

	@Override
	public boolean canBePostponed(SimulationEnvironment environment) {
		return !mandatory
			|| constraint.notBefore() >= environment.currentTick()
			|| appliedLastActiveReduceFor(environment);
	}

	@Override
	public boolean mustRunBeforeExplicitTick() {
		return mandatory;
	}

	@Override
	public boolean canRunInSameTickWith(TickAmbiguousUpdate other) {
		return !lastActiveInTick
			|| !(other instanceof Reduce)
			|| !((Reduce) other).lastActiveInTick;
	}

	@Override
	public boolean expired(SimulationEnvironment environment) {
		return environment.activeSequence() >= constraint.sequenceNumber()
			|| super.expired(environment);
	}

	public static Reduce openEnded(
		boolean lastActiveInTick, boolean mandatory, MovementMetadata metadata
	) {
		return new Reduce(
			lastActiveInTick,
			mandatory,
			CausalConstraint.openEnded(metadata.currentTick(), metadata.newSequenceNumber())
		);
	}

	private boolean appliedLastActiveReduceFor(SimulationEnvironment environment) {
		long activeSequence = environment.activeSequence();
		for (TickAmbiguousUpdate update : environment.allTickAmbiguousUpdates()) {
			if (update.constraint().sequenceNumber() > activeSequence) {
				continue;
			}
			if (update instanceof Reduce
				&& ((Reduce) update).lastActiveInTick
				&& update.constraint().notBefore() == constraint.notBefore()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return "Reduce{lastActiveInTick=" + lastActiveInTick
			+ ", mandatory=" + mandatory + ", at=" + constraint + '}';
	}
}
