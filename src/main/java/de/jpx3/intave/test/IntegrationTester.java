package de.jpx3.intave.test;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class IntegrationTester implements Runnable {
  private final Class<? extends IntegrationTests> testClass;

  private Method beforeMethod;
  private Method afterMethod;

  private final List<Method> testMethods = new ArrayList<>();

  public IntegrationTester(Class<? extends IntegrationTests> testClass) {
    this.testClass = testClass;
    prepare();
  }

  @Override
  public void run() {
    for (Runnable test : createTests()) {
      test.run();
    }
  }

  private void prepare() {
    Method[] methods = testClass.getMethods();
    for (Method method : methods) {
      if (method.getAnnotation(Test.class) != null) {
        testMethods.add(method);
      }
      if (method.getAnnotation(Before.class) != null) {
        if (beforeMethod != null) {
          throw new RuntimeException("Only one @Before method allowed");
        }
        beforeMethod = method;
      }
      if (method.getAnnotation(After.class) != null) {
        if (afterMethod != null) {
          throw new RuntimeException("Only one @After method allowed");
        }
        afterMethod = method;
      }
    }
  }

  public List<Runnable> createTests() {
    List<Runnable> tests = new ArrayList<>();
    for (Method method : testMethods) {
      String parameters = method.getAnnotation(Test.class).parameters();
      if (parameters.isEmpty()) {
        if (method.getParameterCount() != 0) {
          throw new IllegalArgumentException("Missing parameter source for " + method);
        }
        tests.add(() -> runTest(method));
        continue;
      }
      if (method.getParameterCount() != 1) {
        throw new IllegalArgumentException("Parameterized test must take one argument: " + method);
      }
      try {
        Method source = testClass.getMethod(parameters);
        if (!Modifier.isStatic(source.getModifiers()) || !Iterable.class.isAssignableFrom(source.getReturnType())) {
          throw new IllegalArgumentException("Parameter source must be static and return Iterable: " + source);
        }
        int count = 0;
        for (Object parameter : (Iterable<?>) source.invoke(null)) {
          tests.add(() -> runTest(method, parameter));
          count++;
        }
        if (count == 0) {
          throw new IllegalArgumentException("Empty parameter source for " + method);
        }
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Failed to load parameters for " + method, exception);
      }
    }
    return tests;
  }

  private void runTest(Method testMethod, Object... parameters) {
    Test annotation = testMethod.getAnnotation(Test.class);
    String testCode = annotation.testCode();

    IntegrationTests test;
    try {
      test = testClass.newInstance();
    } catch (Exception exception) {
      throw new RuntimeException("Failed to instantiate test", exception);
    }
    String fullTestName = test.testCode() + "::" + testCode;
    if (parameters.length != 0) {
      fullTestName += Arrays.toString(parameters);
    }
    try {
      if (beforeMethod != null) {
        long start = System.currentTimeMillis();
        beforeMethod.invoke(test);
        long end = System.currentTimeMillis();
        long duration = end - start;
        if (duration > 100 && IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
          IntaveLogger.logger().warn("[debug] Before method of test " + fullTestName + " took " + duration + "ms!");
        }
      }
    } catch (Throwable t) {
      throw new RuntimeException("Failed to run @Before method for "+ fullTestName, t);
    }
    try {
      if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
//        IntaveLogger.logger().info("Running test: " + testMethod.getName());
      }

      long start = System.currentTimeMillis();
      testMethod.invoke(test, parameters);
      long end = System.currentTimeMillis();
      long duration = end - start;
      if (duration > 250 && IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
        IntaveLogger.logger().warn("[debug] Test " + fullTestName + " took " + duration + "ms!");
      }
    } catch (Throwable throwable) {
      Severity severity = annotation.severity();
      String message = "Self-test " + fullTestName + " failed";
//      if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
//        throwable.printStackTrace();
//        while (throwable.getCause() != null) {
//          throwable = throwable.getCause();
//        }
//        message += " from a " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
//      } else {
//        message += "";
//      }
      if (severity.mustInterrupt()) {
        IntaveLogger.logger().error(message);
        throw new RuntimeException(message, throwable);
      } else {
        IntaveLogger.logger().info(message);
      }
    } finally {
      if (afterMethod != null) {
        try {
          long start = System.currentTimeMillis();
          afterMethod.invoke(test);
          long end = System.currentTimeMillis();
          long duration = end - start;
          if (duration > 100 && IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
            IntaveLogger.logger().warn("[debug] After method of test " + fullTestName + " took " + duration + "ms!");
          }
        } catch (Exception exception) {
          exception.printStackTrace();
        }
      }
    }
  }
}
