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

import brave.ScopedSpan;
import brave.Tracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import io.opentracing.Scope;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenTracing0_33_BraveScopeManagerTest {

  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing brave = Tracing.newBuilder()
      .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(StrictScopeDecorator.create())
          .build())
      .spanReporter(spans::add)
      .build();
  BraveTracer opentracing = BraveTracer.create(brave);

  @After public void clear() {
    brave.close();
  }

  @Test public void activate() {
    BraveSpan span = opentracing.buildSpan("spanA").start();

    try (Scope scopeA = opentracing.scopeManager().activate(span)) {
      assertThat(opentracing.scopeManager().activeSpan().context().unwrap())
          .isEqualTo(span.context().unwrap());
    }

    assertThat(opentracing.scopeManager().activeSpan())
        .isNull();
  }

  /** This ensures downstream code using OpenTracing api can see Brave's scope */
  @Test public void activeSpan_bridgesNormalBrave() {
    ScopedSpan spanInScope = brave.tracer().startScopedSpan("spanA");
    try {
      assertThat(opentracing.scopeManager().activeSpan())
          .extracting("delegate.context")
          .containsExactly(spanInScope.context());
    } finally {
      spanInScope.finish();
    }
  }

  @Test public void activate_nested() {
    BraveSpan spanA = opentracing.buildSpan("spanA").start();
    BraveSpan spanB = opentracing.buildSpan("spanB").start();

    try (Scope scopeA = opentracing.scopeManager().activate(spanA)) {
      try (Scope scopeB = opentracing.scopeManager().activate(spanB)) {
        assertThat(opentracing.scopeManager().activeSpan().context().unwrap())
            .isEqualTo(spanB.context().unwrap());
      }

      assertThat(opentracing.scopeManager().activeSpan().context().unwrap())
          .isEqualTo(spanA.context().unwrap());
    }
  }
}
