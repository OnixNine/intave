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

package de.jpx3.intave.module.test.record.action;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.TickRange;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class Action {
	private static final StreamCodec<ByteBuf, ByteBuf, Action> CURRENT_CODEC = StreamCodec.dispatchBuilder(Action.class, ActionType.STREAM_CODEC)
		.subtype(ActionType.RECEIVE_VELOCITY, ReceiveVelocity.class, () -> ReceiveVelocity.STREAM_CODEC)
		.subtype(ActionType.PISTON_SLIME, PistonSlimeAction.class, () -> PistonSlimeAction.STREAM_CODEC)
		.subtype(ActionType.SHULKER_BOX, ShulkerBoxAction.class, () -> ShulkerBoxAction.STREAM_CODEC)
		.subtype(ActionType.ATTACK_REDUCTION_V2, AttackReduction.class, () -> AttackReduction.STREAM_CODEC)
		.build();
	public static final StreamCodec<ByteBuf, ByteBuf, Action> STREAM_CODEC = StreamCodec.of(
		CURRENT_CODEC::encode,
		buffer -> {
			int start = buffer.readerIndex();
			if (ActionType.STREAM_CODEC.decode(buffer) == ActionType.ATTACK_REDUCTION) {
				// Legacy payloads contain only the range and may have multiple attacks per tick.
				return new AttackReduction(false, true,
					TickRange.STREAM_CODEC.decode(buffer));
			}
			buffer.readerIndex(start);
			return CURRENT_CODEC.decode(buffer);
		}
	);
	public static final StreamCodec<ByteBuf, ByteBuf, List<Action>> LIST_STREAM_CODEC =
		ByteBufStreamCodecs.listCodecOf(STREAM_CODEC);

	public abstract @NotNull ActionType type();
}
