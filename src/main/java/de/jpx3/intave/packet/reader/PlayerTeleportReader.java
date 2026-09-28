package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.packet.converter.PosMoveRotConverter;
import de.jpx3.intave.share.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.OptionalInt;
import java.util.Set;
import java.util.EnumSet;

public final class PlayerTeleportReader extends AbstractPacketReader {
  private final static boolean DIRECT_PMR_USED = MinecraftVersions.VER1_21_3.atOrAbove();
  private PositionMoveRotation positionMoveRotation;
  private Motion companionMotion;
  private Boolean additiveCompanionMotion;
  private boolean mod;

  public OptionalInt teleportId() {
    StructureModifier<Integer> integers = packet().getIntegers();
    if (integers.size() < 1) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(integers.read(0));
  }

  public void setTeleportId(int id) {
    StructureModifier<Integer> integers = packet().getIntegers();
	  if (integers.size() > 0) {
	    integers.write(0, id);
	  }
  }

  public double positionX() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().position().getX();
    }
    return packet().getDoubles().read(0);
  }

  public void setPositionX(double x) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().position().setX(x);
      mod = true;
    } else {
      packet().getDoubles().write(0, x);
    }
  }

  public double positionY() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().position().getY();
    }
    return packet().getDoubles().read(1);
  }

  public void setPositionY(double y) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().position().setY(y);
      mod = true;
    } else {
      packet().getDoubles().write(1, y);
    }
  }

  public double positionZ() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().position().getZ();
    }
    return packet().getDoubles().read(2);
  }

  public void setPositionZ(double z) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().position().setZ(z);
      mod = true;
    } else {
      packet().getDoubles().write(2, z);
    }
  }

  public Position position() {
    mod = true;
    return internalPosMoveRotation().position();
  }

  public float yaw() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().rotation().yaw();
    }
    return packet().getFloat().read(0);
  }

  public void setYaw(float yaw) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().rotation().setYaw(yaw);
      mod = true;
    } else {
      packet().getFloat().write(0, yaw);
    }
  }

  public float pitch() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().rotation().pitch();
    }
    return packet().getFloat().read(1);
  }

  public void setPitch(float pitch) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().rotation().setPitch(pitch);
      mod = true;
    } else {
      packet().getFloat().write(1, pitch);
    }
  }

  public Rotation rotation() {
    mod = true;
    return internalPosMoveRotation().rotation();
  }

  public double motionX() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().motion().motionX();
    }
    return 0;
  }

  public void setMotionX(double x) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().motion().setMotionX(x);
      mod = true;
    }
  }

  public double motionY() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().motion().motionY();
    }
    return 0;
  }

  public void setMotionY(double y) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().motion().setMotionY(y);
      mod = true;
    }
  }

  public double motionZ() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().motion().motionZ();
    }
    return 0;
  }

  public void setMotionZ(double z) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().motion().setMotionZ(z);
      mod = true;
    }
  }

  private final static Motion UNUSED_MOTION = new Motion(0, 0, 0);

  public Motion motion() {
    if (DIRECT_PMR_USED) {
      mod = true;
      return internalPosMoveRotation().motion();
    }
    return UNUSED_MOTION;
  }

  public PositionMoveRotation positionMoveRotation() {
    mod = true;
    return internalPosMoveRotation();
  }

  private PositionMoveRotation internalPosMoveRotation() {
    if (positionMoveRotation == null) {
      if (DIRECT_PMR_USED) {
        positionMoveRotation = packet().getModifier().withType(
          PosMoveRotConverter.nativePositionMoveRotClass,
          PosMoveRotConverter.INSTANCE
        ).read(0);
      } else {
        positionMoveRotation = new PositionMoveRotation(
          Position.mutableOf(
            packet().getDoubles().read(0),
            packet().getDoubles().read(1),
            packet().getDoubles().read(2)
          ),
          new Motion(0, 0, 0),
          new Rotation(
            packet().getFloat().read(0),
            packet().getFloat().read(1)
          )
        );
      }
    }
    return positionMoveRotation;
  }

  private void writePositionMoveRotation(PositionMoveRotation posMoveRot) {
    if (DIRECT_PMR_USED) {
      packet().getModifier().withType(
        PosMoveRotConverter.nativePositionMoveRotClass,
        PosMoveRotConverter.INSTANCE
      ).write(0, posMoveRot);
    } else {
      packet().getDoubles().write(0, posMoveRot.position().getX());
      packet().getDoubles().write(1, posMoveRot.position().getY());
      packet().getDoubles().write(2, posMoveRot.position().getZ());
      packet().getFloat().write(0, posMoveRot.rotation().yaw());
      packet().getFloat().write(1, posMoveRot.rotation().pitch());
    }
  }

  public void setPositionMoveRotation(PositionMoveRotation posMoveRot) {
    writePositionMoveRotation(posMoveRot);
    positionMoveRotation = posMoveRot;
    mod = true;
  }

  /*
    Flushing is usually not required, but some very niece packet readers do
    require flushing before the packet is accessed.
    If you want to access a packet modified with a packet-reader, make sure
    to add a call to this method before.
   */
  @Override
  public void flush() {
    if (mod) {
      writePositionMoveRotation(positionMoveRotation);
    }
    mod = false;
    super.flush();
  }

  @Override
  public void release() {
    flush();
    positionMoveRotation = null;
    companionMotion = null;
    additiveCompanionMotion = null;
    super.release();
  }

  // if it is just adding motion (all relative and motions are the only thing with chg),
  //  we can replace it with an explosion packet
  public boolean couldBeAnExplosionPacketInstead() {
    if (!DIRECT_PMR_USED) {
      return false;
    }
    PositionMoveRotation pmr = internalPosMoveRotation();
    return pmr.position().isZero() && pmr.rotation().isZero() && !pmr.motion().isZero();
  }

  public Teleport readTeleport(long uniqueId) {
    return new Teleport(uniqueId, teleportId(), positionMoveRotation(), flags());
  }

  public PacketContainer motionCompanionPacket(Entity entity) {
    if (MinecraftVersions.VER1_21_3.atOrAbove()) {
      return null;
    }
    Motion legacy = companionMotion;
    Boolean additive = additiveCompanionMotion;
    PacketContainer companion = null;
    if (additive != null) {
      companion = ProtocolLibrary.getProtocolManager().createPacket(additive ?
        PacketType.Play.Server.EXPLOSION :
        PacketType.Play.Server.ENTITY_VELOCITY
      );
      if (additive) {
        try (ExplosionReader reader = PacketReaders.readerOf(companion)) {
          reader.setSilentDefaults();
          reader.setMotion(legacy);
        }
      } else {
        try (EntityVelocityReader reader = PacketReaders.readerOf(companion)) {
          reader.setEntityId(entity.getEntityId());
          reader.setMotion(legacy);
        }
      }
    }
    return companion;
  }

  public void writeTeleport(Teleport teleport) {
		if (teleport == null) {
			throw new IllegalArgumentException("teleport must not be null");
		}
    additiveCompanionMotion = teleport.additiveMotionPacket();
    PositionMoveRotation change = teleport.change();
    Motion motion = change.motion();
    companionMotion = additiveCompanionMotion == null ? null : motion.copy();
		setPositionMoveRotation(change);
		setFlags(teleport.relativeSet());
    if (teleport.id().isPresent()) {
			setTeleportId(teleport.id().getAsInt());
		}
	}

  public Set<Relative> flags() {
    return Relative.flagsFrom(packet());
  }

  public void setFlags(Set<Relative> flags) {
    if (!DIRECT_PMR_USED) {
      Set<Relative> legacyFlags = EnumSet.noneOf(Relative.class);
      legacyFlags.addAll(flags);
      legacyFlags.retainAll(Relative.RELATIVE_POSITION_AND_ROTATION);
      Relative.writeFlags(packet(), legacyFlags);
    } else {
      Relative.writeFlags(packet(), flags);
    }
  }
}
