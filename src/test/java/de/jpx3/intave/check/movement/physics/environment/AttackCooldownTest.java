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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AttackCooldownTest {
	@Test
	void invalidAttackSpeedsCannotProveAChargedAttack() {
		AttackCooldown cooldown = new AttackCooldown();
		for (int tick = 0; tick < 30; tick++) cooldown.tick();
		for (double speed : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertFalse(cooldown.definitelyCharged(speed));
		}
	}

	@Test
	void slowerWeaponsUseTheirOwnRechargeDelay() {
		AttackCooldown cooldown = new AttackCooldown();
		for (int tick = 0; tick < 10; tick++) cooldown.tick();
		assertFalse(cooldown.definitelyCharged(1.6D));
		cooldown.tick();
		assertTrue(cooldown.definitelyCharged(1.6D));
	}
	@Test
	void usesStrictClientChargeBoundary() {
		AttackCooldown cooldown = new AttackCooldown();

		for (int tick = 0; tick < 4; tick++) {
			cooldown.tick();
		}

		assertFalse(cooldown.definitelyCharged(4.0D));
		cooldown.tick();
		assertTrue(cooldown.definitelyCharged(4.0D));
	}

	@Test
	void attackResetsTheLowerBound() {
		AttackCooldown cooldown = new AttackCooldown();
		for (int tick = 0; tick < 5; tick++) {
			cooldown.tick();
		}

		cooldown.reset();

		assertEquals(0, cooldown.minimumTicksSinceLastAttack());
		assertFalse(cooldown.definitelyCharged(4.0D));
	}

	@Test
	void viaVersionAttackSpeedChargesAfterOneTick() {
		AttackCooldown cooldown = new AttackCooldown();

		assertFalse(cooldown.definitelyCharged(20.0D));
		cooldown.tick();
		assertTrue(cooldown.definitelyCharged(20.0D));
	}
}
