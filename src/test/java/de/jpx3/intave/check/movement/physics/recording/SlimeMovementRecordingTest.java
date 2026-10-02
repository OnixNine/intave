package de.jpx3.intave.check.movement.physics.recording;

import org.junit.jupiter.api.Test;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.resource.Resources;

import java.io.IOException;

final class SlimeMovementRecordingTest {
  @Test
  void modernAndLegacySlimeRecordingsRetainClientParity() throws IOException {
    for (String recording : new String[]{"slime_26.2.ptr", "slime_26.2_2.ptr", "slime_jumping_4_1_8_8.ptr"}) {
      String resource = "physics_test_runs/blocks/slime/" + recording;
      MovementRecording data = MovementRecording.loadFrom(Resources.resourceFromJarOrTestBuild(resource));
      System.out.println(recording + ": protocol=" + data.clientProtocolVersion() + ", server=" + data.serverVersion());
      MovementRecordingPhysicsTests.processRecordingResource(resource);
    }
  }
}
