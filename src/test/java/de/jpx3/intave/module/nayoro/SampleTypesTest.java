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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.share.Block;
import ac.intave.samples.share.BlockUpdate;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Position;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

final class SampleTypesTest {
  @Test
  void blockIncludesNamePropertiesAndNormalizedCollisionShape() throws Exception {
    BlockState state = blockState("north");

    Block converted = SampleTypes.block(state, 10, 64, 20);

    assertEquals("OAK_STAIRS", converted.name());
    assertEquals("north", converted.properties().get("facing"));
    assertEquals("false", converted.properties().get("waterlogged"));
    assertEquals(
      new ac.intave.samples.share.BoundingBox(0.25, 0.0, 0.5, 0.75, 1.0, 1.0),
      converted.boundingBoxes().getFirst()
    );
  }

  @Test
  void dirtyBlocksUseReducedRadiusAndSendAirTombstones() {
    int blockX = 7;
    int blockY = 61;
    int blockZ = 17;
    AtomicReference<BlockState> state = new AtomicReference<>(BlockState.stone());
    BlockCache blockCache = mutableBlockCacheAt(blockX, blockY, blockZ, state);
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    BoundingBox playerBox = BoundingBox.fromBounds(
      10.0, 64.0, 20.0,
      10.6, 65.8, 20.6
    );

    List<BlockUpdate> initial = dirtyNearbyBlocks(tracker, blockCache, playerBox);
    List<BlockUpdate> unchanged = dirtyNearbyBlocks(tracker, blockCache, playerBox);
    state.set(BlockState.empty());
    List<BlockUpdate> removed = dirtyNearbyBlocks(tracker, blockCache, playerBox);

    assertEquals(1, initial.size());
    assertEquals("STONE", initial.getFirst().block().name());
    assertEquals(blockX, initial.getFirst().position().x());
    assertEquals(blockY, initial.getFirst().position().y());
    assertEquals(blockZ, initial.getFirst().position().z());
    assertTrue(unchanged.isEmpty());
    assertEquals(1, removed.size());
    assertSame(Block.AIR, removed.getFirst().block());
    assertEquals(0, tracker.recordedBlockCount());
  }

  @Test
  void stationarySamplingOnlyScansNormalSurroundings() {
    AtomicInteger accesses = new AtomicInteger();
    BlockCache blockCache = (BlockCache) Proxy.newProxyInstance(
      BlockCache.class.getClassLoader(),
      new Class<?>[]{BlockCache.class},
      (proxy, method, arguments) -> {
        if (method.getName().equals("stateAt")) {
          accesses.incrementAndGet();
          return BlockState.empty();
        }
        throw new UnsupportedOperationException(method.getName());
      }
    );
    BoundingBox playerBox = BoundingBox.fromBounds(
      10.0, 64.0, 20.0,
      10.6, 65.8, 20.6
    );

    NearbyBlockTracker tracker = new NearbyBlockTracker();
    dirtyNearbyBlocks(tracker, blockCache, playerBox);
    accesses.set(0);
    List<BlockUpdate> firstUnchanged = dirtyNearbyBlocks(tracker, blockCache, playerBox);

    assertEquals(392, accesses.get());
    assertSame(firstUnchanged, dirtyNearbyBlocks(tracker, blockCache, playerBox),
      "unchanged scans should reuse the empty result");
    assertEquals(2 * 392, accesses.get());
  }

