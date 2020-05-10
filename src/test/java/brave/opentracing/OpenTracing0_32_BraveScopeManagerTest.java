/*
 * Copyright 2016-2020 The OpenZipkin Authors
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
import brave.propagation.StrictCurrentTraceContext;
import brave.test.TestSpanHandler;
import io.opentracing.Scope;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenTracing0_32_BraveScopeManagerTest {
  StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  TestSpanHandler spans = new TestSpanHandler();
  Tracing brave = Tracing.newBuilder()
      .currentTraceContext(currentTraceContext).addSpanHandler(spans).build();

  BraveTracer opentracing = BraveTracer.create(brave);

  @After public void clear() {
    brave.close();
    currentTraceContext.close();
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
          .isEqualTo(spanInScope.context());
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
}
