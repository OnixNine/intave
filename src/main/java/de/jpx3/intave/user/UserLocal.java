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

package de.jpx3.intave.user;

import de.jpx3.intave.cleanup.GarbageCollector;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class UserLocal<T> {
  private final Function<? super User, ? extends T> initializer;
  private final Consumer<? super User> finalizer;

  private boolean finalizerSet;

  private final Map<UUID, T> map;

  private UserLocal(
    Function<? super User, ? extends T> initializer,
    Consumer<? super User> finalizer,
    boolean threadSafe
  ) {
    this.initializer = initializer;
    this.finalizer = finalizer;
    if (threadSafe) {
      map = GarbageCollector.watch(new ConcurrentHashMap<>());
    } else {
      map = GarbageCollector.watch(new HashMap<>());
    }
  }

  public T get(Player player) {
    return get(UserRepository.userOf(player));
  }

  public T get(User user) {
    if (user == null) {
      throw new IllegalArgumentException("User must not be null");
    }
    if (!user.hasPlayer()) {
      return initializer.apply(user);
    }
    UUID id = user.player().getUniqueId();
    if (finalizer != null && !finalizerSet) {
      GarbageCollector.subscribeToRemoval(id, () -> finalizer.accept(user));
      finalizerSet = true;
    }
    // Avoid computeIfAbsent's locking on the common read path.
    T cached = map.get(id);
    if (cached != null) {
      return cached;
    }
    return map.computeIfAbsent(id, uuid -> initializer.apply(user));
  }

  public static <T> UserLocal<T> withInitial(T value) {
    return new UserLocal<>(u -> value, null, true);
  }

  public static <T> UserLocal<T> withInitial(Supplier<? extends T> initializer) {
    return new UserLocal<>(user -> initializer.get(), null, true);
  }

  public static <T> UserLocal<T> withInitial(Function<? super User, ? extends T> initializer) {
    return new UserLocal<>(initializer, null, true);
  }

  public static <T> UserLocal<T> withInitial(
    Function<? super User, ? extends T> initializer,
    Consumer<? super User> finalizer
  ) {
    return new UserLocal<>(initializer, finalizer, true);
  }

  public static <T> UserLocal<T> withInitialThreadUnsafe(
    Function<? super User, ? extends T> initializer,
    Consumer<? super User> finalizer
  ) {
    return new UserLocal<>(initializer, finalizer, false);
  }

  public static <T> UserLocal<T> withInitialThreadUnsafe(
    Function<? super User, ? extends T> initializer
  ) {
    return new UserLocal<>(initializer, null, false);
  }
}