  @Test
  void recordedBlocksStayStrictlyBoundedDuringLongDistanceTravel() {
    BlockState glass = new BlockState(
      BlockShapes.originCube(), BlockShapes.originCube(), Material.GLASS, 0
    );
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    AtomicInteger peakRecordedBlocks = new AtomicInteger();
    Map<BlockPosition, BlockState> overrides = new HashMap<>();
    BlockCache blockCache = blockCache(position -> {
      // Measure during scans as well as between ticks to catch temporary overshoots.
      peakRecordedBlocks.accumulateAndGet(tracker.recordedBlockCount(), Math::max);
      return overrides.getOrDefault(position, glass);
    });
    Position playerPosition = null;
    for (int step = 0; step < 100; step++) {
      playerPosition = new Position(step * 32.0D + 0.5D, 64.0D, 0.5D);
      assertFalse(sampleAt(tracker, blockCache, playerPosition).isEmpty());
      assertTrue(tracker.recordedBlockCount() <= 10_000);
      assertTrue(sampleAt(tracker, blockCache, playerPosition).isEmpty(),
        "eviction must keep the newest area available for unchanged scans");
    }
    assertEquals(10_000, tracker.recordedBlockCount());
    assertEquals(10_000, peakRecordedBlocks.get());

    BlockPosition changed = new BlockPosition(99 * 32, 64, 0);
    overrides.put(changed, BlockState.stone());
    assertEquals(1, sampleAt(tracker, blockCache, playerPosition).size());
    assertEquals(10_000, tracker.recordedBlockCount(), "replacing an entry must not evict another");
    overrides.put(changed, BlockState.empty());
    List<BlockUpdate> removed = sampleAt(tracker, blockCache, playerPosition);
    assertEquals(1, removed.size());
    assertSame(Block.AIR, removed.getFirst().block());
    assertEquals(9_999, tracker.recordedBlockCount());

    assertFalse(sampleAt(tracker, blockCache, new Position(0.5D, 64.0D, 0.5D)).isEmpty(),
      "evicted positions must be recorded again when revisited");
    assertEquals(10_000, tracker.recordedBlockCount());
  }

  @Test
  void propertyOnlyChangesAreDirty() throws Exception {
    int blockX = 10;
    int blockY = 64;
    int blockZ = 20;
    AtomicReference<BlockState> state = new AtomicReference<>(blockState("north"));
    BlockCache blockCache = mutableBlockCacheAt(blockX, blockY, blockZ, state);
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    BoundingBox playerBox = BoundingBox.fromBounds(
      10.0, 64.0, 20.0,
      10.6, 65.8, 20.6
    );

    dirtyNearbyBlocks(tracker, blockCache, playerBox);
    state.set(blockState("south"));
    List<BlockUpdate> updates = dirtyNearbyBlocks(tracker, blockCache, playerBox);

    assertEquals(1, updates.size());
    assertEquals("south", updates.getFirst().block().properties().get("facing"));
  }

  @Test
  void emptyScansDoNotRetainAirBlocks() {
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    BlockCache blockCache = mutableBlockCacheAt(
      Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
      new AtomicReference<>(BlockState.empty())
    );
    BoundingBox playerBox = BoundingBox.fromBounds(
      10.0, 64.0, 20.0,
      10.6, 65.8, 20.6
    );

    List<BlockUpdate> updates = dirtyNearbyBlocks(tracker, blockCache, playerBox);

    assertTrue(updates.isEmpty());
    assertEquals(0, tracker.recordedBlockCount());
  }

  @Test
  void lookAheadExtendsOnlyTheViewedFacesIncludingUpAndDown() {
    Vector[] directions = {
      new Vector(1, 0, 0), new Vector(-1, 0, 0),
      new Vector(0, 1, 0), new Vector(0, -1, 0),
      new Vector(0, 0, 1), new Vector(0, 0, -1)
    };
    BlockPosition[] targets = {
      new BlockPosition(5, 64, 0), new BlockPosition(-5, 64, 0),
      new BlockPosition(0, 70, 0), new BlockPosition(0, 59, 0),
      new BlockPosition(0, 64, 5), new BlockPosition(0, 64, -5)
    };
    BlockPosition nearby = new BlockPosition(0, 64, 0);
    for (int i = 0; i < directions.length; i++) {
      Set<BlockPosition> blocks = Set.of(nearby, targets[i], targets[i ^ 1]);
      BlockCache cache = blockCache(position ->
        blocks.contains(position) ? BlockState.stone() : BlockState.empty()
      );
      Vector look = directions[i].clone();
      List<BlockUpdate> updates = sampleAt(
        new NearbyBlockTracker(), cache, new Position(0.5D, 64.0D, 0.5D), look, 2.0D
      );

      assertEquals(2, updates.size());
      assertTrue(containsUpdateAt(updates, nearby));
      assertTrue(containsUpdateAt(updates, targets[i]));
      assertFalse(containsUpdateAt(updates, targets[i ^ 1]));
      assertEquals(directions[i], look, "the cached look vector must not be mutated");
    }
  }

