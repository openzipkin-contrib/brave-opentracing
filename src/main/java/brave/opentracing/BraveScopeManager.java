/**
 * Copyright 2016-2017 The OpenZipkin Authors
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
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the active Brave span.
 *
 * Note that it is important to use the OpenTracing API exclusively to activate spans. If you fail
 * to do this, querying for the active span using the OpenTracing API will throw an exception.
 */
final class BraveScopeManager implements ScopeManager {
  private final Map<Long, BraveScope> activeSpans = new ConcurrentHashMap<>();
  private final Tracer tracer;

  BraveScopeManager(Tracing brave4) {
    tracer = brave4.tracer();
  }

  @Override public Scope active() {
    brave.Span span = tracer.currentSpan();
    if (span == null) {
      return null;
    }

    return getOrEstablishActiveSpan(span, false);
  }

  @Override public BraveScope activate(Span span) {
    return activate(span, true);
  }

  @Override public BraveScope activate(Span span, boolean finishSpanOnClose) {
    if (span == null) return null;
    if (!(span instanceof BraveSpan)) {
      throw new IllegalArgumentException(
          "Span must be an instance of brave.opentracing.BraveSpan, but was " + span.getClass());
    }

    BraveSpan wrappedSpan = (BraveSpan) span;
    brave.Span rawSpan = wrappedSpan.unwrap();
    return getOrEstablishActiveSpan(rawSpan, finishSpanOnClose);
  }

  private BraveScope getOrEstablishActiveSpan(brave.Span span, boolean finishSpanOnClose) {
    long spanId = span.context().spanId();
    BraveScope braveScope = activeSpans.get(spanId);
    if (braveScope == null) {
      braveScope = new BraveScope(this, tracer.withSpanInScope(span), BraveSpan.wrap(span),
          finishSpanOnClose);
      activeSpans.put(spanId, braveScope);
    }
    return braveScope;
  }

  void deregisterSpan(brave.Span span) {
    activeSpans.remove(span.context().spanId());
  }
}
