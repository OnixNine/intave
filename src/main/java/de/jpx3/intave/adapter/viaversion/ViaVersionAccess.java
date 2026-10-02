package de.jpx3.intave.adapter.viaversion;

import org.bukkit.entity.Player;

public interface ViaVersionAccess {
  void setup();

  void patchConfiguration();

  int protocolVersionOf(Player player);

  boolean ignoreBlocking(Player player);

  default boolean serverSideBlockConnections() {
    return true;
  }

  default void decrementReceivedPackets(Player player, int amount) {
  }

  boolean available(String version);

  String version();
}
