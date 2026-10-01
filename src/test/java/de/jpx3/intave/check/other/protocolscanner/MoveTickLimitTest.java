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

package de.jpx3.intave.check.other.protocolscanner;

import de.jpx3.intave.user.meta.MovementMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MoveTickLimitTest {
  @Test
  void ignoresTeleportConfirmation() {
    MovementMetadata movement = new MovementMetadata(null, null);
    movement.isTeleportConfirmationPacket = true;

    assertTrue(MoveTickLimit.shouldIgnoreMovement(movement));
  }

  @Test
  void ignoresExpectedItemUseMovement() {
    MovementMetadata movement = new MovementMetadata(null, null);
    movement.awaitClickMovementSkip = true;
    movement.dropPostTickMotionProcessing = true;

    assertTrue(MoveTickLimit.shouldIgnoreMovement(movement));
  }

  @Test
  void doesNotIgnoreSingleItemUseFlag() {
    MovementMetadata movement = new MovementMetadata(null, null);
    movement.awaitClickMovementSkip = true;

    assertFalse(MoveTickLimit.shouldIgnoreMovement(movement));

    movement.awaitClickMovementSkip = false;
    movement.dropPostTickMotionProcessing = true;

    assertFalse(MoveTickLimit.shouldIgnoreMovement(movement));
  }
}
