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

package de.jpx3.intave.block.physics;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

public final class BlockPhysics {
	private static final MinecraftVersion THIS_MINECRAFT_VERSION = MinecraftVersion.current();
	private static final Map<Material, BlockPhysic> materialLookup = new EnumMap<>(Material.class);

	public static void setup() {
		setup(THIS_MINECRAFT_VERSION);
	}

	public static void setup(
		MinecraftVersion version
	) {
		BounceSuppression.setup(version);
		materialLookup.clear();
		setup(BedPhysics.class, version);
		setup(SlimePhysics.class, version);
		setup(WebPhysics.class, version);
		setup(SoulSandPhysics.class, version);
		setup(BerryBushPhysics.class, version);
		setup(HoneyPhysics.class, version);
		setup(FluidPhysics.class, version);
		setup(BubbleColumnPhysics.class, version);
		setup(PowderSnowPhysics.class, version);
	}

	public static void setup(
		Class<? extends BlockPhysic> blockPhysicClass,
		MinecraftVersion version
	) {
		try {
			BlockPhysic blockPhysic = blockPhysicClass.newInstance();
			blockPhysic.setupFor(version);
			if (blockPhysic.supportedOnServerVersion()) {
				for (Material material : blockPhysic.applicableMaterials()) {
					if (material == null) {
						throw new IllegalStateException("BlockPhysic " + blockPhysicClass.getName() + " returned null material");
					}
					materialLookup.put(material, blockPhysic);
				}
			}
		} catch (InstantiationException | IllegalAccessException exception) {
			exception.printStackTrace();
		}
	}

	@Nullable
	public static Motion entityInside(
		User user, Material material,
		SimulationEnvironment environment,
		BlockPosition blockPosition,
		Position from, Motion motionInput,
		boolean insideBlockOrTooFast
	) {
		BlockPhysic collision = physicLookup(material);
		if (collision != null) {
			return collision.entityInside(
				user, environment, blockPosition, from,
				motionInput, insideBlockOrTooFast
			);
		}
		return null;
	}

	public static BlockShape entityInsideCollisionShape(
		User user,
		Material material,
		SimulationEnvironment environment,
		BlockPosition position
	) {
		BlockPhysic physic = physicLookup(material);
		return physic == null
			? BlockShapes.cubeAt(position.getBlockX(), position.getBlockY(), position.getBlockZ())
			: physic.entityInsideCollisionShape(user, environment, position);
	}

	@Nullable
	public static Motion stepOn(
		User user,
		Material material,
		SimulationEnvironment environment,
		double motionX, double motionY, double motionZ
	) {
		BlockPhysic collision = physicLookup(material);
		return collision != null ? collision.stepOn(user, environment, motionX, motionY, motionZ) : null;
	}

	@Nullable
	public static Motion blockLanded(
		User user, SimulationEnvironment environment,
		Material material,
		double motionX, double motionY, double motionZ
	) {
		BlockPhysic collision = physicLookup(material);
		return collision != null ? collision.landed(user, environment, motionX, motionY, motionZ) : null;
	}

	public static void fallenUpon(User user, Material material) {
		BlockPhysic collision = physicLookup(material);
		if (collision != null) {
			collision.fallenUpon(user);
		}
	}

	private static BlockPhysic physicLookup(Material material) {
		return materialLookup.get(material);
	}
}
