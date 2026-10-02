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

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

import java.lang.reflect.Method;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.WATERFLOW_PUSH;
import static de.jpx3.intave.share.ClientMath.ceil;
import static de.jpx3.intave.share.ClientMath.floor;

final class v13Fluidflow implements FluidFlow {
  private static final Method IS_ULTRA_WARM = ultraWarmMethod();

  @Override
  public boolean applyWaterFlowTo(
    User user, SimulationEnvironment environment,
    Motion baseMotion, BoundingBox boundingBox
  ) {
    return updateWaterState(user, environment, baseMotion, boundingBox, true);
  }

  @Override
  public boolean updateWaterState(
    User user, SimulationEnvironment environment,
    Motion baseMotion, BoundingBox boundingBox,
    boolean applyFlow
  ) {
    BoundingBox wrappedBoundingBox = boundingBox.shrink(0.001D);
    int minX = floor(wrappedBoundingBox.minX);
    int minY = floor(wrappedBoundingBox.minY);
    int minZ = floor(wrappedBoundingBox.minZ);
    int maxX = ceil(wrappedBoundingBox.maxX);
    int maxY = ceil(wrappedBoundingBox.maxY);
    int maxZ = ceil(wrappedBoundingBox.maxZ);

    double largestFluidDepth = 0;
    boolean inWater = false;
    Motion waterFlowTotal = null;
    int countedWaterCollisions = 0;
    boolean doublePrecision = user.meta().protocol().fluidHeightUsesDoublePrecision();

    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Fluid fluid = VolatileBlockAccess.fluidAccess(user, x, y, z);
          if (fluid.isOfWater()) {
            double fluidSurfaceY = fluidSurfaceY(y, fluid.height(), doublePrecision);
            if (fluidSurfaceY >= wrappedBoundingBox.minY) {
              inWater = true;
              largestFluidDepth = Math.max(fluidSurfaceY - wrappedBoundingBox.minY, largestFluidDepth);
              Motion pushMotion = pushMotionAt(user, x, y, z);
              if (largestFluidDepth < 0.4) {
                pushMotion.multiply(largestFluidDepth);
              }
              if (waterFlowTotal == null) {
                waterFlowTotal = new Motion();
              }
              waterFlowTotal.add(pushMotion);
              ++countedWaterCollisions;
            }
          }
        }
      }
    }

    if (applyFlow && waterFlowTotal != null && waterFlowTotal.length() > 0.0D) {
      if (countedWaterCollisions > 0) {
        waterFlowTotal.multiply(1.0D / (double) countedWaterCollisions);
      }

      double d2 = 0.014d;
      waterFlowTotal.multiply(d2);

      if (Math.abs(baseMotion.motionX) < 0.003D &&
        Math.abs(baseMotion.motionZ) < 0.003D &&
        waterFlowTotal.length() < 0.0045000000000000005D
      ) {
        waterFlowTotal.normalize().multiply(0.0045000000000000005D);
      }

      // add is in-place
	    baseMotion.add(waterFlowTotal);
	    environment.activeTick(WATERFLOW_PUSH);
    }
    return inWater;
  }

  @Override
  public boolean applyLavaFlowTo(
    User user, SimulationEnvironment environment,
    Motion baseMotion, BoundingBox boundingBox
  ) {
    return updateLavaState(user, environment, baseMotion, boundingBox, true);
  }

  @Override
  public boolean updateLavaState(
    User user, SimulationEnvironment environment,
    Motion baseMotion, BoundingBox boundingBox,
    boolean applyFlow
  ) {
    BoundingBox wrappedBoundingBox = boundingBox.shrink(0.001D);
    int minX = floor(wrappedBoundingBox.minX);
    int minY = floor(wrappedBoundingBox.minY);
    int minZ = floor(wrappedBoundingBox.minZ);
    int maxX = ceil(wrappedBoundingBox.maxX);
    int maxY = ceil(wrappedBoundingBox.maxY);
    int maxZ = ceil(wrappedBoundingBox.maxZ);

    boolean doublePrecision = user.meta().protocol().fluidHeightUsesDoublePrecision();
    double depthBaseY = user.meta().protocol().refreshesFluidStateAfterMove()
      ? boundingBox.minY
      : wrappedBoundingBox.minY;
    double largestFluidDepth = 0.0D;
    boolean inLava = false;
    Motion lavaFlowTotal = null;
    int countedLavaCollisions = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Fluid fluid = VolatileBlockAccess.fluidAccess(user, x, y, z);
          if (!fluid.isOfLava()) {
            continue;
          }

          Fluid fluidAbove = VolatileBlockAccess.fluidAccess(user, x, y + 1, z);
          float fluidHeight = fluid.similarTo(fluidAbove) ? 1.0F : fluid.height();
          double fluidSurfaceY = doublePrecision
            ? y + (double) fluidHeight
            : (double) ((float) y + fluidHeight);
          if (fluidSurfaceY < wrappedBoundingBox.minY) {
            continue;
          }

          inLava = true;
          largestFluidDepth = Math.max(fluidSurfaceY - depthBaseY, largestFluidDepth);
          Motion pushMotion = pushMotionAt(user, x, y, z);
          if (largestFluidDepth < 0.4D) {
            pushMotion.multiply(largestFluidDepth);
          }
          if (lavaFlowTotal == null) {
            lavaFlowTotal = new Motion();
          }
          lavaFlowTotal.add(pushMotion);
          ++countedLavaCollisions;
        }
      }
    }

    if (inLava) {
      environment.setLavaDepth(largestFluidDepth);
    }

    if (applyFlow && lavaFlowTotal != null && countedLavaCollisions > 0) {
      boolean currentPresent = user.meta().protocol().refreshesFluidStateAfterMove()
        ? lavaFlowTotal.lengthSquared() >= (double) 1.0E-5F
        : lavaFlowTotal.length() > 0.0D;
      if (currentPresent) {
        lavaFlowTotal.multiply(1.0D / (double) countedLavaCollisions);
        lavaFlowTotal.multiply(lavaFlowScale(user.player().getWorld()));
        if (Math.abs(baseMotion.motionX) < 0.003D
          && Math.abs(baseMotion.motionZ) < 0.003D
          && lavaFlowTotal.length() < 0.0045000000000000005D) {
          lavaFlowTotal.normalize().multiply(0.0045000000000000005D);
        }
        baseMotion.add(lavaFlowTotal);
      }
    }
    return inLava;
  }

  @Override
  public double fluidDepthAt(User user, BoundingBox boundingBox) {
    double largestFluidDepth = 0;
    boolean doublePrecision = user.meta().protocol().fluidHeightUsesDoublePrecision();
    BoundingBox wrappedBoundingBox = boundingBox.shrink(0.001D);
    int minX = floor(wrappedBoundingBox.minX);
    int minY = floor(wrappedBoundingBox.minY);
    int minZ = floor(wrappedBoundingBox.minZ);
    int maxX = ceil(wrappedBoundingBox.maxX);
    int maxY = ceil(wrappedBoundingBox.maxY);
    int maxZ = ceil(wrappedBoundingBox.maxZ);
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Fluid fluid = VolatileBlockAccess.fluidAccess(user, x, y, z);
          if (fluid.isOfWater()) {
            double fluidSurfaceY = fluidSurfaceY(y, fluid.height(), doublePrecision);
            if (fluidSurfaceY >= wrappedBoundingBox.minY) {
              largestFluidDepth = Math.max(fluidSurfaceY - wrappedBoundingBox.minY, largestFluidDepth);
            }
          }
        }
      }
    }
    return largestFluidDepth;
  }

  static double fluidSurfaceY(int blockY, float fluidHeight, boolean doublePrecision) {
    return doublePrecision
      ? blockY + (double) fluidHeight
      : (double) ((float) blockY + fluidHeight);
  }

  @Override
  public Motion pushMotionAt(User user, int blockX, int blockY, int blockZ) {
    double flowX = 0.0D;
    double flowZ = 0.0D;

    Fluid fluid = VolatileBlockAccess.fluidAccess(user, blockX, blockY, blockZ);
    for (Direction direction : Direction.Plane.HORIZONTAL) {
      BlockPosition position = new BlockPosition(blockX, blockY, blockZ).offset(direction);
      Fluid adjacentFluid = VolatileBlockAccess.fluidAccess(user, position.x(), position.y(), position.z());

      if (fluid.affectsFlow(adjacentFluid)) {
        float adjacentHeight = adjacentFluid.height();
        float heightDifference = 0.0F;

        if (adjacentHeight == 0) {
          if (!blocksMovement(user, position)) {
            BlockPosition below = position.down();
            Fluid belowFluid = VolatileBlockAccess.fluidAccess(user, below.x(), below.y(), below.z());
            if (fluid.affectsFlow(belowFluid)) {
              adjacentHeight = belowFluid.height();
              if (adjacentHeight > 0) {
                heightDifference = fluid.height() - (adjacentHeight - 0.8888889F);
              }
            }
          }
        } else if (adjacentHeight > 0) {
          heightDifference = fluid.height() - adjacentHeight;
        }

        if (heightDifference != 0) {
          flowX += direction.offsetX() * heightDifference;
          flowZ += direction.offsetZ() * heightDifference;
        }
      }
    }

    Motion flow = new Motion(flowX, 0.0D, flowZ);
    if (fluid.falling()) {
      for (Direction direction : Direction.Plane.HORIZONTAL) {
        BlockPosition position = new BlockPosition(blockX, blockY, blockZ).offset(direction);
        if (isBlockSolid(user, position, direction) || isBlockSolid(user, position.up(), direction)) {
          flow.normalize().add(0.0D, -6.0D, 0.0D);
          break;
        }
      }
    }

    return flow.normalize();
  }

  private static boolean blocksMovement(User user, BlockPosition position) {
    Material type = VolatileBlockAccess.typeAccess(user, user.player().getWorld(), position.x(), position.y(), position.z());
    if (user.meta().protocol().fluidFlowBlockingTag()) {
      return FluidFlowBlocking.contains(type);
    }
    return MaterialMagic.blocksMovement(type);
  }

  private static boolean isBlockSolid(User user, BlockPosition pos, Direction side) {
    World world = user.player().getWorld();
    Material type = VolatileBlockAccess.typeAccess(user, world, pos.x(), pos.y(), pos.z());
    int variantIndex = VolatileBlockAccess.variantIndexAccess(user, world, pos.x(), pos.y(), pos.z());
    if (MaterialMagic.couldContainLiquid(type, variantIndex)) {
      return false;
    }
    return side == Direction.UP || (type != Material.ICE && MaterialMagic.blockSolid(type));
  }

  private static double lavaFlowScale(World world) {
    if (IS_ULTRA_WARM != null) {
      try {
        if (Boolean.TRUE.equals(IS_ULTRA_WARM.invoke(world))) {
          return 0.007D;
        }
      } catch (ReflectiveOperationException ignored) {
      }
    }
    try {
      if (world.getEnvironment() == World.Environment.NETHER) {
        return 0.007D;
      }
    } catch (UnsupportedOperationException ignored) {
    }
    return 0.0023333333333333335D;
  }

  private static Method ultraWarmMethod() {
    try {
      return World.class.getMethod("isUltraWarm");
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }
}
