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

import ac.intave.samples.event.BlockUpdatesEvent;
import ac.intave.samples.event.Event;
import ac.intave.samples.event.PropertiesEvent;
import ac.intave.samples.serial.JsonReader;
import ac.intave.samples.serial.JsonWriter;
import ac.intave.samples.share.Block;
import ac.intave.samples.share.BlockPosition;
import ac.intave.samples.share.BlockUpdate;
import ac.intave.samples.share.BoundingBox;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class RecordEventSinkTest {
  @Test
  void ignoresEventsDeliveredAfterClose() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    RecordEventSink sink = new RecordEventSink(null, output);
    initializeWriter(sink, output);

    sink.visitAny(new PropertiesEvent(Collections.emptyMap()));
    sink.close();
    int sizeAfterClose = output.size();

    assertDoesNotThrow(() -> sink.visitAny(new PropertiesEvent(Collections.emptyMap())));
    assertDoesNotThrow(sink::close);
    assertEquals(sizeAfterClose, output.size());
  }

  @Test
  void samplesWriterSplitsComplexUpdatesIntoConsecutiveReadableEvents() throws Exception {
    List<BlockUpdate> updates = updates(24, 40);
    StringWriter output = new StringWriter();
    JsonWriter writer = new JsonWriter(output);
    BlockUpdatesEvent updateEvent = new BlockUpdatesEvent(updates);
    updateEvent.withOffset(17L);
    writer.visitAny(updateEvent);

    List<BlockUpdate> combined = new ArrayList<>();
    int decodedEvents = 0;
    try (JsonReader reader = new JsonReader(new StringReader(output.toString()))) {
      Event event;
      while ((event = reader.nextEvent()) != null) {
        BlockUpdatesEvent decoded = assertInstanceOf(BlockUpdatesEvent.class, event);
        assertEquals(decodedEvents == 0 ? 17L : 0L, decoded.offset());
        combined.addAll(decoded.updates());
        decodedEvents++;
      }
    }
    assertTrue(decodedEvents > 1);
    assertEquals(updates, combined);
  }

  @Test
  void samplesWriterRejectsASingleUpdateThatCannotFit() {
    List<BlockUpdate> update = updates(1, 400);
    JsonWriter writer = new JsonWriter(new StringWriter());

    assertThrows(
      IllegalArgumentException.class,
      () -> writer.visitAny(new BlockUpdatesEvent(update))
    );
  }

  private static List<BlockUpdate> updates(int updateCount, int boxesPerBlock) {
    List<BoundingBox> boxes = new ArrayList<>();
    for (int index = 0; index < boxesPerBlock; index++) {
      double min = index / (double) boxesPerBlock;
      double max = (index + 1) / (double) boxesPerBlock;
      boxes.add(new BoundingBox(min, 0.0, 0.0, max, 1.0, 1.0));
    }
    Block block = new Block("TEST_BLOCK", Collections.emptyMap(), boxes);
    List<BlockUpdate> updates = new ArrayList<>();
    for (int index = 0; index < updateCount; index++) {
      updates.add(new BlockUpdate(new BlockPosition(index, 64, 0), block));
    }
    return updates;
  }

  private static void initializeWriter(RecordEventSink sink, ByteArrayOutputStream output) throws Exception {
    Field writer = RecordEventSink.class.getDeclaredField("writer");
    writer.setAccessible(true);
    writer.set(sink, new JsonWriter(output));

    Field setup = RecordEventSink.class.getDeclaredField("setup");
    setup.setAccessible(true);
    setup.setBoolean(sink, true);
  }
}
