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

import de.jpx3.intave.search.SearchBrancher;

import java.util.Arrays;
import java.util.List;

public final class MovementSearchBranchers {
  private MovementSearchBranchers() {
  }

  public static List<SearchBrancher<MovementSearchInput, MovementSearchBranch>> tick() {
    return Arrays.asList(
      new RotationBrancher(),
      new PreviousPostTickBrancher(),
      new KeypressBrancher(),
      new CrouchingInputBrancher(),
      new SprintingBrancher(),
      new UpdateBrancher(),
      new UseItemBrancher(),
      JumpBrancher.restricted()
    );
  }

  public static List<SearchBrancher<MovementSearchInput, MovementSearchBranch>> afterTick() {
    return Arrays.asList(
      new UseLastMovementConfigBrancher(),
      new SprintingBrancher(),
      new ActualOrOffsetMotionBrancher(),
      new P773BlockInsideBrancher(),
      JumpBrancher.unrestricted()
    );
  }
}
