package de.jpx3.intave.block.physics;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.PlaybackBlockCacheView;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.recording.ReplayPotionRegistry;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.test.record.MaterialVariantStore;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.*;

final class WebPhysicsTest {
  private final WebPhysics physics = new WebPhysics();
  private final AtomicReference<List<PotionEffect>> effects = new AtomicReference<>(List.of());

  @BeforeEach
  void setup() {
    ReplayPotionRegistry.initialize();
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_2);
    BlockPhysics.setup(MinecraftVersions.VER26_2);
  }

  @Test
  void weavingUsesClientMultipliersFrom1205RegardlessOfAmplifier() {
    // WebBlock in 1.20.5, 1.21.1 and 26.1.2 selects a constant Vec3(0.5, 0.25, 0.5).
    for (int protocol : new int[]{VER_1_20_5, VER_1_21, VER_26_1_1}) {
      for (int amplifier : new int[]{0, 5}) {
        effects.set(List.of(new PotionEffect(PotionEffectType.WEAVING, 200, amplifier)));
        User user = user(protocol);
        SimulationEnvironment environment = environment(user);
        contact(user, environment);
        assertMotion(user, environment, 0.5D, 0.25D);
      }
    }
  }

  @Test
  void olderClientsAndPlayersWithoutWeavingKeepFloatVerticalSlowdown() {
    for (int protocol : new int[]{VER_1_20_5 - 1, VER_1_20_5, VER_1_21, VER_26_1_1}) {
      effects.set(protocol < VER_1_20_5
        ? List.of(new PotionEffect(PotionEffectType.WEAVING, 200, 0)) : List.of());
      User user = user(protocol);
      SimulationEnvironment environment = environment(user);
      contact(user, environment);
      assertMotion(user, environment, 0.25D, (double) 0.05F);
    }
  }

  @Test
  void flyingRetainsPreWebVerticalVelocityForPlayerTravel() {
    for (int protocol : new int[]{VER_1_8, VER_26_1_1}) {
      User user = user(protocol);
      user.meta().abilities().setAllowFlying(true);
      user.meta().abilities().setFlying(true);
      SimulationEnvironment environment = environment(user);
      contact(user, environment);
      Motion input = Motion.of(0.4D, 0.8D, -0.6D);
      SimulationResult result = Colliders.collision(
        user, environment, input, true, 0.0D, 64.0D, 0.0D
      );

      assertEquals(Motion.of(0.0D, 0.8D, 0.0D), result.actualMotion());
    }
  }

  @Test
  void contactCapturesEffectForTheNextMoveAndResetsFallDistance() {
    User user = user(VER_26_1_1);
    SimulationEnvironment environment = environment(user);
    environment.addFallDistance(5.0D);
    contact(user, environment);
    effects.set(List.of(new PotionEffect(PotionEffectType.WEAVING, 200, 0)));
    assertMotion(user, environment, 0.25D, (double) 0.05F);
    assertEquals(0.0D, environment.fallDistance());

    environment.resetInWeb();
    environment.resetMotionMultiplier();
    contact(user, environment);
    effects.set(List.of());
    assertMotion(user, environment, 0.5D, 0.25D);

    environment.resetInWeb();
    environment.resetMotionMultiplier();
    var result = Colliders.collision(user, environment, Motion.of(0.4D, 0.8D, -0.6D), false, 0, 64, 0);
    assertEquals(Motion.of(0.4D, 0.8D, -0.6D), result.offsetMotion());
    assertEquals(result.offsetMotion(), result.actualMotion());
  }

  @Test
  void ridersWeavingDoesNotApplyToTheirBoat() {
    effects.set(List.of(new PotionEffect(PotionEffectType.WEAVING, 200, 0)));
    User user = user(VER_26_1_1);
    user.meta().movement().restoreRecordedVehicle(new Entity(1,
      new EntityTypeData("Boat", HitboxSize.of(1.375F, 0.5625F), 1, false, 0), false));
    SimulationEnvironment environment = user.meta().movement().mutableView();
    assertTrue(environment.isInVehicle());
    contact(user, environment);
    assertEquals(new Vector(0.25D, 0.05F, 0.25D), environment.motionMultiplier());
  }

  @Test
  void webClearedVelocityStillUpdatesTheSupportingBlock() {
    var recording = MovementRecording.create();
    recording.collisionShapes().put(Material.STONE, Map.of(0, BlockShapes.cubeAt(0, 0, 0)));
    recording.collisionShapes().put(Material.ICE, Map.of(0, BlockShapes.cubeAt(0, 0, 0)));
    var blocks = new PlaybackBlockCacheView(recording);
    BlockPosition stone = BlockPosition.of(0, 63, 0);
    BlockPosition ice = BlockPosition.of(0, 63, 1);
    blocks.updateBlocks(Map.of(stone, MaterialVariantStore.of(Material.STONE, 0),
      ice, MaterialVariantStore.of(Material.ICE, 0)));

    for (int protocol : new int[]{VER_1_20_1, VER_1_20_5, VER_26_1_1}) {
      User user = user(protocol, blocks);
      var root = user.meta().movement();
      root.setPosition(0.5D, 64.0D, 1.05D);
      root.setLastPosition(Position.of(0.5D, 64.0D, 0.95D));
      root.setMainSupportingBlockPos(stone);
      root.setFrictionMaterial(Material.STONE);
      root.setLastOnGround(true);
      root.onGround = true;
      root.setInWeb(true);
      // Entity.move clears velocity in webs but still reports a downward collision and displacement.
      Motion displacement = Motion.of(0.0D, 0.0D, 0.1D);
      root.setSimulationResult(new SimulationResult(Motion.newEmpty(), displacement,
        Motion.of(0.0D, -0.00392D, 0.1D), true, false, true, false, false, false, false, 0.0D));
      SimulationEnvironment candidate = root.mutableView();
      Simulators.PLAYER.simulateAfterTick(user, candidate, MovementConfiguration.blank(),
        candidate.position(), Motion.newEmpty());

      assertEquals(ice, candidate.mainSupportingBlockPos());
      assertEquals(Material.ICE, candidate.frictionMaterial());
      assertEquals(stone, root.mainSupportingBlockPos(), "The candidate must not change root state");
    }
  }

  @Test
  void webContactUsesTheExistingBranchLocalMultiplier() {
    User user = user(VER_26_1_1);
    SimulationEnvironment root = environment(user);
    SimulationEnvironment candidate = root.mutableView();
    effects.set(List.of(new PotionEffect(PotionEffectType.WEAVING, 200, 0)));
    contact(user, candidate);
    assertFalse(root.inWeb());
    assertNull(root.motionMultiplier());
    assertMotion(user, candidate, 0.5D, 0.25D);
    candidate.commitTo(root);
    assertEquals(new Vector(0.5D, 0.25D, 0.5D), root.motionMultiplier());

    // Ordinary web contact replaces another block's multiplier instead of multiplying both.
    effects.set(List.of());
    candidate.setMotionMultiplier(new Vector(0.8F, 0.75D, 0.8F));
    contact(user, candidate);
    assertMotion(user, candidate, 0.25D, (double) 0.05F);
  }

  private void contact(User user, SimulationEnvironment environment) {
    Motion motion = Motion.of(0.4D, 0.8D, -0.6D);
    assertNull(physics.entityInside(user, environment, BlockPosition.of(0, 64, 0), Position.of(0, 64, 0), motion, true));
    assertEquals(Motion.of(0.4D, 0.8D, -0.6D), motion, "Contact stores slowdown without scaling this tick's velocity");
  }

  private static void assertMotion(User user, SimulationEnvironment environment, double horizontal, double vertical) {
    var result = Colliders.collision(user, environment, Motion.of(0.4D, 0.8D, -0.6D), true, 0, 64, 0);
    assertEquals(0.4D * horizontal, result.offsetMotion().motionX(), 0.0D);
    assertEquals(0.8D * vertical, result.offsetMotion().motionY(), 0.0D);
    assertEquals(-0.6D * horizontal, result.offsetMotion().motionZ(), 0.0D);
    assertEquals(Motion.newEmpty(), result.actualMotion(), "Entity.move clears stored velocity when stuck");
  }

  private static SimulationEnvironment environment(User user) {
    var environment = new MockSimulationEnvironment(user);
    environment.setPosition(0, 64, 0);
    environment.setBoundingBox(BoundingBox.fromBounds(-0.3D, 64, -0.3D, 0.3D, 65.8D, 0.3D));
    return environment;
  }

  private User user(int protocol) {
    return user(protocol, new MockFullBlockStaticPlane());
  }

  private User user(int protocol, BlockCache blocks) {
    var world = FakeWorldFactory.createWorld((name, args) -> "isChunkLoaded".equals(name) ? true : null);
    UUID id = UUID.randomUUID();
    var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
      case "getWorld" -> world;
      case "getLocation" -> new Location(world, 0, 64, 0);
      case "getUniqueId" -> id;
      case "getActivePotionEffects" -> effects.get();
      default -> null;
    });
    User user = UserFactory.createTestUserFor(player, (ignored, key) -> switch (key) {
      case "blockCache" -> blocks;
      case "protocolVersion" -> protocol;
      default -> null;
    });
    UserRepository.manuallyRegisterUser(player, user);
    return user;
  }
}
