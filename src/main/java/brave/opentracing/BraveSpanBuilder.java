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
import io.opentracing.tag.Tags;
import java.util.LinkedHashMap;
import java.util.Map;
import zipkin2.Endpoint;

import static brave.opentracing.BraveSpan.trySetKind;
import static brave.opentracing.BraveSpan.trySetPeer;

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
  private final Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();

  private String operationName;
  private long timestamp;
  private TraceContext parent;
  private boolean ignoreActiveSpan = false;

  BraveSpanBuilder(Tracer tracer, brave.Tracer braveTracer, String operationName) {
    this.tracer = tracer;
    this.braveTracer = braveTracer;
    this.operationName = operationName;
  }

  @Override public BraveSpanBuilder asChildOf(SpanContext parent) {
    return addReference(References.CHILD_OF, parent);
  }

  @Override public BraveSpanBuilder asChildOf(Span parent) {
    return asChildOf(parent.context());
  }

  @Override public BraveSpanBuilder addReference(String type, SpanContext context) {
    if (parent != null) {
      return this;
    }
    if (References.CHILD_OF.equals(type) || References.FOLLOWS_FROM.equals(type)) {
      this.parent = ((BraveSpanContext) context).unwrap();
    }
    return this;
  }

  @Override public BraveSpanBuilder withTag(String key, String value) {
    if (trySetPeer(remoteEndpoint, key, value)) return this;
    tags.put(key, value);
    return this;
  }

  @Override public BraveSpanBuilder withTag(String key, boolean value) {
    if (Tags.ERROR.getKey().equals(key) && !value) return this;
    return withTag(key, Boolean.toString(value));
  }

  @Override public BraveSpanBuilder withTag(String key, Number value) {
    if (trySetPeer(remoteEndpoint, key, value)) return this;
    return withTag(key, value.toString());
  }

  @Override public BraveSpanBuilder withStartTimestamp(long microseconds) {
    this.timestamp = microseconds;
    return this;
  }

  @Override public BraveSpan start() {
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

  @Override public BraveSpanBuilder ignoreActiveSpan() {
    ignoreActiveSpan = true;
    return this;
  }

  @Override public BraveSpan startManual() {
    boolean server = Tags.SPAN_KIND_SERVER.equals(tags.get(Tags.SPAN_KIND.getKey()));

    // Check if active span should be established as CHILD_OF relationship
    if (parent == null && !ignoreActiveSpan) {
      Scope parent = tracer.scopeManager().active();
      if (parent != null) {
        asChildOf(parent.span());
      }
    }

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
      String key = tag.getKey(), value = tag.getValue();
      if (trySetKind(span, key, value)) continue;
      span.tag(key, value);
    }

    if (timestamp != 0) {
      span.start(timestamp);
    } else {
      span.start();
    }

    return new BraveSpan(span, remoteEndpoint.build());
  }
}
