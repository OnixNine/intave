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

package de.jpx3.intave.block.fluid;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;

public interface FluidFlow {
  boolean applyWaterFlowTo(
    User user, SimulationEnvironment environment,
    Motion baseMotion, BoundingBox boundingBox
  );

  boolean applyLavaFlowTo(
    User user, SimulationEnvironment environment,
    Motion baseMotion, BoundingBox boundingBox
  );

  default boolean updateWaterState(
    User user, SimulationEnvironment environment,
    Motion baseMotion, BoundingBox boundingBox,
    boolean applyFlow
  ) {
    Motion flowTarget = applyFlow ? baseMotion : Motion.newEmpty();
    return applyWaterFlowTo(user, environment, flowTarget, boundingBox);
  }

  default boolean updateLavaState(
    User user, SimulationEnvironment environment,
    Motion baseMotion, BoundingBox boundingBox,
    boolean applyFlow
  ) {
    Motion flowTarget = applyFlow ? baseMotion : Motion.newEmpty();
    return applyLavaFlowTo(user, environment, flowTarget, boundingBox);
  }

  double fluidDepthAt(User user, BoundingBox boundingBox);

  Motion pushMotionAt(User user, int blockX, int blockY, int blockZ);
}
