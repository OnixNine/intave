package de.jpx3.intave.world;

import com.google.common.collect.MapMaker;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.World;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("PointlessArithmeticExpression")
public final class WorldHeight {
  private static final boolean MINECRAFT_18 = MinecraftVersions.VER1_18_0.atOrAbove();
  private static final MethodHandle MINIMUM_HEIGHT_ACCESSOR = minimumHeightAccessor();
  private static final Map<World, Bounds> WORLD_BOUNDS =
    new MapMaker().weakKeys().makeMap();
  public static final int UPPER_WORLD_LIMIT = MINECRAFT_18 ? 256 + 64 : 256;
  public static final int LOWER_WORLD_LIMIT = MINECRAFT_18 ?   0 - 64 : 0;

  public static int minimum(World world) {
    return bounds(world).minimum;
  }

  public static int maximumExclusive(World world) {
    return bounds(world).maximumExclusive;
  }

  public static boolean contains(World world, int blockY) {
    Bounds bounds = bounds(world);
    return blockY >= bounds.minimum && blockY < bounds.maximumExclusive;
  }

  private static Bounds bounds(World world) {
    Objects.requireNonNull(world, "world");
    Bounds cached = WORLD_BOUNDS.get(world);
    if (cached != null) {
      return cached;
    }
    Bounds resolved = new Bounds(resolveMinimum(world), world.getMaxHeight());
    Bounds raced = WORLD_BOUNDS.putIfAbsent(world, resolved);
    return raced == null ? resolved : raced;
  }

  private static int resolveMinimum(World world) {
    if (MINIMUM_HEIGHT_ACCESSOR == null) {
      return 0;
    }
    try {
      return (int) MINIMUM_HEIGHT_ACCESSOR.invokeExact(world);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Unable to read the world's minimum height", throwable);
    }
  }

  private static MethodHandle minimumHeightAccessor() {
    try {
      return MethodHandles.publicLookup().findVirtual(
        World.class, "getMinHeight", MethodType.methodType(int.class)
      );
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      return null;
    }
  }

  private static final class Bounds {
    private final int minimum;
    private final int maximumExclusive;

    private Bounds(int minimum, int maximumExclusive) {
      this.minimum = minimum;
      this.maximumExclusive = maximumExclusive;
    }
  }
}
