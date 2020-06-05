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

import brave.Span.Kind;
import brave.Tracer;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.internal.Nullable;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Iterator;
import java.util.Map;

/**
 * Holds the {@linkplain brave.Span} used by the underlying {@linkplain brave.Tracer}.
 *
 * <p>This type also includes hooks to integrate with the underlying {@linkplain brave.Tracer}. Ex
 * you can access the underlying span with {@link #unwrap}
 *
 * <p>Operations to add data to the span are ignored once {@link #finish()} or {@link
 * #finish(long)} are called.
 */
public final class BraveSpan implements Span {
  private final Tracer tracer;
  volatile BraveSpanContext context;
  /** Prevents late adding data to a span */
  volatile boolean finishCalled;
  /** Reference invalidated when sampling priority set to 0, which can happen on any thread */
  volatile brave.Span delegate;
  volatile String remoteIpV4, remoteIpV6;
  volatile int remotePort;

  // tracer is only needed because the sampling.priority flag is used as a sampling api
  BraveSpan(brave.Tracer tracer, brave.Span delegate) {
    this.tracer = tracer;
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
    this.context = BraveSpanContext.create(delegate.context());
  }

  /**
   * Returns wrapped {@linkplain brave.Span}
   */
  public final brave.Span unwrap() {
    return delegate;
  }

  @Override public BraveSpanContext context() {
    return context;
  }

  @Override public BraveSpan setTag(String key, String value) {
    if (finishCalled) return this;

    if (trySetPeer(delegate, key, value)) return this;
    Kind kind = trySetKind(key, value);
    if (kind != null) {
      delegate.kind(kind);
      context.kind = kind;
      return this;
    }
    delegate.tag(key, value);
    return this;
  }

  @Override public BraveSpan setTag(String key, boolean value) {
    if (finishCalled) return this;

    if (Tags.ERROR.getKey().equals(key) && !value) return this;
    return setTag(key, Boolean.toString(value));
  }

  /**
   * <em>Note:</em>If the key is {@linkplain Tags#SAMPLING_PRIORITY} and the value is zero, the
   * current span will be abandoned and future references to the {@link #context()} will be
   * unsampled. This does not affect the active span, nor does it affect any equivalent instances of
   * this object. This is a best efforts means to handle late sampling decisions.
   */
  @Override public BraveSpan setTag(String key, Number value) {
    if (finishCalled) return this;

    if (trySetPeer(key, value)) return this;

    // handle late sampling decision
    if (Tags.SAMPLING_PRIORITY.getKey().equals(key) && value.intValue() == 0) {
      delegate.abandon();
      // convert the span to no-op
      Kind kind = context.kind;
      delegate = tracer.toSpan(delegate.context().toBuilder().sampled(false).build());
      context = BraveSpanContext.create(delegate.context());
      context.kind = kind;
    }
    return setTag(key, value.toString());
  }

  @Override public <T> BraveSpan setTag(io.opentracing.tag.Tag<T> tag, T value) {
    // Strange there's a new api only to dispatch something that can be done as easily directly
    // eg instead of tag.set(span, value) this allows span.setTag(tag, value) (3 more characters!)
    // Would be nice to see documentation clarify why this was important enough to break api over.
    tag.set(this, value);
    return this;
  }

  @Override public BraveSpan log(Map<String, ?> fields) {
    if (finishCalled) return this;

    if (fields.isEmpty()) return this;
    return log(toAnnotation(fields));
  }

  @Override public BraveSpan log(long timestampMicroseconds, Map<String, ?> fields) {
    if (finishCalled) return this;

    if (fields.isEmpty()) return this;
    // in real life, do like zipkin-go-opentracing: "key1=value1 key2=value2"
    return log(timestampMicroseconds, toAnnotation(fields));
  }

  @Override public BraveSpan log(String event) {
    if (finishCalled) return this;

    delegate.annotate(event);
    return this;
  }

  @Override public BraveSpan log(long timestampMicroseconds, String event) {
    if (finishCalled) return this;

    delegate.annotate(timestampMicroseconds, event);
    return this;
  }

