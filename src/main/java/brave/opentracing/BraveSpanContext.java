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

import brave.propagation.TraceContext;
import io.opentracing.SpanContext;
import java.util.Collections;
import java.util.Map;

/**
 * Holds the {@linkplain TraceContext} used by the underlying {@linkplain brave.Tracer}. An {@link
 * TraceContext#sampled() unsampled} context results in a {@link opentracing...NoopSpan}.
 *
 * <p>This type also includes hooks to integrate with the underlying {@linkplain brave.Tracer}. Ex
 * you can access the underlying trace context with {@link #unwrap}
 */
public final class BraveSpanContext implements SpanContext {

  private final TraceContext traceContext;

  static BraveSpanContext wrap(TraceContext traceContext) {
    return new BraveSpanContext(traceContext);
  }

  /**
   * Returns the underlying trace context for use in Brave apis
   */
  public TraceContext unwrap() {
    return traceContext;
  }

  private BraveSpanContext(TraceContext traceContext) {
    this.traceContext = traceContext;
  }

  /**
   * Returns empty as neither <a href="https://github.com/openzipkin/b3-propagation">B3</a> nor
   * Brave include baggage support.
   */
  @Override public Iterable<Map.Entry<String, String>> baggageItems() {
    // brave doesn't support baggage
    return Collections.EMPTY_SET;
  }
}
