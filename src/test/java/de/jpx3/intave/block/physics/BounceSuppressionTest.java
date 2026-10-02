package de.jpx3.intave.block.physics;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BounceSuppressionTest {
  @Test
  void missingHoneyOnOldServersDoesNotSuppressSlimeOrBedBounces() {
    assertFalse(BounceSuppression.suppresses(Material.SLIME_BLOCK, null));
    assertFalse(BounceSuppression.suppresses(Material.STONE, null));
  }

  @Test
  void onlyTheSuppressingMaterialIsExcludedWhenAvailable() {
    Material honey = Material.valueOf("HONEY_BLOCK");
    assertTrue(BounceSuppression.suppresses(honey, honey));
    assertFalse(BounceSuppression.suppresses(Material.SLIME_BLOCK, honey));
  }
}
