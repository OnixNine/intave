package de.jpx3.intave.version;

import de.jpx3.intave.adapter.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

final class ProtocolVersionRanges implements Iterable<ProtocolVersionRange> {
  private final Collection<ProtocolVersionRange> versionRanges;

  private final Map<MinecraftVersion, Integer> declaredReleases;
  private final Map<MinecraftVersion, Integer> releaseProtocols;

  public ProtocolVersionRanges(List<ProtocolVersionRange> versionRanges, Map<MinecraftVersion, Integer> declaredReleases) {
    this.versionRanges = versionRanges;
    this.declaredReleases = new HashMap<>(declaredReleases);
    this.releaseProtocols = new HashMap<>();
    for (ProtocolVersionRange range : versionRanges) {
      if (range.to() > 0) {
        releaseProtocols.put(range.asMinecraftVersion(), range.to());
      }
    }
    releaseProtocols.putAll(declaredReleases);
  }

  public int exactProtocolVersion(MinecraftVersion version) {
    if (version.isSnapshot() || version.getDevelopmentStage() != null) {
      return -1;
    }
    return releaseProtocols.getOrDefault(version, -1);
  }

  public ProtocolVersionRanges merge(ProtocolVersionRanges fallback) {
    Map<MinecraftVersion, Integer> declarations = new HashMap<>(fallback.declaredReleases);
    declarations.putAll(declaredReleases);
    ProtocolVersionRanges result = new ProtocolVersionRanges(new ArrayList<>(versionRanges), declarations);
    result.releaseProtocols.putAll(fallback.releaseProtocols);
    result.releaseProtocols.putAll(releaseProtocols);
    // An explicit release declaration is more precise than either source's display label.
    result.releaseProtocols.putAll(declarations);
    return result;
  }

  public Optional<ProtocolVersionRange> newest() {
    return versionRanges.stream()
      .max(ProtocolVersionRange::compareTo);
  }

  public String byProtocolVersion(int version) {
    ProtocolVersionRange protocolVersionRange =
      versionRanges.stream()
        .filter(range -> range.includes(version))
        .findFirst()
        .orElseGet(() -> newest().orElseGet(() -> new ProtocolVersionRange(Integer.MIN_VALUE, Integer.MAX_VALUE, "1.0.0")));
    return protocolVersionRange.version();
  }

  public int nearestProtocolVersion(String version) {
    MinecraftVersion requestVersion = new MinecraftVersion(version);
    Optional<ProtocolVersionRange> nearest = versionRanges.stream()
      .min((first, second) -> {
        int firstDistance = Math.abs(first.asMinecraftVersion().compareTo(requestVersion));
        int secondDistance = Math.abs(second.asMinecraftVersion().compareTo(requestVersion));
        return firstDistance - secondDistance;
      });
    return nearest.map(ProtocolVersionRange::to).orElseThrow(() -> new IllegalStateException("No protocol version ranges available"));
  }

  @NotNull
  @Override
  public Iterator<ProtocolVersionRange> iterator() {
    return versionRanges.iterator();
  }

  @Override
  public void forEach(Consumer<? super ProtocolVersionRange> action) {
    versionRanges.forEach(action);
  }

  @Override
  public Spliterator<ProtocolVersionRange> spliterator() {
    return versionRanges.spliterator();
  }
}
