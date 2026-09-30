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

package de.jpx3.intave.check.movement.physics.recording.action;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.module.test.record.TickRange;
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.module.test.record.action.AttackReduction;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ActionCodecTest {

	@Test
	void readsLegacyAttackPayloadWithoutConsumingTheNextAction() {
		ByteBuf buffer = Unpooled.buffer();
		try {
			// Original wire format: the action name followed by exactly two longs.
			ByteBufStreamCodecs.STRING.encode(buffer, "ATTACK_REDUCTION");
			buffer.writeLong(3).writeLong(4);
			ReceiveVelocity next = new ReceiveVelocity(new Motion(1, 2, 3), new TickRange(4, 6));
			Action.STREAM_CODEC.encode(buffer, next);
			AttackReduction attack = assertInstanceOf(AttackReduction.class, Action.STREAM_CODEC.decode(buffer));
			assertEquals(new TickRange(3, 4), attack.tickRange());
			assertTrue(attack.mandatory());
			assertFalse(attack.lastActiveInTick());
			assertEquals(next, Action.STREAM_CODEC.decode(buffer));
			assertEquals(0, buffer.readableBytes());
		} finally {
			buffer.release();
		}
	}

	@Test
	void writesVersionedAttacksAndPreservesEveryFlagCombination() {
		for (boolean last : new boolean[]{false, true}) {
			for (boolean mandatory : new boolean[]{false, true}) {
				ByteBuf buffer = Unpooled.buffer();
				try {
					AttackReduction attack = new AttackReduction(last, mandatory, new TickRange(17, 19));
					Action.STREAM_CODEC.encode(buffer, attack);
					assertEquals("ATTACK_REDUCTION_V2", ByteBufStreamCodecs.STRING.decode(buffer));
					buffer.readerIndex(0);
					assertEquals(attack, Action.STREAM_CODEC.decode(buffer));
					assertEquals(0, buffer.readableBytes());
				} finally {
					buffer.release();
				}
			}
		}
	}

	@Test
	public void testReceiveVelocity() {
		ReceiveVelocity receiveVelocity = new ReceiveVelocity(Motion.random(), TickRange.random());

		ByteBuf buf = Unpooled.buffer();
		StreamCodec<ByteBuf, ByteBuf, Action> actionCodec = Action.STREAM_CODEC;

		actionCodec.encode(buf, receiveVelocity);
		Action reconstructed = actionCodec.decode(buf);

		assertInstanceOf(ReceiveVelocity.class, reconstructed);
		assertEquals(receiveVelocity, reconstructed);
	}

	@Test
	void testAttackReduction() {
		AttackReduction reduction = new AttackReduction(
			true, true, TickRange.betweenExclusive(3, 4)
		);

		ByteBuf buffer = Unpooled.buffer();
		try {
			Action.STREAM_CODEC.encode(buffer, reduction);
			Action reconstructed = Action.STREAM_CODEC.decode(buffer);

			assertInstanceOf(AttackReduction.class, reconstructed);
			assertEquals(reduction, reconstructed);
			assertTrue(((AttackReduction) reconstructed).lastActiveInTick());
			assertTrue(((AttackReduction) reconstructed).mandatory());
		} finally {
			buffer.release();
		}
	}
}
