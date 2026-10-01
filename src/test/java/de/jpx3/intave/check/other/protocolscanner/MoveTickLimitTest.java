package de.jpx3.intave.check.other.protocolscanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MoveTickLimitTest {
  @Test
  void countsOnlyOneMovementPacketPerClientTick() {
    MoveTickLimit.MovementPacketWindow window = new MoveTickLimit.MovementPacketWindow();

    assertEquals(1, window.recordMovement());
    assertEquals(2, window.recordMovement());
    assertEquals(3, window.recordMovement());
  }

  @Test
  void clientTickEndStartsANewMovementPacketWindow() {
    MoveTickLimit.MovementPacketWindow window = new MoveTickLimit.MovementPacketWindow();

    assertEquals(1, window.recordMovement());
    assertEquals(2, window.recordMovement());

    window.startNextTick();

    assertEquals(1, window.recordMovement());
  }

  @Test
  void emptyClientTicksAlsoResetTheWindow() {
    MoveTickLimit.MovementPacketWindow window = new MoveTickLimit.MovementPacketWindow();

    window.startNextTick();
    window.startNextTick();

    assertEquals(1, window.recordMovement());
  }

  @Test
  void teleportConfirmationIsNotNormalMovement() {
    assertFalse(MoveTickLimit.shouldCountAsNormalMovement(true, false, false));
  }

  @Test
  void itemUseMovementIsNotNormalMovement() {
    assertFalse(MoveTickLimit.shouldCountAsNormalMovement(false, true, true));
  }

  @Test
  void unrelatedDroppedOrPendingStateDoesNotHideMovement() {
    assertTrue(MoveTickLimit.shouldCountAsNormalMovement(false, true, false));
    assertTrue(MoveTickLimit.shouldCountAsNormalMovement(false, false, true));
  }
}
