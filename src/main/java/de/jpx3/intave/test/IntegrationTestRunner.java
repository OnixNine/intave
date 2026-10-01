package de.jpx3.intave.test;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class IntegrationTestRunner implements Runnable {
	private static final long TICK_BUDGET_NANOS = 2_000_000L;

	private final Queue<Runnable> tests;
	private final Consumer<Runnable> nextTick;
	private final Runnable success;
	private final Consumer<Throwable> failure;
	private final LongSupplier nanoTime;
	private boolean finished;

	IntegrationTestRunner(
		Collection<Runnable> tests,
		Consumer<Runnable> nextTick,
		Runnable success,
		Consumer<Throwable> failure
	) {
		this(tests, nextTick, success, failure, System::nanoTime);
	}

	IntegrationTestRunner(
		Collection<Runnable> tests,
		Consumer<Runnable> nextTick,
		Runnable success,
		Consumer<Throwable> failure,
		LongSupplier nanoTime
	) {
		this.tests = new ArrayDeque<>(tests);
		this.nextTick = nextTick;
		this.success = success;
		this.failure = failure;
		this.nanoTime = nanoTime;
	}

	@Override
	public void run() {
		if (finished) {
			return;
		}
		try {
			long start = nanoTime.getAsLong();
			while (!tests.isEmpty()) {
				tests.remove().run();
				if (nanoTime.getAsLong() - start >= TICK_BUDGET_NANOS) {
					break;
				}
			}
			if (tests.isEmpty()) {
				finished = true;
				success.run();
			} else {
				nextTick.accept(this);
			}
		} catch (Throwable throwable) {
			finished = true;
			tests.clear();
			failure.accept(throwable);
		}
	}
}
