package de.jpx3.intave.check.other.protocolscanner;

import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.HeldItemSlotReader;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.HELD_ITEM_SLOT_IN;

public final class SentSlotTwice extends PlayerCheckPart<ProtocolScanner> {
  private final int vl;
  private int lastSlot;
  private int slotPacketsSent;

  public SentSlotTwice(User user, ProtocolScanner parentCheck) {
    super(user, parentCheck);
    this.vl = parentCheck.configuration().settings().intBy("sst-vl", parentCheck.configuration().settings().intBy("check_sent_slot_twice_vl", 100));
  }

  @PacketSubscription(
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveSlotSwitch(HeldItemSlotReader reader) {
    if (vl == 0) {
      return;
    }

    Player player = owningUser().player();
    int slot = reader.slot();
    if (lastSlot == slot && slot > 0) {
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player).withMessage("sent slot twice").withDetails("slot " + slot)
        .withVL(slotPacketsSent > 4 ? vl : 0)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
    lastSlot = slot;
    slotPacketsSent++;
  }
}
