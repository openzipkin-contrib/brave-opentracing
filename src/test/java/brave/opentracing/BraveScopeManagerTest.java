/**
 * Copyright 2016-2018 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.opentracing;

import brave.ScopedSpan;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.StrictCurrentTraceContext;
import io.opentracing.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BraveScopeManagerTest {

  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing brave = Tracing.newBuilder()
      .currentTraceContext(CurrentTraceContext.Default.create())
      .spanReporter(spans::add)
      .build();

  BraveTracer opentracing = BraveTracer.create(brave);

  static List<LogRecord> LogRecordBuffer = new ArrayList<>();

  static Handler handler = new Handler() {
    @Override public void publish(LogRecord record) {
      LogRecordBuffer.add(record);
    }

    @Override public void flush() {

    }

    @Override public void close() throws SecurityException {

    }
  };

  @BeforeClass public static void setup() {
    Logger logger = Logger.getLogger(BraveScopeManager.class.getName());
    logger.setLevel(Level.FINE);
    logger.addHandler(handler);
  }

  @Test public void scopeManagerActive() {
    BraveSpan span = opentracing.buildSpan("spanA").start();

    try (Scope scopeA = opentracing.scopeManager().activate(span, false)) {
      assertThat(opentracing.scopeManager().active().span())
          .isEqualTo(span);
      //Call again to ensure the ThreadLocal cache works
      assertThat(opentracing.scopeManager().active().span())
          .isEqualTo(span);
    }

    assertThat(opentracing.scopeManager().active())
        .isNull();
  }

  /** This ensures downstream code using OpenTracing api can see Brave's scope */
  @Test public void scopeManagerActive_bridgesNormalBrave() {
    ScopedSpan spanInScope = brave.tracer().startScopedSpan("spanA");
    try {
      assertThat(opentracing.scopeManager().active().span())
          .extracting("delegate.context")
          .containsExactly(spanInScope.context());
    } finally {
      spanInScope.finish();
    }
  }

  @Test public void scopeManagerNested() {
    BraveSpan spanA = opentracing.buildSpan("spanA").start();
    BraveSpan spanB = opentracing.buildSpan("spanB").start();

    try (Scope scopeA = opentracing.scopeManager().activate(spanA, false)) {
      try (Scope scopeB = opentracing.scopeManager().activate(spanB, false)) {
        assertThat(opentracing.scopeManager().active().span())
            .isEqualTo(spanB);
      }

      assertThat(opentracing.scopeManager().active().span())
          .isEqualTo(spanA);
    }
  }

  @Test public void scopeManagerActiveClose() {
    BraveSpan spanA = opentracing.buildSpan("spanA").start();
    try (Scope scopeA = opentracing.scopeManager().activate(spanA, false)) {
      Scope scopeB = opentracing.scopeManager().active();

      scopeB.close();
    }
  }

  @Test public void scopeManagerActiveCloseMismatch() {
    BraveSpan spanA = opentracing.buildSpan("spanA").start();
    BraveSpan spanB = opentracing.buildSpan("spanB").start();

    Scope scopeA = opentracing.scopeManager().activate(spanA, false);
    assertThat(opentracing.scopeManager().active().span()).isEqualTo(spanA);
    Scope scopeB = opentracing.scopeManager().activate(spanB, false);
    assertThat(opentracing.scopeManager().active().span()).isEqualTo(spanB);

    assertThat(opentracing.scopeManager().currentScopes.get().size()).isEqualTo(2);
    scopeA.close();//close parent first

    assertThat(LogRecordBuffer.size()).isEqualTo(1);
    LogRecord log = LogRecordBuffer.get(0);
    assertThat(log.getLevel()).isEqualTo(Level.WARNING);
    assertThat(log.getMessage()).startsWith("Child scope not closed when closing");
    assertThat(log.getThrown().getMessage()).startsWith(
        "Thread " + Thread.currentThread().getName() + " opened BraveScope for");
    assertThat(log.getThrown()).isEqualTo(
        opentracing.scopeManager().currentScopes.get().peekFirst().tracker.caller);

    assertThat(opentracing.scopeManager().currentScopes.get().size()).isEqualTo(1);
    assertThat(opentracing.scopeManager().active().span()).isEqualTo(spanB);
    scopeB.close();
    assertThat(opentracing.scopeManager().currentScopes.get().size()).isEqualTo(0);

    //scopeB.previous is A which is still hold
    assertThat(opentracing.scopeManager().active().span()).isNotEqualTo(spanA);
    assertThat(opentracing.scopeManager().active().span().toString()).isEqualTo(spanA.toString());

    scopeA.close(); // close again to ensure thread local state is correct.
    assertThat(opentracing.scopeManager().active()).isNull();
  }

  @Test public void scopeManagerActiveCloseMismatchSkipLogging() {

    Logger logger = Logger.getLogger(BraveScopeManager.class.getName());
    logger.setLevel(Level.SEVERE);

    BraveSpan spanA = opentracing.buildSpan("spanA").start();
    BraveSpan spanB = opentracing.buildSpan("spanB").start();

    Scope scopeA = opentracing.scopeManager().activate(spanA, false);
    assertThat(opentracing.scopeManager().active().span()).isEqualTo(spanA);
    Scope scopeB = opentracing.scopeManager().activate(spanB, false);
    assertThat(opentracing.scopeManager().active().span()).isEqualTo(spanB);

    assertThat(opentracing.scopeManager().currentScopes.get().size()).isEqualTo(2);
    scopeA.close();//close parent first

    assertThat(LogRecordBuffer.size()).isEqualTo(0);

    assertThat(opentracing.scopeManager().currentScopes.get().size()).isEqualTo(1);
    assertThat(opentracing.scopeManager().active().span()).isEqualTo(spanB);
    scopeB.close();
    assertThat(opentracing.scopeManager().currentScopes.get().size()).isEqualTo(0);

    //scopeB.previous is A which is still hold
    assertThat(opentracing.scopeManager().active().span()).isNotEqualTo(spanA);
    assertThat(opentracing.scopeManager().active().span().toString()).isEqualTo(spanA.toString());

    scopeA.close(); // close again to ensure thread local state is correct.
    assertThat(opentracing.scopeManager().active()).isNull();

    logger.setLevel(Level.FINE);
  }

  @Test public void scopeManagerActiveCloseDifferentThread() throws InterruptedException {
    BraveSpan spanA = opentracing.buildSpan("spanA").start();

    BraveScope scopeA = opentracing.scopeManager().activate(spanA, false);
    CountDownLatch count = new CountDownLatch(1);

    new Thread(() -> {
      scopeA.close();
      count.countDown();
    }).start();

    count.await();

    assertThat(LogRecordBuffer.size()).isEqualTo(1);
    LogRecord log = LogRecordBuffer.get(0);

    String threadName = Thread.currentThread().getName();
    assertThat(log.getLevel()).isEqualTo(Level.WARNING);
    assertThat(log.getMessage()).startsWith("Closing scope in wrong thread");
    assertThat(log.getThrown().getMessage()).startsWith(
        "Thread " + threadName + " opened BraveScope for");
    assertThat(log.getThrown()).isEqualTo(scopeA.tracker.caller);

    assertThat(opentracing.scopeManager().active().span()).isEqualTo(spanA);

    scopeA.close();//avoid thread local leak
    assertThat(opentracing.scopeManager().active()).isNull();
  }

  @After public void clear() {
    LogRecordBuffer.clear();
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }
}
