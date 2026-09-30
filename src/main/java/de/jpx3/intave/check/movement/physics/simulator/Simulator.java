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

package de.jpx3.intave.check.movement.physics.simulator;

import de.jpx3.intave.annotate.Immutable;
import de.jpx3.intave.annotate.Mutable;
import de.jpx3.intave.block.tick.BlockTickEntities;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.FLYING_PACKET_ACCURATE;

public abstract class Simulator {
  public void simulateBetween(
    User user, SimulationEnvironment environment,
    MovementConfiguration config
  ) {
    environment.setStepHeight(stepHeight(user));

    /*
     * Pre-tick
     */
    Motion lastMotion = environment.mutableBaseMotionCopy();
    Motion afterPreTickMotion = simulatePreTick(user, lastMotion, environment);

    Simulator tickSimulator = Simulators.selectFor(environment);
    environment.setSimulator(tickSimulator);
    environment.setStepHeight(tickSimulator.stepHeight(user));

    /*
     * Tick
     */
    Simulation simulation = tickSimulator.simulateTick(
      user, afterPreTickMotion.copy(), environment.immutableView(), config
    );
    environment.assumeOccurred(simulation);

    Position newPosition = simulation.postTickPosition();
    environment.updateMovement(newPosition, Rotation.zero());

    /*
     * Post-tick
     */
    Motion afterPostTickMotion = tickSimulator.simulateAfterTick(
      user, environment,
      config,
      environment.position(), simulation.actualMotion()
    );
    afterPostTickMotion = BlockTickEntities.tick(
      user, environment, environment.position(), afterPostTickMotion
    );
    environment.clearPostTickMotionCandidates();

    environment.setBaseMotion(afterPostTickMotion);
    environment.setLastOnGround(environment.onGround());
    environment.setVerifiedLastPosition(
      environment.position(), "AUTOACCEPT"
    );
  }

  public Simulator simulateAround(
    User user, SimulationEnvironment environment,
    Simulation simulation,
    Position sentPosition, Rotation sentRotation
  ) {
    // assume received
    Position verifiedLastPosition = environment.verifiedLastPosition();
    Motion actualMotion = simulation.actualMotion();
    Position firstTickPosition = simulation.postTickPosition();
    environment.updateMovement(firstTickPosition, null);
    environment.setLastPosition(verifiedLastPosition);
    environment.assumeOccurred(simulation);

    // after tick
    Motion afterTickMotion = simulateAfterTick(
      user, environment,
      simulation.configuration(),
      firstTickPosition, actualMotion
    );
    afterTickMotion = BlockTickEntities.tick(
      user, environment, firstTickPosition, afterTickMotion
    );
    environment.clearPostTickMotionCandidates();
    environment.setBaseMotion(afterTickMotion);
    environment.setLastOnGround(environment.onGround());
    environment.activeTick(FLYING_PACKET_ACCURATE);
    environment.setVerifiedLastPosition(environment.position(), "Two-tick flying simulation");
    environment.tickComplete(false, false, false);

    // receive new packet
    environment.updateMovement(sentPosition, sentRotation);

    environment.setStepHeight(stepHeight(user));
    return this;
  }

  /**
   * Simulate the movement before any inputs are used.
   */
  public abstract Motion simulatePreTick(
    User user,
    @Immutable Motion baseMotion,
    @Mutable SimulationEnvironment environment
  );

  /**
   * Simulate the entire movement until the position is updated.
   * This method is a function, so it does not change the metadata, the inputs or the motion.
   * It only returns the simulation result, which contains the new motion and collision results.
   */
  public abstract Simulation simulateTick(
    User user,
    @Immutable Motion motion,
    @Immutable SimulationEnvironment environment,
    @Immutable MovementConfiguration configuration
  );

  /**
   * Simulate all motion calculations that happen after the position-send.
   * The motion input can be either an offset motion or an actual motion, depending on the context.
   * A sent-offset-motion should be used as motion when no checking is performed, since this prevents simulation errors propagating.
   */
  public abstract Motion simulateAfterTick(
    User user,
    @Mutable SimulationEnvironment environment,
    @Immutable MovementConfiguration configuration,
    @Immutable Position position,
    @Immutable Motion motion
  );

  public float stepHeight(User user) {
    return 0.6f;
  }

  public boolean affectedByMovementKeys() {
    return true;
  }
}
