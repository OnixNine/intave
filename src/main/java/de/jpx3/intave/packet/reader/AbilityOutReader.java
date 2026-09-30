package de.jpx3.intave.packet.reader;

public class AbilityOutReader extends AbstractPacketReader {
  public float flyingSpeed() {
    return packet().getFloat().read(0);
  }

  public float walkingSpeed() {
    return packet().getFloat().read(1);
  }

  public boolean flying() {
    return packet().getBooleans().read(1);
  }

  public boolean flyingAllowed() {
    return packet().getBooleans().read(2);
  }
}
