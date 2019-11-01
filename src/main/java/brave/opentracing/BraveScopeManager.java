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

import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

/** This integrates with Brave's {@link CurrentTraceContext}. */
public class BraveScopeManager implements ScopeManager {
  final Tracing tracing;
  final Tracer tracer;

  BraveScopeManager(Tracing tracing) {
    this.tracing = tracing;
    this.tracer = tracing.tracer();
  }

  @Override public BraveScope activate(Span span) {
    if (span == null) return null;
    if (!(span instanceof BraveSpan)) {
      throw new IllegalArgumentException(
          "Span must be an instance of brave.opentracing.BraveSpan, but was " + span.getClass());
    }
    return new BraveScope(tracer.withSpanInScope(((BraveSpan) span).delegate));
  }

  @Override public BraveSpan activeSpan() {
    brave.Span braveSpan = tracer.currentSpan();
    return braveSpan != null ? new BraveSpan(tracer, braveSpan) : null;
  }

  /* @Override deprecated 0.32 method: Intentionally no override to ensure 0.33 works! */
  @Deprecated public Scope active() {
    throw new UnsupportedOperationException("Not supported in OpenTracing 0.33+");
  }

  /* @Override deprecated 0.32 method: Intentionally no override to ensure 0.33 works! */
  @Deprecated public BraveScope activate(Span span, boolean finishSpanOnClose) {
    throw new UnsupportedOperationException("Not supported in OpenTracing 0.33+");
  }

  /** Attempts to get a span from the current api, falling back to brave's native one */
  /* @Override deprecated 0.32 method: Intentionally no override to ensure 0.33 works! */
  @Deprecated BraveSpan currentSpan() {
    throw new UnsupportedOperationException("Not supported in OpenTracing 0.33+");
  }
}
