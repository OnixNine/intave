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

package de.jpx3.intave.module.tracker.player;

import ac.intave.samples.event.ScoreboardEvent;
import de.jpx3.intave.user.meta.ClientScoreboard.Snapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class PlayerScoreboardRecorderTest {
  @Test
  void combinesTitleAndFirstChangedLineAndEmitsOnlyChanges() {
    Snapshot before = new Snapshot(
      "Round 1", Arrays.asList("Kills: 2", "Deaths: 1", "Ping: 20")
    );
    Snapshot after = new Snapshot(
      "Round 2", Arrays.asList("Kills: 3", "Deaths: 1", "Ping: 25")
    );

    List<ScoreboardEvent> events = PlayerScoreboardRecorder.eventsBetween(before, after);

    assertEquals(2, events.size());
    assertEquals(Integer.valueOf(0), events.get(0).line());
    assertEquals("Kills: 3", events.get(0).text());
    assertEquals("Round 2", events.get(0).title());
    assertEquals(Integer.valueOf(2), events.get(1).line());
    assertEquals("Ping: 25", events.get(1).text());
    assertNull(events.get(1).title());
    assertEquals(Collections.emptyList(), PlayerScoreboardRecorder.eventsBetween(after, after));
  }

  @Test
  void clearsRemovedTitlesAndTrailingLines() {
    Snapshot before = new Snapshot("Title", Arrays.asList("First", "Second"));

    List<ScoreboardEvent> events = PlayerScoreboardRecorder.eventsBetween(
      before, Snapshot.empty()
    );

    assertEquals(2, events.size());
    assertEquals(Integer.valueOf(0), events.get(0).line());
    assertEquals("", events.get(0).text());
    assertEquals("", events.get(0).title());
    assertEquals(Integer.valueOf(1), events.get(1).line());
    assertEquals("", events.get(1).text());
    assertNull(events.get(1).title());
  }

  @Test
  void emitsAStandaloneTitleWhenLinesStayTheSame() {
    List<ScoreboardEvent> events = PlayerScoreboardRecorder.eventsBetween(
      new Snapshot("Old", Collections.singletonList("Line")),
      new Snapshot("New", Collections.singletonList("Line"))
    );

    assertEquals(1, events.size());
    assertEquals("New", events.get(0).title());
    assertNull(events.get(0).line());
    assertNull(events.get(0).text());
  }
}
