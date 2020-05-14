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

import brave.Span;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import java.util.Map;

/**
 * Holds the {@linkplain TraceContext} used by the underlying {@linkplain brave.Tracer}, or an {link
 * TraceContextOrSamplingFlags extraction result if an incoming context}. An {@link
 * TraceContext#sampled() unsampled} context results in a {@link Span#isNoop() noop span}.
 *
 * <p>This type also includes hooks to integrate with the underlying {@linkplain brave.Tracer}. Ex
 * you can access the underlying trace context with {@link #unwrap}
 */
public abstract class BraveSpanContext implements SpanContext {
  /**
   * Returns the underlying trace context for use in Brave apis, or null if this object does not
   * represent a span.
   *
   * <p>When a span context is returned from {@link BraveSpan#context()}, there's no ambiguity. It
   * represents the current span. However, a span context can be in an intermediate state when
   * extracted from headers. In other words, unwrap might not have a {@link TraceContext} to
   * return.
   *
   * <p>Why? {@link BraveTracer#extract(Format, Object) Extraction from headers} can return partial
   * info. For example, in Amazon Web Services, you may be suggested just a trace ID. In other
   * cases, you might just inherit baggage or a sampling hint.
   */
  public abstract TraceContext unwrap();

  /** Returns empty unless {@link BaggagePropagation} is in use */
  @Override public abstract Iterable<Map.Entry<String, String>> baggageItems();

  static BraveSpanContext create(TraceContext context) {
    return new Complete(context);
  }

  static BraveSpanContext create(TraceContextOrSamplingFlags extractionResult) {
    return extractionResult.context() != null
        ? new BraveSpanContext.Complete(extractionResult.context())
        : new BraveSpanContext.Incomplete(extractionResult);
  }

  static final class Complete extends BraveSpanContext {
    final TraceContext context;

    Complete(TraceContext context) {
      this.context = context;
    }

    @Override public TraceContext unwrap() {
      return context;
    }

    // notice: no sampling or parent span ID here!
    @Override public String toTraceId() {
      return context.traceIdString();
    }

    @Override public String toSpanId() {
      return context.spanIdString();
    }

    @Override public Iterable<Map.Entry<String, String>> baggageItems() {
      return BaggageField.getAllValues(context).entrySet();
    }
  }

  static final class Incomplete extends BraveSpanContext {
    final TraceContextOrSamplingFlags extractionResult;

    Incomplete(TraceContextOrSamplingFlags extractionResult) {
      this.extractionResult = extractionResult;
    }

    TraceContextOrSamplingFlags extractionResult() { // temporarily hidden
      return extractionResult;
    }

    @Override public TraceContext unwrap() {
      return extractionResult.context();
    }

    // notice: no sampling or parent span ID here!
    @Override public String toTraceId() {
      TraceContext context = extractionResult.context();
      return context != null ? context.traceIdString() : null;
    }

    @Override public String toSpanId() {
      TraceContext context = extractionResult.context();
      return context != null ? context.spanIdString() : null;
    }

    /** Returns empty unless {@link BaggagePropagation} is in use */
    @Override public Iterable<Map.Entry<String, String>> baggageItems() {
      return BaggageField.getAllValues(extractionResult).entrySet();
    }
  }

  volatile Span.Kind kind;

  BraveSpanContext() {
  }
}
