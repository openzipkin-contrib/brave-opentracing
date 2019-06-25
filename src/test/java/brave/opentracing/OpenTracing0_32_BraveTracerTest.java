/*
 * Copyright 2016-2019 The OpenZipkin Authors
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

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import io.opentracing.Scope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class OpenTracing0_32_BraveTracerTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing brave = Tracing.newBuilder()
      .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(StrictScopeDecorator.create())
          .build())
      .propagationFactory(ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
          .addPrefixedFields("baggage-", Arrays.asList("country-code", "user-id"))
          .build())
      .spanReporter(spans::add)
      .build();
  BraveTracer opentracing = BraveTracer.create(brave);

  @After public void clear() {
    brave.close();
  }

  @Test public void versionIsCorrect() {
    assertThat(OpenTracingVersion.get())
        .isInstanceOf(OpenTracingVersion.v0_32.class);
  }

  /** OpenTracing span implements auto-closeable, and implies reporting on close */
  @Test public void startActive_autoCloseOnTryFinally() {
    try (Scope scope = opentracing.buildSpan("foo").startActive(true)) {
    }

    assertThat(spans)
        .hasSize(1);
  }

  @Test public void startActive_autoCloseOnTryFinally_doesntReportTwice() {
    try (Scope scope = opentracing.buildSpan("foo").startActive(true)) {
      opentracing.activeSpan().finish(); // user closes and also auto-close closes
    }

    assertThat(spans)
        .hasSize(1);
  }

  @Test public void startActive_autoCloseOnTryFinally_dontClose() {
    try (Scope scope = opentracing.buildSpan("foo").startActive(false)) {
    }

    assertThat(spans)
        .isEmpty();
  }

  @Test public void subsequentChildrenNestProperly_OTStyle() {
    // this test is semantically identical to subsequentChildrenNestProperly_BraveStyle, but uses
    // the OpenTracingAPI instead of the Brave API.

    Long idOfSpanA;
    Long shouldBeIdOfSpanA;
    Long idOfSpanB;
    Long shouldBeIdOfSpanB;
    Long parentIdOfSpanB;
    Long parentIdOfSpanC;

    try (Scope scopeA = opentracing.buildSpan("spanA").startActive(false)) {
      idOfSpanA = brave.currentTraceContext().get().spanId();
      try (Scope scopeB = opentracing.buildSpan("spanB").startActive(false)) {
        idOfSpanB = brave.currentTraceContext().get().spanId();
        parentIdOfSpanB = brave.currentTraceContext().get().parentId();
        shouldBeIdOfSpanB = brave.currentTraceContext().get().spanId();
      }
      shouldBeIdOfSpanA = brave.currentTraceContext().get().spanId();
      try (Scope scopeC = opentracing.buildSpan("spanC").startActive(false)) {
        parentIdOfSpanC = brave.currentTraceContext().get().parentId();
      }
    }

    assertEquals("SpanA should have been active again after closing B", idOfSpanA,
        shouldBeIdOfSpanA);
    assertEquals("SpanB should have been active prior to its closure", idOfSpanB,
        shouldBeIdOfSpanB);
    assertEquals("SpanB's parent should be SpanA", idOfSpanA, parentIdOfSpanB);
    assertEquals("SpanC's parent should be SpanA", idOfSpanA, parentIdOfSpanC);
  }

  @Test public void implicitParentFromSpanManager_startActive() {
    try (BraveScope scopeA = opentracing.buildSpan("spanA").startActive(true)) {
      try (BraveScope scopeB = opentracing.buildSpan("spanB").startActive(true)) {
        TraceContext current = brave.currentTraceContext().get();
        assertThat(scopeB.span().context().unwrap().parentId())
            .isEqualTo(scopeA.span().context().unwrap().spanId());
      }
    }
  }

  @Test public void implicitParentFromSpanManager_start() {
    try (Scope scopeA = opentracing.buildSpan("spanA").startActive(true)) {
      BraveSpan span = opentracing.buildSpan("spanB").start();
      assertThat(span.unwrap().context().parentId())
          .isEqualTo(brave.currentTraceContext().get().spanId());
    }
  }

  @Test public void implicitParentFromSpanManager_startActive_ignoreActiveSpan() {
    try (Scope scopeA = opentracing.buildSpan("spanA").startActive(true)) {
      try (Scope scopeB = opentracing.buildSpan("spanA")
          .ignoreActiveSpan().startActive(true)) {
        assertThat(brave.currentTraceContext().get().parentId())
            .isNull(); // new trace
      }
    }
  }

  @Test public void implicitParentFromSpanManager_start_ignoreActiveSpan() {
    try (Scope scopeA = opentracing.buildSpan("spanA").startActive(true)) {
      BraveSpan span = opentracing.buildSpan("spanB")
          .ignoreActiveSpan().start();
      assertThat(span.unwrap().context().parentId())
          .isNull(); // new trace
    }
  }
}
