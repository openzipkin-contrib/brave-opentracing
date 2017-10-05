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

import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.tag.Tags;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uses by the underlying {@linkplain brave.Tracer} to create a {@linkplain BraveSpan} wrapped
 * {@linkplain brave.Span}
 *
 * <p>Indicate {@link Tags#SPAN_KIND} before calling {@link #start()} to ensure RPC spans are
 * identified properly.
 *
 * <p>Brave does not support multiple parents so this has been implemented to use the first parent
 * defined.
 */
public final class BraveSpanBuilder implements Tracer.SpanBuilder {

  private final Tracer tracer;
  private final brave.Tracer braveTracer;
  private final Map<String, String> tags = new LinkedHashMap<>();

  private String operationName;
  private long timestamp;
  private TraceContext parent;
  private boolean ignoreActiveSpan = false;

  BraveSpanBuilder(Tracer tracer, brave.Tracer braveTracer, String operationName) {
    this.tracer = tracer;
    this.braveTracer = braveTracer;
    this.operationName = operationName;
  }

  @Override public Tracer.SpanBuilder asChildOf(SpanContext parent) {
    return addReference(References.CHILD_OF, parent);
  }

  @Override public Tracer.SpanBuilder asChildOf(Span parent) {
    return asChildOf(parent.context());
  }

  @Override public Tracer.SpanBuilder addReference(String type, SpanContext context) {
    if (parent != null) {
      return this;
    }
    if (References.CHILD_OF.equals(type) || References.FOLLOWS_FROM.equals(type)) {
      this.parent = ((BraveSpanContext) context).unwrap();
    }
    return this;
  }

  @Override public Tracer.SpanBuilder withTag(String key, String value) {
    tags.put(key, value);
    return this;
  }

  @Override public Tracer.SpanBuilder withTag(String key, boolean value) {
    return withTag(key, Boolean.toString(value));
  }

  @Override public Tracer.SpanBuilder withTag(String key, Number value) {
    return withTag(key, value.toString());
  }

  @Override public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
    this.timestamp = microseconds;
    return this;
  }

  @Override public Span start() {
    return startManual();
  }

  @Override public Scope startActive() {
    return startActive(true);
  }

  @Override public Scope startActive(boolean finishSpanOnClose) {
    if (!ignoreActiveSpan) {
      Scope parent = tracer.scopeManager().active();
      if (parent != null) {
        asChildOf(parent.span());
      }
    }
    return tracer.scopeManager().activate(startManual(), finishSpanOnClose);
  }

  @Override public SpanBuilder ignoreActiveSpan() {
    ignoreActiveSpan = true;
    return this;
  }

  @Override public BraveSpan startManual() {
    boolean server = Tags.SPAN_KIND_SERVER.equals(tags.get(Tags.SPAN_KIND.getKey()));

    brave.Span span;
    if (parent == null) {
      // adjust sampling decision, this reflects Zipkin's "before the fact" sampling policy
      // https://github.com/openzipkin/brave/tree/master/brave#sampling
      SamplingFlags samplingFlags = SamplingFlags.EMPTY;
      String sampling = tags.get(Tags.SAMPLING_PRIORITY.getKey());
      if (sampling != null) {
        try {
          Integer samplingPriority = Integer.valueOf(sampling);
          if (samplingPriority == 0) {
            samplingFlags = SamplingFlags.NOT_SAMPLED;
          } else if (samplingPriority > 0) {
            samplingFlags = SamplingFlags.SAMPLED;
          }
        } catch (NumberFormatException ex) {
          // ignore
        }
      }
      span = braveTracer.newTrace(samplingFlags);
    } else if (server && parent.shared()) {
      // Zipkin's default is to share a span ID between the client and the server in an RPC.
      // When we start a server span with a parent, we assume the "parent" is actually the
      // client on the other side of the RPC. Accordingly, we join that span instead of fork.
      span = braveTracer.joinSpan(parent);
    } else {
      span = braveTracer.newChild(parent);
    }

    if (operationName != null) span.name(operationName);
    for (Map.Entry<String, String> tag : tags.entrySet()) {
      span.tag(tag.getKey(), tag.getValue());

      if (Tags.SPAN_KIND.getKey().equals(tag.getKey()) && Tags.SPAN_KIND_CLIENT.equals(
          tag.getValue())) {
        span.kind(brave.Span.Kind.CLIENT);
      } else if (server) {
        span.kind(brave.Span.Kind.SERVER);
      }
    }
    brave.Span result;
    if (timestamp != 0) {
      result = span.start(timestamp);
    } else {
      result = span.start();
    }
    return BraveSpan.wrap(result);
  }
}
