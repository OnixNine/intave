package de.jpx3.intave.user;

import de.jpx3.intave.cleanup.GarbageCollector;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

final class UserLocalTest {
  @Test
  void concurrentFirstAccessPublishesOneInstance() throws Exception {
    User user = user(UUID.randomUUID());
    AtomicInteger initialized = new AtomicInteger();
    UserLocal<int[]> local = UserLocal.withInitial(() -> new int[]{initialized.incrementAndGet()});
    ExecutorService executor = Executors.newFixedThreadPool(8);
    CyclicBarrier start = new CyclicBarrier(8);
    try {
      List<Future<int[]>> results = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        results.add(executor.submit(() -> {
          start.await(5, SECONDS);
          int[] value = local.get(user);
          assertEquals(1, value[0]);
          for (int hit = 0; hit < 100; hit++) {
            assertSame(value, local.get(user));
          }
          return value;
        }));
      }
      int[] expected = results.get(0).get(5, SECONDS);
      for (Future<int[]> result : results) {
        assertSame(expected, result.get(5, SECONDS));
      }
      assertEquals(1, initialized.get());
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, SECONDS));
    }
  }

  @Test
  void removalInvalidatesCachedValueWithoutAffectingOtherUsers() {
    UUID id = UUID.randomUUID();
    User user = user(id);
    User otherUser = user(UUID.randomUUID());
    UserLocal<Object> local = UserLocal.withInitial(Object::new);
    Object original = local.get(user);
    Object other = local.get(otherUser);

    assertSame(original, local.get(user));
    assertNotSame(original, other);
    GarbageCollector.clear(id);

    Object replacement = local.get(user);
    assertNotSame(original, replacement);
    assertSame(replacement, local.get(user));
    assertSame(other, local.get(otherUser));
  }

  @Test
  void retriesInitializationAfterNullResult() {
    User user = user(UUID.randomUUID());
    AtomicInteger initialized = new AtomicInteger();
    Object expected = new Object();
    UserLocal<Object> local = UserLocal.withInitial(() -> initialized.incrementAndGet() == 1 ? null : expected);

    assertNull(local.get(user));
    assertSame(expected, local.get(user));
    assertSame(expected, local.get(user));
    assertEquals(2, initialized.get());
  }

  @Test
  void usersWithoutPlayersRemainUncached() {
    User user = user(null);
    UserLocal<Object> local = UserLocal.withInitial(Object::new);

    assertNotSame(local.get(user), local.get(user));
  }

  private static User user(UUID id) {
    Player player = id == null ? null : (Player) Proxy.newProxyInstance(
      Player.class.getClassLoader(), new Class<?>[]{Player.class},
      (proxy, method, args) -> {
        if (method.getName().equals("getUniqueId")) return id;
        throw new AssertionError("Unexpected player access: " + method.getName());
      }
    );
    return (User) Proxy.newProxyInstance(
      User.class.getClassLoader(), new Class<?>[]{User.class},
      (proxy, method, args) -> switch (method.getName()) {
        case "hasPlayer" -> player != null;
        case "player" -> player;
        default -> throw new AssertionError("Unexpected user access: " + method.getName());
      }
    );
  }
}
