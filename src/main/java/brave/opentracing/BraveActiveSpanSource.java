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
import brave.Tracer.SpanInScope;
import brave.Tracing;
import io.opentracing.ActiveSpan;
import io.opentracing.ActiveSpanSource;
import io.opentracing.Span;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the active Brave span.
 *
 * Note that it is important to use the OpenTracing API exclusively to activate spans. If you fail
 * to do this, querying for the active span using the OpenTracing API will throw an exception.
 */
final class BraveActiveSpanSource implements ActiveSpanSource {
  private final Map<Long, BraveActiveSpan> activeSpans = new ConcurrentHashMap<>();
  private final Tracer tracer;

  BraveActiveSpanSource(Tracing brave4) {
    tracer = brave4.tracer();
  }

  @Override
  public ActiveSpan activeSpan() {
    brave.Span span = tracer.currentSpan();
    if (span == null) {
      return null;
    }

    return getOrEstablishActiveSpan(span);
  }

  @Override
  public BraveActiveSpan makeActive(Span span) {
    if (span == null) return null;
    if (!(span instanceof BraveSpan)) throw new IllegalArgumentException(
        "Span must be an instance of brave.opentracing.BraveSpan, but was " + span.getClass());

    BraveSpan wrappedSpan = (BraveSpan) span;
    brave.Span rawSpan = wrappedSpan.unwrap();
    return getOrEstablishActiveSpan(rawSpan);
  }

  private BraveActiveSpan getOrEstablishActiveSpan(brave.Span span) {
    long spanId = span.context().spanId();
    BraveActiveSpan braveActiveSpan = activeSpans.get(spanId);
    if (braveActiveSpan == null) {
      braveActiveSpan = new BraveActiveSpan(this, tracer.withSpanInScope(span), BraveSpan.wrap(span));
      activeSpans.put(spanId, braveActiveSpan);
    }
    return braveActiveSpan;
  }

  void deregisterSpan(brave.Span span) {
    activeSpans.remove(span.context().spanId());
  }
}
