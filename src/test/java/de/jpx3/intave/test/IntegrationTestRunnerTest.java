package de.jpx3.intave.test;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class IntegrationTestRunnerTest {
  private final AtomicLong clock = new AtomicLong();
  private final Queue<Runnable> scheduled = new ArrayDeque<>();
  private final List<Integer> executed = new ArrayList<>();
  private final List<Throwable> failures = new ArrayList<>();
  private int successes;

  @Test
  void yieldsAtTheBudgetAndCompletesOnlyAfterTheLastTick() {
    IntegrationTestRunner runner = runner(Arrays.asList(
      test(1, 1_000_000), test(2, 1_000_000), test(3, 1_000_000)));

    runner.run();

    assertEquals(Arrays.asList(1, 2), executed);
    assertEquals(1, scheduled.size());
    assertEquals(0, successes);
    // Time spent waiting for a server tick does not count against its new budget.
    clock.addAndGet(50_000_000);
    scheduled.remove().run();
    assertEquals(Arrays.asList(1, 2, 3), executed);
    assertEquals(1, successes);
    assertTrue(scheduled.isEmpty());
    assertTrue(failures.isEmpty());
    runner.run();
    assertEquals(1, successes);
  }

  @Test
  void anExpensiveTestFinishesButTheNextTestWaitsForAnotherTick() {
    runner(Arrays.asList(test(1, 5_000_000), test(2, 0))).run();

    assertEquals(Collections.singletonList(1), executed);
    assertEquals(0, successes);
    scheduled.remove().run();
    assertEquals(Arrays.asList(1, 2), executed);
    assertEquals(1, successes);
  }

  @Test
  void fastTestsShareATickWithoutAnUnnecessaryCompletionTick() {
    runner(Arrays.asList(test(1, 100), test(2, 100), test(3, 100))).run();

    assertEquals(Arrays.asList(1, 2, 3), executed);
    assertEquals(1, successes);
    assertTrue(scheduled.isEmpty());
  }

  @Test
  void emptySuiteCompletesOnce() {
    IntegrationTestRunner runner = runner(Collections.emptyList());
    runner.run();
    runner.run();

    assertEquals(1, successes);
    assertTrue(scheduled.isEmpty());
  }

  @Test
  void failureOnALaterTickAbortsRemainingTestsAndDoesNotReportSuccess() {
    AssertionError error = new AssertionError("test failure");
    IntegrationTestRunner runner = runner(Arrays.asList(test(1, 2_000_000), () -> {
      throw error;
    }, test(3, 0)));
    runner.run();
    scheduled.remove().run();
    runner.run();

    assertEquals(Collections.singletonList(1), executed);
    assertEquals(Collections.singletonList(error), failures);
    assertEquals(0, successes);
    assertTrue(scheduled.isEmpty());
  }

  @Test
  void reschedulingFailureAbortsTheSuite() {
    IllegalStateException error = new IllegalStateException("scheduler unavailable");
    IntegrationTestRunner runner = new IntegrationTestRunner(
      Arrays.asList(test(1, 2_000_000), test(2, 0)), next -> { throw error; },
      () -> successes++, failures::add, clock::get);
    runner.run();
    runner.run();

    assertEquals(Collections.singletonList(1), executed);
    assertEquals(Collections.singletonList(error), failures);
    assertEquals(0, successes);
  }

  @Test
  void parameterCasesUseFreshFixturesAndRestoreStateBeforeYielding() {
    ParameterFixture.clock = clock;
    ParameterFixture.instances.clear();
    ParameterFixture.events.clear();
    List<Runnable> tests = new IntegrationTester(ParameterFixture.class).createTests();
    assertEquals(2, tests.size());
    assertTrue(ParameterFixture.instances.isEmpty());
    new IntegrationTestRunner(tests, next -> {
      assertFalse(ParameterFixture.modified);
      scheduled.add(next);
    }, () -> successes++, failures::add, clock::get).run();

    assertEquals(Arrays.asList("before", "first", "after"), ParameterFixture.events);
    assertEquals(0, successes);
    scheduled.remove().run();
    assertEquals(Arrays.asList("before", "first", "after", "before", "second", "after"),
      ParameterFixture.events);
    assertEquals(2, ParameterFixture.instances.size());
    assertNotSame(ParameterFixture.instances.get(0), ParameterFixture.instances.get(1));
    assertEquals(1, successes);
    assertTrue(failures.isEmpty());
  }

  @Test
  void ordinaryTestMethodsAreSeparateRunnableFixtures() {
    assertEquals(2, new IntegrationTester(OrdinaryFixture.class).createTests().size());
  }

  @Test
  void emptyParameterSourceCannotSilentlySkipCoverage() {
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
      () -> new IntegrationTester(EmptyParameterFixture.class).createTests());
    assertTrue(failure.getMessage().contains("Empty parameter source"));
  }

  private IntegrationTestRunner runner(List<Runnable> tests) {
    return new IntegrationTestRunner(tests, scheduled::add, () -> successes++, failures::add, clock::get);
  }

  private Runnable test(int id, long duration) {
    return () -> {
      executed.add(id);
      clock.addAndGet(duration);
    };
  }

  public static final class ParameterFixture extends IntegrationTests {
    private static final List<ParameterFixture> instances = new ArrayList<>();
    private static final List<String> events = new ArrayList<>();
    private static AtomicLong clock;
    private static boolean modified;

    public ParameterFixture() {
      super("parameter-fixture");
      instances.add(this);
    }

    public static Iterable<String> parameters() {
      return Arrays.asList("first", "second");
    }

    @Before
    public void before() {
      assertFalse(modified);
      modified = true;
      events.add("before");
      clock.addAndGet(1_000_000);
    }

    @de.jpx3.intave.test.Test(parameters = "parameters", severity = Severity.ERROR)
    public void test(String parameter) {
      assertTrue(modified);
      events.add(parameter);
    }

    @After
    public void after() {
      modified = false;
      events.add("after");
      clock.addAndGet(1_000_000);
    }
  }

  public static final class OrdinaryFixture extends IntegrationTests {
    public OrdinaryFixture() { super("ordinary-fixture"); }
    @de.jpx3.intave.test.Test public void first() { }
    @de.jpx3.intave.test.Test public void second() { }
  }

  public static final class EmptyParameterFixture extends IntegrationTests {
    public EmptyParameterFixture() { super("empty-parameter-fixture"); }
    public static Iterable<String> parameters() { return Collections.emptyList(); }
    @de.jpx3.intave.test.Test(parameters = "parameters") public void test(String value) { }
  }
}
