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

package de.jpx3.intave.check.movement.physics.branch;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;

import java.util.Collection;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.ENTITY_USE;

final class UseItemBrancher extends MovementSearchBrancher {
  private static final boolean[] OPTIMISTIC = new boolean[]{true, false};
  private static final boolean[] PESSIMISTIC = new boolean[]{false, true};

  @Override
  public void branch(MovementSearchInput input, MovementSearchBranch inputBranch, Collection<MovementSearchBranch> outputBranches) {
    InventoryMetadata inventoryData = input.user().meta().inventory();
    ProtocolMetadata protocol = input.user().meta().protocol();
    UseItemRequirement requirement = useItemRequirement(input, inputBranch);
    if (input.user().receives(MessageChannel.DEBUG_ITEM_USE) && input.shouldUpdateItemUseDebug()) {
      inventoryData.itemUsePermissionDebug(requirement.name());
    }
    int numOutputBranches = 0;
    for (boolean useItemState : inventoryData.handActive() ? OPTIMISTIC : PESSIMISTIC) {
      if (!requirement.allows(useItemState)) {
        continue;
      }
      // players on 1.8.X cannot sprint while using an item
      if (inputBranch.moveConfig().isSprinting() && useItemState && !protocol.combatUpdate()) {
        continue;
      }
      outputBranches.add(inputBranch.withHandActive(useItemState));
      numOutputBranches++;
    }
    if (numOutputBranches == 0) {
      outputBranches.add(inputBranch.withHandActive(false));
    }
  }

  private UseItemRequirement useItemRequirement(MovementSearchInput input, MovementSearchBranch inputBranch) {
    InventoryMetadata inventoryData = input.user().meta().inventory();
    SimulationEnvironment movementData = input.environment();
    ProtocolMetadata protocol = input.user().meta().protocol();
    boolean hasUsableItem = input.usableItemInEitherHandOrHotbar();
    if (!hasUsableItem) {
      return UseItemRequirement.SKIP;
    }
    boolean skipUseItem = !protocol.sprintWhenHandActive() && inputBranch.isSprinting() && !protocol.viaVersionShieldBlockReplacement();
    boolean requireUseItem = !protocol.combatUpdate()
      && inventoryData.handActive()
      && inventoryData.pastHotBarSlotChange > 20
      && (inventoryData.heldItem() == null || inventoryData.heldItem().getType() != Material.BOW);

    if (requireUseItem && movementData.ticksPast(ENTITY_USE) <= inventoryData.handActiveTicks) {
      requireUseItem = false;
    }

    if (requireUseItem || input.user().sizeOf(movementData.pose()).height() <= 1) {
      skipUseItem = false;
    }

    if ((requireUseItem || skipUseItem) && input.user().hasPlayer() && input.couldChargeCrossbow()) {
      requireUseItem = false;
      skipUseItem = false;
    }

    if (!input.detectNoSlowdown()) {
      skipUseItem = false;
      requireUseItem = false;
    }
    return UseItemRequirement.from(skipUseItem, requireUseItem);
  }

  private enum UseItemRequirement {
    ANY(true, true),
    SKIP(true, false),
    REQUIRE(false, true),
    NONE(false, false);

    private final boolean allowInactive;
    private final boolean allowActive;

    UseItemRequirement(boolean allowInactive, boolean allowActive) {
      this.allowInactive = allowInactive;
      this.allowActive = allowActive;
    }

    boolean allows(boolean useItemState) {
      return useItemState ? allowActive : allowInactive;
    }

    private static UseItemRequirement from(boolean skip, boolean require) {
      if (skip && require) {
        return NONE;
      }
      if (require) {
        return REQUIRE;
      }
      if (skip) {
        return SKIP;
      }
      return ANY;
    }
  }
}
