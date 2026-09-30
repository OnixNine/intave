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

package de.jpx3.intave.check.movement.physics.environment;

public final class AttackCooldown {
	private int minimumTicksSinceLastAttack;

	public int minimumTicksSinceLastAttack() {
		return minimumTicksSinceLastAttack;
	}

	public void tick() {
		if (minimumTicksSinceLastAttack < Integer.MAX_VALUE) {
			minimumTicksSinceLastAttack++;
		}
	}

	public void reset() {
		minimumTicksSinceLastAttack = 0;
	}

	public boolean definitelyCharged(double attackSpeed) {
		if (!Double.isFinite(attackSpeed) || attackSpeed <= 0.0D) {
			return false;
		}
		float attackDelay = (float) (1.0D / attackSpeed * 20.0D);
		float attackStrength = Math.min(
			((float) minimumTicksSinceLastAttack + 0.5F) / attackDelay,
			1.0F
		);
		return attackStrength > 0.9F;
	}
}
