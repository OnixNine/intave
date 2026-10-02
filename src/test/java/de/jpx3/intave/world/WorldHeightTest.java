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

package de.jpx3.intave.world;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldHeightTest {

  @BeforeEach
  void setupVersion() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
  }

  @Test
  void readsBoundsFromEachWorld() {
    World vanillaWorld = worldWithBounds(-64, 320);
    World extendedWorld = worldWithBounds(-3000, 3001);

    assertEquals(-64, WorldHeight.minimum(vanillaWorld));
    assertEquals(320, WorldHeight.maximumExclusive(vanillaWorld));
    assertEquals(-3000, WorldHeight.minimum(extendedWorld));
    assertEquals(3001, WorldHeight.maximumExclusive(extendedWorld));
  }

  @Test
  void containsUsesInclusiveMinimumAndExclusiveMaximum() {
    World world = worldWithBounds(-3000, 3001);

    assertFalse(WorldHeight.contains(world, -3001));
    assertTrue(WorldHeight.contains(world, -3000));
    assertTrue(WorldHeight.contains(world, 3000));
    assertFalse(WorldHeight.contains(world, 3001));
  }

  @Test
  void cachesBoundsPerWorld() {
    AtomicInteger minimumReads = new AtomicInteger();
    AtomicInteger maximumReads = new AtomicInteger();
    World world = worldWithBounds(-3000, 3001, minimumReads, maximumReads);

    assertEquals(-3000, WorldHeight.minimum(world));
    assertEquals(3001, WorldHeight.maximumExclusive(world));
    assertTrue(WorldHeight.contains(world, 0));
    assertEquals(-3000, WorldHeight.minimum(world));
    assertEquals(3001, WorldHeight.maximumExclusive(world));

    assertEquals(1, minimumReads.get());
    assertEquals(1, maximumReads.get());
  }

  private static World worldWithBounds(int minimum, int maximumExclusive) {
    return worldWithBounds(
      minimum, maximumExclusive, new AtomicInteger(), new AtomicInteger()
    );
  }

  private static World worldWithBounds(
    int minimum,
    int maximumExclusive,
    AtomicInteger minimumReads,
    AtomicInteger maximumReads
  ) {
    return (World) Proxy.newProxyInstance(
      World.class.getClassLoader(),
      new Class<?>[]{World.class},
      (proxy, method, arguments) -> {
        switch (method.getName()) {
          case "getMinHeight":
            minimumReads.incrementAndGet();
            return minimum;
          case "getMaxHeight":
            maximumReads.incrementAndGet();
            return maximumExclusive;
          case "toString":
            return "World[" + minimum + ", " + maximumExclusive + ")";
          default:
            throw new UnsupportedOperationException(method.toString());
        }
      }
    );
  }
}