  @Test
  void diagonalLookAheadPreservesTheNormalSurroundings() {
    Set<BlockPosition> scanned = scanAt(
      new NearbyBlockTracker(), new Vector(1, 1, -1).normalize(), 2.0D
    );

    assertTrue(scanned.contains(new BlockPosition(4, 69, -4)));
    assertTrue(scanned.contains(new BlockPosition(-3, 61, 3)));
    assertFalse(scanned.contains(new BlockPosition(-4, 60, 4)));
  }

  @Test
  void recentMovementAccumulatesAndDecaysBackToNormalSurroundings() {
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    Vector look = new Vector(0, 0, 1);
    Set<BlockPosition> initial = scanAt(tracker, look, 0.5D);
    assertTrue(initial.contains(new BlockPosition(0, 64, 4)));
    assertFalse(initial.contains(new BlockPosition(0, 64, 6)));

    Set<BlockPosition> moving = initial;
    for (int tick = 0; tick < 20; tick++) {
      moving = scanAt(tracker, look, 0.5D);
    }
    assertTrue(moving.contains(new BlockPosition(0, 64, 6)));
    Set<BlockPosition> decayed = scanAt(tracker, look, 0.0D);
    assertTrue(decayed.size() > 392, "stopping must not immediately discard recent movement");

    for (int tick = 0; tick < 40; tick++) {
      Set<BlockPosition> next = scanAt(tracker, look, 0.0D);
      assertTrue(decayed.containsAll(next), "extra reach must keep shrinking while stationary");
      decayed = next;
    }
    assertEquals(392, decayed.size());
    assertEquals(scanAt(new NearbyBlockTracker(), look, 0.0D), decayed);
  }

  @Test
  void fasterMovementExtendsTheSearchFurther() {
    Vector look = new Vector(0, 0, 1);
    Set<BlockPosition> slow = scanAt(new NearbyBlockTracker(), look, 0.5D);
    Set<BlockPosition> fast = scanAt(new NearbyBlockTracker(), look, 2.0D);

    assertTrue(fast.containsAll(slow));
    assertTrue(fast.size() > slow.size());
  }

  @Test
  void decayingReachFollowsTheCurrentLookDirection() {
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    scanAt(tracker, new Vector(0, 0, 1), 2.0D);

    Set<BlockPosition> turned = scanAt(tracker, new Vector(-1, 0, 0), 0.0D);

    assertTrue(turned.contains(new BlockPosition(-5, 64, 0)));
    assertFalse(turned.contains(new BlockPosition(0, 64, 5)));
  }

  @Test
  void largeMovementCannotCreateAnUnboundedScan() {
    Vector look = new Vector(1, 1, 1).normalize();
    Set<BlockPosition> capped = scanAt(new NearbyBlockTracker(), look, 4.0D);
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    for (int tick = 0; tick < 5; tick++) {
      assertEquals(capped, scanAt(tracker, look, 30_000_000.0D));
    }
    assertTrue(capped.size() < 2_000);
  }

