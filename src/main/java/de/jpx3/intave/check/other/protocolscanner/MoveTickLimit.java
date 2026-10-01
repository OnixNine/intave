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

package de.jpx3.intave.check.other.protocolscanner;

import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.event.Cancellable;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CLIENT_TICK_END;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.FLYING;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class MoveTickLimit extends PlayerCheckPart<ProtocolScanner> {
  private final int packetOrderViolationLevel;
  private final MovementPacketWindow packetWindow = new MovementPacketWindow();

  public MoveTickLimit(User user, ProtocolScanner parentCheck) {
    super(user, parentCheck);
    this.packetOrderViolationLevel = parentCheck.configuration().settings().intBy("packet-order-vl", 5);
  }

  @PacketSubscription(
    // MovementDispatcher identifies teleport and item-use movement at LOW priority.
    priority = ListenerPriority.NORMAL,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK}
  )
  public void receiveMovement(Cancellable cancellable) {
    User user = owningUser();
    if (!usesClientTickEnd(user)) {
      return;
    }

    if (!shouldCountAsNormalMovement(user.meta().movement())) {
      return;
    }

    int movementPacketCount = packetWindow.recordMovement();
    if (movementPacketCount == 1 || packetOrderViolationLevel == 0) {
      return;
    }

    cancellable.setCancelled(true);
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withMessage("sent multiple movement packets")
      .withDetails(movementPacketCount + " packets without client tick end")
      .withVL(packetOrderViolationLevel)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = CLIENT_TICK_END
  )
  public void receiveClientTickEnd() {
    if (usesClientTickEnd(owningUser())) {
      packetWindow.startNextTick();
    }
  }

  private boolean usesClientTickEnd(User user) {
    return user.meta().protocol().sendsClientTickEnd();
  }

  private static boolean shouldCountAsNormalMovement(MovementMetadata movement) {
    return shouldCountAsNormalMovement(
      movement.isTeleportConfirmationPacket,
      movement.awaitClickMovementSkip,
      movement.dropPostTickMotionProcessing
    );
  }

  static boolean shouldCountAsNormalMovement(
    boolean teleportConfirmation,
    boolean itemUseMovementExpected,
    boolean postTickProcessingSuppressed
  ) {
    boolean itemUseMovement = itemUseMovementExpected && postTickProcessingSuppressed;
    return !teleportConfirmation && !itemUseMovement;
  }

  static final class MovementPacketWindow {
    private int movementPacketCount;

    int recordMovement() {
      return ++movementPacketCount;
    }

    void startNextTick() {
      movementPacketCount = 0;
    }
  }
}