  /** This is a NOOP unless {@link BaggagePropagation} is in use */
  @Override public BraveSpan setBaggageItem(String key, String value) {
    BaggageField field = BaggageField.getByName(delegate.context(), key);
    if (field == null) return this;
    field.updateValue(delegate.context(), value);
    return this;
  }

  /** Returns null unless {@link BaggagePropagation} is in use */
  @Override public String getBaggageItem(String key) {
    BaggageField field = BaggageField.getByName(delegate.context(), key);
    if (field == null) return null;
    return field.getValue(delegate.context());
  }

  @Override public BraveSpan setOperationName(String operationName) {
    if (finishCalled) return this;

    delegate.name(operationName);
    return this;
  }

  @Override public void finish() {
    if (finishCalled) return;
    finishCalled = true;
    trySetRemoteIpAndPort();
    delegate.finish();
  }

  @Override public void finish(long finishMicros) {
    if (finishCalled) return;
    finishCalled = true;
    trySetRemoteIpAndPort();
    delegate.finish(finishMicros);
  }

  /**
   * Converts a map to a string of form: "key1=value1 key2=value2"
   */
  static String toAnnotation(Map<String, ?> fields) {
    // special-case the "event" field which is similar to the semantics of a zipkin annotation
    Object event = fields.get("event");
    if (event != null && fields.size() == 1) return event.toString();

    return joinOnEqualsSpace(fields);
  }

  static String joinOnEqualsSpace(Map<String, ?> fields) {
    if (fields.isEmpty()) return "";

    StringBuilder result = new StringBuilder();
    for (Iterator<? extends Map.Entry<String, ?>> i = fields.entrySet().iterator(); i.hasNext(); ) {
      Map.Entry<String, ?> next = i.next();
      result.append(next.getKey()).append('=').append(next.getValue());
      if (i.hasNext()) result.append(' ');
    }
    return result.toString();
  }

  @Nullable static Kind trySetKind(String key, String value) {
    if (!Tags.SPAN_KIND.getKey().equals(key)) return null;

    Kind kind;
    if (Tags.SPAN_KIND_CLIENT.equals(value)) {
      kind = Kind.CLIENT;
    } else if (Tags.SPAN_KIND_SERVER.equals(value)) {
      kind = Kind.SERVER;
    } else if (Tags.SPAN_KIND_PRODUCER.equals(value)) {
      kind = Kind.PRODUCER;
    } else if (Tags.SPAN_KIND_CONSUMER.equals(value)) {
      kind = Kind.CONSUMER;
    } else {
      return null;
    }
    return kind;
  }

  boolean trySetPeer(brave.Span span, String key, String value) {
    if (Tags.PEER_SERVICE.getKey().equals(key)) {
      span.remoteServiceName(value);
    } else if (Tags.PEER_HOST_IPV4.getKey().equals(key)) {
      remoteIpV4 = value;
    } else if (Tags.PEER_HOST_IPV6.getKey().equals(key)) {
      remoteIpV6 = value;
    } else {
      return false;
    }
    return true;
  }

  boolean trySetPeer(String key, Number value) {
    if (Tags.PEER_HOST_IPV4.getKey().equals(key)) {
      int ipv4 = value.intValue();
      remoteIpV4 = new StringBuilder()
          .append(ipv4 >> 24 & 0xff).append('.')
          .append(ipv4 >> 16 & 0xff).append('.')
          .append(ipv4 >> 8 & 0xff).append('.')
          .append(ipv4 & 0xff).toString();
    } else if (Tags.PEER_PORT.getKey().equals(key)) {
      remotePort = value.intValue();
    } else {
      return false;
    }
    return true;
  }

  void trySetRemoteIpAndPort() {
    if (remoteIpV4 != null) delegate.remoteIpAndPort(remoteIpV4, remotePort);
    if (remoteIpV6 != null) delegate.remoteIpAndPort(remoteIpV6, remotePort);
  }

  @Override public String toString() {
    return context.toString();
  }

  @Override public final boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof BraveSpan)) return false;
    return context.equals(((BraveSpan) o).context);
  }

  @Override public final int hashCode() {
    return context.hashCode();
  }
}
