package de.jpx3.intave.block.physics;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.type.MaterialSearch;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class BounceSuppression {
  private static final Material HONEY = MaterialSearch.materialThatIsNamed("HONEY_BLOCK");

  private BounceSuppression() {
  }

  public static boolean suppresses(Material material) {
    if (MinecraftVersions.VER26_2.atOrAbove() && Bukkit.getServer() != null) {
      return NativeTag.contains(material);
    }
    return suppresses(material, HONEY);
  }

  static boolean suppresses(Material material, Material honey) {
    return honey != null && honey.equals(material);
  }

  // Isolate modern Bukkit linkage from servers predating NamespacedKey and Tag.
  private static final class NativeTag {
    private static final Constructor<?> KEY;
    private static final Method GET_TAG;
    private static final Method IS_TAGGED;

    static {
      try {
        Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
        KEY = keyClass.getConstructor(String.class, String.class);
        GET_TAG = Bukkit.class.getMethod("getTag", String.class, keyClass, Class.class);
        IS_TAGGED = Class.forName("org.bukkit.Tag").getMethod("isTagged", Class.forName("org.bukkit.Keyed"));
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Unable to resolve bounce suppression tags", exception);
      }
    }

    private static boolean contains(Material material) {
      try {
        Object tag = GET_TAG.invoke(null, "blocks", KEY.newInstance("minecraft", "suppresses_bounce"), Material.class);
        return tag != null ? (boolean) IS_TAGGED.invoke(tag, material) : suppresses(material, HONEY);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Unable to read bounce suppression tags", exception);
      }
    }
  }
}
