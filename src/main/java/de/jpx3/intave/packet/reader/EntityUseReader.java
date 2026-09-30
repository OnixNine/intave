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

package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedEnumEntityUseAction;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.share.RawVector3d;
import org.bukkit.util.Vector;

public final class EntityUseReader extends EntityReader {

  public boolean isAttackPacket() {
    return useAction() == EnumWrappers.EntityUseAction.ATTACK;
  }

  public boolean isSecondary() {
    return useAction() == EnumWrappers.EntityUseAction.INTERACT_AT;
  }

  public EnumWrappers.Hand hand() {
    if (MinecraftVersions.VER1_9_0.below()) {
      return EnumWrappers.Hand.MAIN_HAND;
    }
    if (MinecraftVersions.VER26_1_1.atOrAbove()) {
      return packet().getHands().readSafely(0);
    }
    WrappedEnumEntityUseAction wrappedAction = wrappedAction();
    return wrappedAction == null
      ? packet().getHands().readSafely(0)
      : wrappedAction.getHand();
  }

  public RawVector3d hitPosition() {
    Vector vector;
    if (MinecraftVersions.VER26_1_1.atOrAbove()) {
      vector = packet().getVectors().readSafely(0);
    } else {
      WrappedEnumEntityUseAction wrappedAction = wrappedAction();
      vector = wrappedAction == null
        ? packet().getVectors().readSafely(0)
        : wrappedAction.getPosition();
    }
    return vector == null ? null : RawVector3d.fromBukkit(vector);
  }

  private WrappedEnumEntityUseAction wrappedAction() {
    // Initializing ProtocolLib's wrapper fails on the flat packet layout before 1.17.
    if (MinecraftVersions.VER1_17_0.below()) {
      return null;
    }
    return packet().getEnumEntityUseActions().readSafely(0);
  }

  public EnumWrappers.EntityUseAction useAction() {
    if (MinecraftVersions.VER26_1_1.atOrAbove()) {
      if (PacketId.Client.ATTACK_ENTITY.lookupName().equalsIgnoreCase(
        packet().getType().name()
      )) {
        return EnumWrappers.EntityUseAction.ATTACK;
      }
      boolean isSecondary = packet().getBooleans().read(0);
      return isSecondary ? EnumWrappers.EntityUseAction.INTERACT_AT : EnumWrappers.EntityUseAction.INTERACT;
    } else {
      EnumWrappers.EntityUseAction action = packet().getEntityUseActions().readSafely(0);
      if (action == null) {
        action = packet().getEnumEntityUseActions().read(0).getAction();
      }
      return action;
    }
  }
}
