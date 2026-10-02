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

package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ThinBlockPatchTest {
  private static final int NORTH = 1;
  private static final int EAST = 1 << 1;
  private static final int SOUTH = 1 << 2;
  private static final int WEST = 1 << 3;
  private static final int ALL_DIRECTIONS = NORTH | EAST | SOUTH | WEST;

  private static final BoundingBox LEGACY_EAST_WEST = BoundingBox.originFromX16(0, 0, 7, 16, 16, 9);
  private static final BoundingBox LEGACY_NORTH_SOUTH = BoundingBox.originFromX16(7, 0, 0, 9, 16, 16);
  private static final BoundingBox LEGACY_NORTH = BoundingBox.originFromX16(7, 0, 0, 9, 16, 8);
  private static final BoundingBox LEGACY_EAST = BoundingBox.originFromX16(8, 0, 7, 16, 16, 9);
  private static final BoundingBox LEGACY_SOUTH = BoundingBox.originFromX16(7, 0, 8, 9, 16, 16);
  private static final BoundingBox LEGACY_WEST = BoundingBox.originFromX16(0, 0, 7, 8, 16, 9);

  private static final BoundingBox MODERN_BASE = BoundingBox.originFromX16(7, 0, 7, 9, 16, 9);
  private static final BoundingBox MODERN_NORTH = BoundingBox.originFromX16(7, 0, 0, 9, 16, 9);
  private static final BoundingBox MODERN_EAST = BoundingBox.originFromX16(7, 0, 7, 16, 16, 9);
  private static final BoundingBox MODERN_SOUTH = BoundingBox.originFromX16(7, 0, 7, 9, 16, 16);
  private static final BoundingBox MODERN_WEST = BoundingBox.originFromX16(0, 0, 7, 9, 16, 9);

  @Test
  void decodesEveryOptimizedModernConnectionMask() {
    for (int mask = 0; mask <= ALL_DIRECTIONS; mask++) {
      assertEquals(mask, ThinBlockPatch.connectionMask(optimized(modernBoxes(mask))), "mask " + mask);
    }
  }

  @Test
  void translatesEveryOptimizedModernMaskToLegacyGeometry() {
    for (int mask = 0; mask <= ALL_DIRECTIONS; mask++) {
      List<BoundingBox> translated = ThinBlockPatch.modernToLegacy(optimized(modernBoxes(mask)));
      int expected = mask == 0 ? ALL_DIRECTIONS : mask;
      assertEquals(expected, ThinBlockPatch.connectionMask(optimized(translated)), "mask " + mask);
    }
  }

  @Test
  void translatesEveryOptimizedLegacyMaskToModernGeometry() {
    for (int mask = 0; mask <= ALL_DIRECTIONS; mask++) {
      boolean isolated = mask == 0;
      List<BoundingBox> translated = ThinBlockPatch.legacyToModern(
        optimized(legacyBoxes(mask)), isolated, false
      );
      assertEquals(mask, ThinBlockPatch.connectionMask(optimized(translated)), "mask " + mask);
    }
  }

  @Test
  void keepsViaVersionCrossForIsolatedLegacyPaneOnModernClients() {
    List<BoundingBox> translated = ThinBlockPatch.legacyToModern(
      optimized(legacyBoxes(0)), true, true
    );

    assertEquals(ALL_DIRECTIONS, ThinBlockPatch.connectionMask(optimized(translated)));
  }

  private static List<BoundingBox> optimized(List<BoundingBox> boxes) {
    return BlockShapes.optimizedMerge(boxes).elementaryBoxes();
  }

  private static List<BoundingBox> modernBoxes(int connections) {
    List<BoundingBox> boxes = new ArrayList<>();
    boxes.add(MODERN_BASE);
    if ((connections & NORTH) != 0) boxes.add(MODERN_NORTH);
    if ((connections & EAST) != 0) boxes.add(MODERN_EAST);
    if ((connections & SOUTH) != 0) boxes.add(MODERN_SOUTH);
    if ((connections & WEST) != 0) boxes.add(MODERN_WEST);
    return boxes;
  }

  private static List<BoundingBox> legacyBoxes(int connections) {
    List<BoundingBox> boxes = new ArrayList<>();
    boolean anyConnection = connections != 0;
    boolean east = (connections & EAST) != 0;
    boolean west = (connections & WEST) != 0;
    if (east && west || !anyConnection) {
      boxes.add(LEGACY_EAST_WEST);
    } else if (east) {
      boxes.add(LEGACY_EAST);
    } else if (west) {
      boxes.add(LEGACY_WEST);
    }

    boolean north = (connections & NORTH) != 0;
    boolean south = (connections & SOUTH) != 0;
    if (north && south || !anyConnection) {
      boxes.add(LEGACY_NORTH_SOUTH);
    } else if (north) {
      boxes.add(LEGACY_NORTH);
    } else if (south) {
      boxes.add(LEGACY_SOUTH);
    }
    return boxes;
  }
}
