package de.jpx3.intave.block.physics;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.type.MaterialSearch;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class BounceSuppression {
  private static final Material HONEY = MaterialSearch.materialThatIsNamed("HONEY_BLOCK");
  private static volatile Set<Material> suppressingMaterials = HONEY == null
    ? Collections.emptySet() : Collections.singleton(HONEY);

  private BounceSuppression() {
  }

  static void setup(MinecraftVersion version) {
    Set<Material> materials = EnumSet.noneOf(Material.class);
    if (version.isAtLeast(MinecraftVersions.VER26_2) && Bukkit.getServer() != null) {
      for (Material material : Material.values()) {
        if (material.isBlock() && !material.name().startsWith("LEGACY_") && NativeTag.contains(material)) {
          materials.add(material);
        }
      }
    } else if (HONEY != null) {
      materials.add(HONEY);
    }
    suppressingMaterials = Collections.unmodifiableSet(materials);
  }

  public static boolean suppresses(Material material) {
    return suppressingMaterials.contains(material);
  }

  static boolean suppresses(Material material, Material honey) {
    return honey != null && honey.equals(material);
  }

  // Isolate modern Bukkit linkage from servers predating NamespacedKey and Tag.
  private static final class NativeTag {
    private static final Object TAG;
    private static final Method IS_TAGGED;

    static {
      try {
        Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
        Object key = keyClass.getConstructor(String.class, String.class).newInstance("minecraft", "suppresses_bounce");
        Method getTag = Bukkit.class.getMethod("getTag", String.class, keyClass, Class.class);
        TAG = getTag.invoke(null, "blocks", key, Material.class);
        IS_TAGGED = Class.forName("org.bukkit.Tag").getMethod("isTagged", Class.forName("org.bukkit.Keyed"));
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Unable to resolve bounce suppression tags", exception);
      }
    }

    private static boolean contains(Material material) {
      try {
        return TAG != null ? (boolean) IS_TAGGED.invoke(TAG, material) : suppresses(material, HONEY);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Unable to read bounce suppression tags", exception);
      }
    }
  }
}
