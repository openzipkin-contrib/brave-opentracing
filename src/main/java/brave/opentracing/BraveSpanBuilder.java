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

import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tag;
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
public class BraveSpanBuilder implements Tracer.SpanBuilder {
  final brave.Tracer tracer;
  final Map<String, String> tags = new LinkedHashMap<>();

  String operationName;
  long timestamp;
  int remotePort;
  BraveSpanContext reference;
  boolean ignoreActiveSpan = false;

  BraveSpanBuilder(brave.Tracer tracer, String operationName) {
    this.tracer = tracer;
    this.operationName = operationName;
  }

  @Override public BraveSpanBuilder asChildOf(SpanContext parent) {
    return addReference(References.CHILD_OF, parent);
  }

  @Override public BraveSpanBuilder asChildOf(Span parent) {
    return asChildOf(parent != null ? parent.context() : null);
  }

  @Override public BraveSpanBuilder addReference(String type, SpanContext context) {
    if (reference != null || context == null) return this;
    if (References.CHILD_OF.equals(type) || References.FOLLOWS_FROM.equals(type)) {
      this.reference = (BraveSpanContext) context;
    }
    return this;
  }

  @Override public BraveSpanBuilder withTag(String key, String value) {
    tags.put(key, value);
    return this;
  }

  @Override public BraveSpanBuilder withTag(String key, boolean value) {
    if (Tags.ERROR.getKey().equals(key) && !value) return this;
    return withTag(key, Boolean.toString(value));
  }

  @Override public BraveSpanBuilder withTag(String key, Number value) {
    if (Tags.PEER_PORT.getKey().equals(key)) {
      remotePort = value.intValue();
      return this;
    }
    return withTag(key, value.toString());
  }

  @Override public <T> BraveSpanBuilder withTag(Tag<T> tag, T value) {
    if (tag == null) throw new NullPointerException("tag == null");
    if (value == null) throw new NullPointerException("value == null");
    if (value instanceof String) return withTag(tag.getKey(), (String) value);
    if (value instanceof Number) return withTag(tag.getKey(), (Number) value);
    if (value instanceof Boolean) return withTag(tag.getKey(), (Boolean) value);
    throw new IllegalArgumentException("tag value not a string, number or boolean: " + value);
  }

  @Override public BraveSpanBuilder withStartTimestamp(long microseconds) {
    this.timestamp = microseconds;
    return this;
  }

  @Override public BraveSpanBuilder ignoreActiveSpan() {
    ignoreActiveSpan = true;
    return this;
  }

  @Override public BraveSpan start() {
    boolean server = Tags.SPAN_KIND_SERVER.equals(tags.get(Tags.SPAN_KIND.getKey()));

    // Check if active span should be established as CHILD_OF relationship
    if (reference == null && !ignoreActiveSpan) {
      brave.Span parent = tracer.currentSpan();
      if (parent != null) asChildOf(BraveSpanContext.create(parent.context()));
    }

    brave.Span span;
    TraceContext context;
    if (reference == null) {
      // adjust sampling decision, this reflects Zipkin's "before the fact" sampling policy
      // https://github.com/openzipkin/brave/tree/master/brave#sampling
      brave.Tracer scopedTracer = tracer;
      String sampling = tags.get(Tags.SAMPLING_PRIORITY.getKey());
      if (sampling != null) {
        try {
          Integer samplingPriority = Integer.valueOf(sampling);
          if (samplingPriority == 0) {
            scopedTracer = tracer.withSampler(Sampler.NEVER_SAMPLE);
          } else if (samplingPriority > 0) {
            scopedTracer = tracer.withSampler(Sampler.ALWAYS_SAMPLE);
          }
        } catch (NumberFormatException ex) {
          // ignore
        }
      }
      span = scopedTracer.newTrace();
    } else if ((context = reference.unwrap()) != null) {
      // Zipkin's default is to share a span ID between the client and the server in an RPC.
      // When we start a server span with a parent, we assume the "parent" is actually the
      // client on the other side of the RPC. Accordingly, we join that span instead of fork.
      span = server ? tracer.joinSpan(context) : tracer.newChild(context);
    } else {
      span = tracer.nextSpan(((BraveSpanContext.Incomplete) reference).extractionResult());
    }

    if (operationName != null) span.name(operationName);
    BraveSpan result = new BraveSpan(tracer, span);
    result.remotePort = remotePort;
    for (Map.Entry<String, String> tag : tags.entrySet()) {
      result.setTag(tag.getKey(), tag.getValue());
    }

    if (timestamp != 0) {
      span.start(timestamp);
    } else {
      span.start();
    }

    return result;
  }

  /* @Override deprecated 0.32 method: Intentionally no override to ensure 0.33 works! */
  @Deprecated public BraveSpan startManual() {
    throw new UnsupportedOperationException("Not supported in OpenTracing 0.33+");
  }

  /* @Override deprecated 0.32 method: Intentionally no override to ensure 0.33 works! */
  @Deprecated public BraveScope startActive(boolean finishSpanOnClose) {
    throw new UnsupportedOperationException("Not supported in OpenTracing 0.33+");
  }
}
