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

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import zipkin2.Endpoint;

/**
 * Holds the {@linkplain brave.Span} used by the underlying {@linkplain brave.Tracer}.\
 *
 * <p>This type also includes hooks to integrate with the underlying {@linkplain brave.Tracer}. Ex
 * you can access the underlying span with {@link #unwrap}
 */
public final class BraveSpan implements Span {
  static final Endpoint EMPTY_ENDPOINT = Endpoint.newBuilder().build();

  private final brave.Span delegate;
  private final SpanContext context;
  private final Endpoint.Builder remoteEndpointBuilder;

  BraveSpan(brave.Span delegate, Endpoint remoteEndpoint) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
    this.remoteEndpointBuilder = remoteEndpoint.toBuilder(); // so that the builder can be reused
    this.context = BraveSpanContext.wrap(delegate.context());
  }

  /**
   * Returns wrapped {@linkplain brave.Span}
   */
  public final brave.Span unwrap() {
    return delegate;
  }

  @Override public SpanContext context() {
    return context;
  }

  @Override public Span setTag(String key, String value) {
    if (trySetPeer(remoteEndpointBuilder, key, value)) return this;
    if (trySetKind(delegate, key, value)) return this;
    delegate.tag(key, value);
    return this;
  }

  @Override public Span setTag(String key, boolean value) {
    if (Tags.ERROR.getKey().equals(key) && !value) return this;
    return setTag(key, Boolean.toString(value));
  }

  @Override public Span setTag(String key, Number value) {
    if (trySetPeer(remoteEndpointBuilder, key, value)) return this;
    return setTag(key, value.toString());
  }

  @Override public Span log(Map<String, ?> fields) {
    if (fields.isEmpty()) return this;
    // in real life, do like zipkin-go-opentracing: "key1=value1 key2=value2"
    return log(toAnnotation(fields));
  }

  @Override public Span log(long timestampMicroseconds, Map<String, ?> fields) {
    if (fields.isEmpty()) return this;
    // in real life, do like zipkin-go-opentracing: "key1=value1 key2=value2"
    return log(timestampMicroseconds, toAnnotation(fields));
  }

  @Override public Span log(String event) {
    delegate.annotate(event);
    return this;
  }

  @Override public Span log(long timestampMicroseconds, String event) {
    delegate.annotate(timestampMicroseconds, event);
    return this;
  }

  /**
   * This is a NOOP as neither <a href="https://github.com/openzipkin/b3-propagation">B3</a> nor
   * Brave include baggage support.
   */
  // OpenTracing could one day define a way to plug-in arbitrary baggage handling similar to how
  // it has feature-specific apis like active-span
  @Override public Span setBaggageItem(String key, String value) {
    return this;
  }

  /**
   * Returns null as neither <a href="https://github.com/openzipkin/b3-propagation">B3</a> nor Brave
   * include baggage support.
   */
  // OpenTracing could one day define a way to plug-in arbitrary baggage handling similar to how
  // it has feature-specific apis like active-span
  @Override public String getBaggageItem(String key) {
    return null;
  }

  @Override public Span setOperationName(String operationName) {
    delegate.name(operationName);
    return this;
  }

  @Override public void finish() {
    trySetRemoteEndpoint();
    delegate.finish();
  }

  @Override public void finish(long finishMicros) {
    trySetRemoteEndpoint();
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
    for (Iterator<? extends Entry<String, ?>> i = fields.entrySet().iterator(); i.hasNext(); ) {
      Map.Entry<String, ?> next = i.next();
      result.append(next.getKey()).append('=').append(next.getValue());
      if (i.hasNext()) result.append(' ');
    }
    return result.toString();
  }

  void trySetRemoteEndpoint() {
    Endpoint remoteEndpoint = remoteEndpointBuilder.build();
    if (!remoteEndpoint.equals(EMPTY_ENDPOINT)) {
      delegate.remoteEndpoint(remoteEndpoint);
    }
  }

  static boolean trySetKind(brave.Span span, String key, String value) {
    if (!Tags.SPAN_KIND.getKey().equals(key)) return false;

    if (Tags.SPAN_KIND_CLIENT.equals(value)) {
      span.kind(brave.Span.Kind.CLIENT);
    } else if (Tags.SPAN_KIND_SERVER.equals(value)) {
      span.kind(brave.Span.Kind.SERVER);
    } else if (Tags.SPAN_KIND_CLIENT.equals(value)) {
      span.kind(brave.Span.Kind.CLIENT);
    } else if (Tags.SPAN_KIND_PRODUCER.equals(value)) {
      span.kind(brave.Span.Kind.PRODUCER);
    } else if (Tags.SPAN_KIND_CONSUMER.equals(value)) {
      span.kind(brave.Span.Kind.CONSUMER);
    } else {
      return false;
    }
    return true;
  }

  static boolean trySetPeer(Endpoint.Builder remoteEndpoint, String key, String value) {
    if (Tags.PEER_SERVICE.getKey().equals(key)) {
      remoteEndpoint.serviceName(value);
    } else if (Tags.PEER_HOST_IPV4.getKey().equals(key) ||
        Tags.PEER_HOST_IPV6.getKey().equals(key)) {
      remoteEndpoint.ip(value);
    } else {
      return false;
    }
    return true;
  }

  static boolean trySetPeer(Endpoint.Builder remoteEndpoint, String key, Number value) {
    if (Tags.PEER_HOST_IPV4.getKey().equals(key)) {
      int ipv4 = value.intValue();
      remoteEndpoint.ip(new StringBuilder()
          .append(ipv4 >> 24 & 0xff).append('.')
          .append(ipv4 >> 16 & 0xff).append('.')
          .append(ipv4 >> 8 & 0xff).append('.')
          .append(ipv4 & 0xff).toString());
    } else if (Tags.PEER_PORT.getKey().equals(key)) {
      remoteEndpoint.port(value.intValue());
    } else {
      return false;
    }
    return true;
  }
}
