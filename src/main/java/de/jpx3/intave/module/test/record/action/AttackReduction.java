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

import java.util.Objects;

/**
 * One client-side horizontal-motion reduction caused by a successful knockback attack.
 *
 * <p>The action is timestamped independently of movement frames so a replay can place it in a
 * skipped client tick through the tick-ambiguous update system. {@code lastActiveInTick} preserves
 * the tick boundary even when no movement packet was recorded between two reductions.
 */
public final class AttackReduction extends Action {
	public static final StreamCodec<ByteBuf, ByteBuf, AttackReduction> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.BOOLEAN, AttackReduction::lastActiveInTick,
			ByteBufStreamCodecs.BOOLEAN, AttackReduction::mandatory,
			TickRange.STREAM_CODEC, AttackReduction::tickRange,
			AttackReduction::new
		);

	private final boolean lastActiveInTick;
	private final boolean mandatory;
	private final TickRange tickRange;

	public AttackReduction(
		boolean lastActiveInTick, boolean mandatory, TickRange tickRange
	) {
		this.lastActiveInTick = lastActiveInTick;
		this.mandatory = mandatory;
		this.tickRange = Objects.requireNonNull(tickRange, "tickRange");
	}

	public boolean lastActiveInTick() {
		return lastActiveInTick;
	}

	public boolean mandatory() {
		return mandatory;
	}

	public TickRange tickRange() {
		return tickRange;
	}

	@Override
	public @NotNull ActionType type() {
		return ActionType.ATTACK_REDUCTION_V2;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		return object instanceof AttackReduction
			&& lastActiveInTick == ((AttackReduction) object).lastActiveInTick
			&& mandatory == ((AttackReduction) object).mandatory
			&& tickRange.equals(((AttackReduction) object).tickRange);
	}

	@Override
	public int hashCode() {
		return Objects.hash(lastActiveInTick, mandatory, tickRange);
	}

	@Override
	public String toString() {
		return "AttackReduction{lastActiveInTick=" + lastActiveInTick
			+ ", mandatory=" + mandatory
			+ ", tickRange=" + tickRange + '}';
	}
}
