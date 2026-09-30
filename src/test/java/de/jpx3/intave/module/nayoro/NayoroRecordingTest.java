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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.AttackEvent;
import ac.intave.samples.event.ClickEvent;
import ac.intave.samples.event.EntityInteractEvent;
import ac.intave.samples.event.Event;
import ac.intave.samples.event.HeaderEvent;
import ac.intave.samples.event.ItemActionEvent;
import ac.intave.samples.event.MarkerEvent;
import ac.intave.samples.event.PlayerFlyToggleEvent;
import ac.intave.samples.event.PlayerMoveEvent;
import ac.intave.samples.event.TotemPopEvent;
import ac.intave.samples.serial.JsonReader;
import ac.intave.samples.serial.JsonWriter;
import ac.intave.samples.share.Classifier;
import ac.intave.samples.share.Hand;
import ac.intave.samples.share.Item;
import ac.intave.samples.share.Position;
import ac.intave.samples.share.Rotation;
import ac.intave.samples.share.Vector3d;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class NayoroRecordingTest {
  @Test
  void dependencyJsonSerializerRoundTripsNayoroEvents() throws Exception {
    UUID recordingId = UUID.randomUUID();
    HeaderEvent header = new HeaderEvent(
      recordingId, "test", Classifier.LEGIT, 1_786_320_000_000L
    );
    AttackEvent attack = new AttackEvent(8, 13, 0.75F);
    attack.withOffset(20);
    ClickEvent click = new ClickEvent(Hand.OFF_HAND);
    click.withOffset(5);
    EntityInteractEvent interaction = new EntityInteractEvent(
      8, 21, EntityInteractEvent.Action.INTERACT_AT,
      Hand.MAIN_HAND, new Vector3d(0.25, 0.5, 0.75)
    );
    interaction.withOffset(7);
    PlayerMoveEvent movement = PlayerMoveEvent.create(
      1, -1,
      new Position(12.5, 64, -3.25), new Rotation(90, 10),
      true, true, false, false, false, true, false, true,
      true, false, "CROUCHING"
    );
    movement.withOffset(50);
    PlayerFlyToggleEvent flyToggle = new PlayerFlyToggleEvent(true);
    flyToggle.withOffset(10);
    TotemPopEvent totemPop = new TotemPopEvent(21);
    totemPop.withOffset(4);
    UUID markerId = UUID.randomUUID();
    MarkerEvent marker = new MarkerEvent(markerId);
    marker.withOffset(60_000);
    ItemActionEvent itemAction = new ItemActionEvent(
      ItemActionEvent.Action.STAB, Hand.MAIN_HAND, Item.air(), 0.5F
    );
    itemAction.withOffset(3);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (JsonWriter writer = new JsonWriter(output)) {
      header.accept(writer);
      attack.accept(writer);
      click.accept(writer);
      interaction.accept(writer);
      movement.accept(writer);
      flyToggle.accept(writer);
      totemPop.accept(writer);
      marker.accept(writer);
      itemAction.accept(writer);
    }

    byte[] recording = output.toByteArray();
    assertTrue(recording.length > 4);
    assertEquals(0x28, recording[0] & 0xff);
    assertEquals(0xb5, recording[1] & 0xff);
    assertEquals(0x2f, recording[2] & 0xff);
    assertEquals(0xfd, recording[3] & 0xff);

    try (JsonReader reader = new JsonReader(new ByteArrayInputStream(recording))) {
      HeaderEvent decodedHeader = assertInstanceOf(HeaderEvent.class, reader.nextEvent());
      assertEquals(recordingId, decodedHeader.id());
      assertEquals("test", decodedHeader.licenseName());
      assertEquals(Classifier.LEGIT, decodedHeader.classifier());

      AttackEvent decodedAttack = assertInstanceOf(AttackEvent.class, reader.nextEvent());
      assertEquals(8, decodedAttack.source());
      assertEquals(13, decodedAttack.target());
      assertEquals(0.75F, decodedAttack.attackStrength());
      assertEquals(20, decodedAttack.offset());

      ClickEvent decodedClick = assertInstanceOf(ClickEvent.class, reader.nextEvent());
      assertEquals(Hand.OFF_HAND, decodedClick.hand());
      assertEquals(5, decodedClick.offset());

      EntityInteractEvent decodedInteraction = assertInstanceOf(
        EntityInteractEvent.class, reader.nextEvent()
      );
      assertEquals(8, decodedInteraction.source());
      assertEquals(21, decodedInteraction.target());
      assertEquals(EntityInteractEvent.Action.INTERACT_AT, decodedInteraction.action());
      assertEquals(Hand.MAIN_HAND, decodedInteraction.hand());
      assertEquals(new Vector3d(0.25, 0.5, 0.75), decodedInteraction.hitPosition());
      assertEquals(7, decodedInteraction.offset());

      Event decodedMovement = reader.nextEvent();
      assertEquals(movement, decodedMovement);
      assertEquals(50, decodedMovement.offset());
      PlayerFlyToggleEvent decodedFlyToggle = assertInstanceOf(
        PlayerFlyToggleEvent.class, reader.nextEvent()
      );
      assertTrue(decodedFlyToggle.isFlying());
      assertEquals(10, decodedFlyToggle.offset());
      TotemPopEvent decodedTotemPop = assertInstanceOf(
        TotemPopEvent.class, reader.nextEvent()
      );
      assertEquals(21, decodedTotemPop.entityId());
      assertEquals(4, decodedTotemPop.offset());
      MarkerEvent decodedMarker = assertInstanceOf(MarkerEvent.class, reader.nextEvent());
      assertEquals(markerId, decodedMarker.uuid());
      assertEquals(60_000, decodedMarker.offset());
      ItemActionEvent decodedItemAction = assertInstanceOf(
        ItemActionEvent.class, reader.nextEvent()
      );
      assertEquals(ItemActionEvent.Action.STAB, decodedItemAction.action());
      assertEquals(Hand.MAIN_HAND, decodedItemAction.hand());
      assertEquals("AIR", decodedItemAction.item().type());
      assertEquals(0.5F, decodedItemAction.attackStrength());
      assertEquals(3, decodedItemAction.offset());
      assertNull(reader.nextEvent());
    }
  }
}