  @Test
  void invalidMovementOrLookDirectionKeepsTheNormalScan() {
    for (double distance : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1.0D}) {
      assertEquals(392, scanAt(new NearbyBlockTracker(), new Vector(0, 0, 1), distance).size());
    }
    assertEquals(392, scanAt(new NearbyBlockTracker(), new Vector(Double.NaN, 0, 1), 4.0D).size());
  }

  @Test
  void expandedSurroundingsReportEnclosedBlocksAndAirTombstones() {
    NearbyBlockTracker tracker = new NearbyBlockTracker();
    Position position = new Position(0.5D, 64.0D, 0.5D);
    Vector look = new Vector(0, 0, 1);
    BlockPosition removed = new BlockPosition(0, 64, 5);
    AtomicReference<BlockState> state = new AtomicReference<>(BlockState.stone());
    BlockCache cache = blockCache(block -> block.equals(removed) ? state.get() : BlockState.stone());

    List<BlockUpdate> initial = sampleAt(tracker, cache, position, look, 2.0D);
    assertEquals(504, initial.size(), "the entire extended box must be sampled without occlusion filtering");
    state.set(BlockState.empty());
    List<BlockUpdate> updates = sampleAt(tracker, cache, position, look, 0.0D);
    assertEquals(1, updates.size());
    assertTrue(containsUpdateAt(updates, removed));
    assertSame(Block.AIR, updates.getFirst().block());
  }

  private static Set<BlockPosition> scanAt(
    NearbyBlockTracker tracker, Vector lookDirection, double movementDistance
  ) {
    Set<BlockPosition> scanned = new HashSet<>();
    BlockCache cache = blockCache(position -> {
      scanned.add(position);
      return BlockState.empty();
    });
    sampleAt(tracker, cache, new Position(0.5D, 64.0D, 0.5D), lookDirection, movementDistance);
    return scanned;
  }

  private static List<BlockUpdate> sampleAt(
    NearbyBlockTracker tracker, BlockCache blockCache, Position position
  ) {
    return sampleAt(tracker, blockCache, position, new Vector(0, 0, 1), 0.0D);
  }

  private static List<BlockUpdate> sampleAt(
    NearbyBlockTracker tracker, BlockCache blockCache, Position position,
    Vector lookDirection, double movementDistance
  ) {
    return tracker.dirtyNearbyBlocks(
      blockCache,
      BoundingBox.fromBounds(
        position.getX() - 0.3D, position.getY(), position.getZ() - 0.3D,
        position.getX() + 0.3D, position.getY() + 1.8D, position.getZ() + 0.3D
      ),
      lookDirection, movementDistance
    );
  }

  private static List<BlockUpdate> dirtyNearbyBlocks(
    NearbyBlockTracker tracker, BlockCache blockCache, BoundingBox playerBox
  ) {
    return tracker.dirtyNearbyBlocks(blockCache, playerBox, new Vector(0, 0, 1), 0.0D);
  }


  private static boolean containsUpdateAt(List<BlockUpdate> updates, BlockPosition position) {
    return updates.stream().anyMatch(update ->
      update.position().x() == position.getX()
        && update.position().y() == position.getY()
        && update.position().z() == position.getZ()
    );
  }

  private static BlockCache blockCache(Function<BlockPosition, BlockState> stateAt) {
    return (BlockCache) Proxy.newProxyInstance(
      BlockCache.class.getClassLoader(),
      new Class<?>[]{BlockCache.class},
      (proxy, method, arguments) -> {
        if (method.getName().equals("stateAt")) {
          return stateAt.apply(new BlockPosition(
            (Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2]
          ));
        }
        throw new UnsupportedOperationException(method.getName());
      }
    );
  }

  private static BlockState blockState(String facing) throws Exception {
    BlockState state = new BlockState(
      BlockShapes.emptyShape(),
      BoundingBox.fromBounds(10.25, 64.0, 20.5, 10.75, 65.0, 21.0),
      Material.OAK_STAIRS,
      7
    );
    Map<String, Comparable<?>> properties = new HashMap<>();
    properties.put("waterlogged", false);
    properties.put("facing", facing);
    setResolvedProperties(state, properties);
    return state;
  }

  private static BlockCache mutableBlockCacheAt(
    int blockX, int blockY, int blockZ, AtomicReference<BlockState> state
  ) {
    return blockCache(position ->
      position.getX() == blockX && position.getY() == blockY && position.getZ() == blockZ
        ? state.get() : BlockState.empty()
    );
  }

  private static void setResolvedProperties(
    BlockState state, Map<String, Comparable<?>> properties
  ) throws Exception {
    Field field = BlockState.class.getDeclaredField("properties");
    field.setAccessible(true);
    field.set(state, properties);
  }
}
