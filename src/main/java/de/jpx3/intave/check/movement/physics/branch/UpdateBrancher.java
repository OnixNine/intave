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
import de.jpx3.intave.check.movement.physics.update.CausalConstraint;
import de.jpx3.intave.check.movement.physics.update.TickAmbiguousUpdate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

public final class UpdateBrancher extends MovementSearchBrancher {
	@Override
	public void branch(MovementSearchInput input, MovementSearchBranch inputBranch, Collection<MovementSearchBranch> outputBranches) {
		SimulationEnvironment environment = input.environment();

		List<TickAmbiguousUpdate> updates = input.sortedPossibleTickAmbiguousUpdates();
		if (updates.isEmpty()) {
			outputBranches.add(inputBranch);
			return;
		}
		// Some updates MUST happen in this tick, so we enforce them and all before to happen now
		long lastVerifiedCompleteUpdate = Long.MIN_VALUE;
		long lastExplicitlyRequiredUpdate = Long.MIN_VALUE;
		long firstUpdateThatCannotBePostponed = Long.MAX_VALUE;
		for (TickAmbiguousUpdate update : updates) {
			CausalConstraint updateConstraint = update.constraint();
			if (updateConstraint.mustHappenThisTick(environment) && !updateConstraint.isOpenBounded()) {
				lastVerifiedCompleteUpdate = Math.max(lastVerifiedCompleteUpdate, updateConstraint.sequenceNumber());
			}
			if (update.mustRunBeforeExplicitTick()) {
				lastExplicitlyRequiredUpdate = Math.max(
					lastExplicitlyRequiredUpdate,
					updateConstraint.sequenceNumber()
				);
			}
			if (!update.canBePostponed(environment)) {
				firstUpdateThatCannotBePostponed = Math.min(
					firstUpdateThatCannotBePostponed,
					updateConstraint.sequenceNumber()
				);
			}
		}

		UnaryOperator<SimulationEnvironment> environmentUpdater = UnaryOperator.identity();
		List<UnaryOperator<SimulationEnvironment>> options = new ArrayList<>();
		List<Boolean> canFinishTick = new ArrayList<>();
		List<TickAmbiguousUpdate> updatesAppliedThisTick = new ArrayList<>();
		long appliedSequence = environment.activeSequence();
		int optionIndex = 0;
		long requiredForExplicitTick = Math.max(
			lastVerifiedCompleteUpdate, lastExplicitlyRequiredUpdate
		);

		while (true) {
			if (firstUpdateThatCannotBePostponed == Long.MAX_VALUE
				|| appliedSequence >= firstUpdateThatCannotBePostponed) {
				options.add(environmentUpdater);
				canFinishTick.add(appliedSequence >= requiredForExplicitTick);
			}
			if (optionIndex == updates.size()) {
				break;
			}

			TickAmbiguousUpdate update = updates.get(optionIndex++);
			if (!canRunInSameTick(update, updatesAppliedThisTick)) {
				break;
			}
			updatesAppliedThisTick.add(update);

			CausalConstraint constraint = update.constraint();
			environmentUpdater = andThen(environmentUpdater, env -> {
				update.applyTo(env);
				env.setActiveSequence(constraint.sequenceNumber());
				return env;
			});
			appliedSequence = constraint.sequenceNumber();
		}

		for (int i = options.size() - 1; i >= 0; i--) {
			UnaryOperator<SimulationEnvironment> envUpdate = options.get(i);
			boolean thisCanFinishTick = canFinishTick.get(i);
			MovementSearchBranch cfg = inputBranch;
			cfg = cfg.withAmbiguousUpdates(envUpdate, i, thisCanFinishTick);
			cfg = cfg.withExplicitTickFinishAllow(thisCanFinishTick);
			outputBranches.add(cfg);
		}
	}

	private static boolean canRunInSameTick(
		TickAmbiguousUpdate update, List<TickAmbiguousUpdate> appliedUpdates
	) {
		for (TickAmbiguousUpdate appliedUpdate : appliedUpdates) {
			if (!update.canRunInSameTickWith(appliedUpdate)
				|| !appliedUpdate.canRunInSameTickWith(update)) {
				return false;
			}
		}
		return true;
	}

	private static <T> UnaryOperator<T> andThen(UnaryOperator<T> first, UnaryOperator<T> second) {
		return t -> second.apply(first.apply(t));
	}
}
