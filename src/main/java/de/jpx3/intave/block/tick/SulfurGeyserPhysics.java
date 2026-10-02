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

package de.jpx3.intave.block.tick;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.GameMode;
import org.bukkit.Material;

public final class SulfurGeyserPhysics {
	static final int ALLOWED_WATER_BLOCKS_ABOVE = 4;
	static final int MAX_LAUNCH_HEIGHT = 6 * ALLOWED_WATER_BLOCKS_ABOVE;
	static final float BASE_LAUNCH_SPEED = 0.3F;
	static final float LAUNCH_FORCE = 0.2F;

	private static final Material POTENT_SULFUR = Material.getMaterial("POTENT_SULFUR");

	private SulfurGeyserPhysics() {
	}

	public static Motion applyAfterPlayerTick(User user, SimulationEnvironment environment, Position position, Motion motion) {
		MetadataBundle meta = user.meta();
		ProtocolMetadata protocol = meta.protocol();
		AbilityMetadata abilities = meta.abilities();
		if (!protocol.supportsSulfurGeysers() || POTENT_SULFUR == null
			|| abilities.inGameMode(GameMode.SPECTATOR) || user.player().isDead()) {
			return motion;
		}
		return applyAfterPlayerTick(
			user, environment,
			BoundingBox.fromPosition(user, environment, position), motion,
			environment.flying(), environment.isInVehicle()
		);
	}

	static Motion applyAfterPlayerTick(
		User user, SimulationEnvironment environment,
		BoundingBox entityBox, Motion motion,
		boolean flying, boolean passenger
	) {
		Motion result = motion;
		int minimumX = floor(entityBox.minX);
		int maximumX = ceil(entityBox.maxX) - 1;
		int minimumY = floor(entityBox.minY) - MAX_LAUNCH_HEIGHT;
		int maximumY = ceil(entityBox.maxY) - 1;
		int minimumZ = floor(entityBox.minZ);
		int maximumZ = ceil(entityBox.maxZ) - 1;

		// wtf mojang
		for (int blockX = minimumX; blockX <= maximumX; blockX++) {
			for (int blockZ = minimumZ; blockZ <= maximumZ; blockZ++) {
				for (int blockY = minimumY; blockY <= maximumY; blockY++) {
					if (!activeGeyserAt(user, blockX, blockY, blockZ)) {
						continue;
					}
					int waterBlocks = findWaterBlocks(user, blockX, blockY, blockZ);
					if (waterBlocks < 0) {
						continue;
					}
					int launchHeight = unobstructedLaunchHeight(user, blockX, blockY, blockZ, waterBlocks);
					BoundingBox launchArea = launchArea(blockX, blockY, blockZ, launchHeight);
					result = applyGeyserLaunch(
						environment, entityBox, result, launchArea,
						waterBlocks, flying, passenger
					);
				}
			}
		}
		return result;
	}

	static Motion applyGeyserLaunch(
		SimulationEnvironment environment,
		BoundingBox entityBox, Motion motion, BoundingBox launchArea,
		int waterBlocks, boolean flying, boolean passenger
	) {
		if (!launchArea.intersectsWith(entityBox)) {
			return motion;
		}

		capAccumulatedFallDistance(environment, motion.motionY());
		double launchSpeedLimit = BASE_LAUNCH_SPEED + waterBlocks * 0.1D;
		if (flying || passenger || motion.motionY() >= launchSpeedLimit) {
			return motion;
		}
		return new Motion(motion.motionX(), motion.motionY() + (double) LAUNCH_FORCE, motion.motionZ());
	}

	private static int findWaterBlocks(User user, int sulfurX, int sulfurY, int sulfurZ) {
		for (int offset = 1; offset <= ALLOWED_WATER_BLOCKS_ABOVE + 1; offset++) {
			int blockY = sulfurY + offset;
			if (!passableAt(user, sulfurX, blockY, sulfurZ)) {
				return -1;
			}
			if (!sourceWaterAt(user, sulfurX, blockY, sulfurZ)) {
				return offset - 1;
			}
		}
		return -1;
	}

	private static int unobstructedLaunchHeight(User user, int sulfurX, int sulfurY, int sulfurZ, int waterBlocks) {
		int maximumHeight = 6 * waterBlocks;
		for (int offset = 0; offset < maximumHeight; offset++) {
			if (!passableAt(user, sulfurX, sulfurY + 1 + offset, sulfurZ)) {
				return offset;
			}
		}
		return maximumHeight;
	}

	static BoundingBox launchArea(int sulfurX, int sulfurY, int sulfurZ, int launchHeight) {
		double minimumY = sulfurY + 1.0D;
		double maximumY = sulfurY + 2.0D;
		double expansion = launchHeight - 1.0D;
		if (expansion < 0.0D) {
			minimumY += expansion;
		} else if (expansion > 0.0D) {
			maximumY += expansion;
		}
		return BoundingBox.fromBounds(sulfurX, minimumY, sulfurZ, sulfurX + 1.0D, maximumY, sulfurZ + 1.0D);
	}

	private static boolean activeGeyserAt(User user, int blockX, int blockY, int blockZ) {
		Material material = VolatileBlockAccess.typeAccess(user, blockX, blockY, blockZ);
		if (material != POTENT_SULFUR) {
			return false;
		}
		BlockVariant variant = VolatileBlockAccess.variantAccess(user, user.player().getWorld(), blockX, blockY, blockZ);
		PotentSulfurState state = variant.enumProperty(PotentSulfurState.class, "potent_sulfur_state");
		return state != null && state.activeGeyser();
	}

	private static boolean sourceWaterAt(User user, int blockX, int blockY, int blockZ) {
		Fluid fluid = VolatileBlockAccess.fluidAccess(user, blockX, blockY, blockZ);
		return fluid.isOfWater() && fluid.isSource();
	}

	private static boolean passableAt(User user, int blockX, int blockY, int blockZ) {
		Material material = VolatileBlockAccess.typeAccess(user, blockX, blockY, blockZ);
		String materialName = material.name();
		if (material == Material.AIR || material == Material.WATER || "CAVE_AIR".equals(materialName) || "VOID_AIR".equals(materialName) || "POWDER_SNOW".equals(materialName) || "SCAFFOLDING".equals(materialName)) {
			return true;
		}
		return VolatileBlockAccess.collisionShapeAccess(user, new BlockPosition(blockX, blockY, blockZ)).isEmpty();
	}

	private static void capAccumulatedFallDistance(SimulationEnvironment environment, double motionY) {
		double fallDistance = environment.fallDistance();
		if (motionY > -0.5D && fallDistance > 1.0D) {
			environment.addFallDistance(1.0D - fallDistance);
		}
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}

	private static int ceil(double value) {
		return (int) Math.ceil(value);
	}

	enum PotentSulfurState {
		DRY, WET, DORMANT, ERUPTING, CONTINUOUS;

		boolean activeGeyser() {
			return this == ERUPTING || this == CONTINUOUS;
		}
	}
}
