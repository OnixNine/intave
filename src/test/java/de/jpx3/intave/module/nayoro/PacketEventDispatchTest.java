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

import ac.intave.samples.event.Event;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PacketEventDispatchTest {
  @Test
  void tickOffsetsBelongToEachEmission() {
    List<Event> events = new ArrayList<>();
    PacketEventDispatch dispatch = new PacketEventDispatch((user, event) -> events.add(event));
    dispatch.receiveClientTickEnd(null);
    events.get(0).withOffset(12);
    dispatch.receiveClientTickEnd(null);
    events.get(1).withOffset(37);

    assertNotSame(events.get(0), events.get(1));
    assertEquals(12, events.get(0).offset());
    assertEquals(37, events.get(1).offset());
  }

  @Test
  void unsupportedAttackStrengthIsUnknownInsteadOfNegative() throws Exception {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_8_0);
    assertNull(attackStrength(0.5F));
  }

  @Test
  void availableAttackStrengthIsClampedAndRejectsNonfiniteValues() throws Exception {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    boolean available;
    try { Player.class.getMethod("getAttackCooldown"); available = true; }
    catch (NoSuchMethodException exception) { available = false; }
    if (!available) {
      assertNull(attackStrength(0.5F));
      return;
    }
    assertEquals(0.5F, attackStrength(0.5F));
    assertEquals(0.0F, attackStrength(-0.5F));
    assertEquals(1.0F, attackStrength(1.5F));
    assertNull(attackStrength(Float.NaN));
    assertNull(attackStrength(Float.POSITIVE_INFINITY));
  }

  private static Float attackStrength(float strength) throws Exception {
    Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
      (proxy, method, args) -> {
        if (method.getName().equals("getAttackCooldown")) return strength;
        throw new AssertionError("Unexpected player call: " + method.getName());
      });
    Method method = PacketEventDispatch.class.getDeclaredMethod("attackStrength", Player.class);
    method.setAccessible(true);
    return (Float) method.invoke(null, player);
  }
}
