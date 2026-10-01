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

package de.jpx3.intave.check.other;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.other.protocolscanner.InvalidPitch;
import de.jpx3.intave.check.other.protocolscanner.InvalidRelease;
import de.jpx3.intave.check.other.protocolscanner.MoveTickLimit;
import de.jpx3.intave.check.other.protocolscanner.SentSlotTwice;
import de.jpx3.intave.check.other.protocolscanner.SkinBlinker;

public final class ProtocolScanner extends Check {
  private final IntavePlugin plugin;

  public ProtocolScanner(IntavePlugin plugin) {
    super("ProtocolScanner", "protocolscanner");
    this.plugin = plugin;

    appendCheckParts(
      new InvalidPitch(this),
      new SkinBlinker(this),
      new InvalidRelease(this)
//      ,
//      new PacketConstraint(this)
    );
    appendPlayerCheckPart(SentSlotTwice.class);
    appendPlayerCheckPart(MoveTickLimit.class);
  }
}
