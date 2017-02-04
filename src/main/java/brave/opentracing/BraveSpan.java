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

import java.util.Iterator;
import java.util.Map;

/**
 * Holds the {@linkplain brave.Span} used by the underlying {@linkplain brave.Tracer}.\
 *
 * <p>This type also includes hooks to integrate with the underlying {@linkplain brave.Tracer}.
 * Ex you can access the underlying span with {@link #unwrap}
 */
public final class BraveSpan implements Span {

    /**
     * Converts an existing {@linkplain brave.Span} for use in OpenTracing apis
     */
    static BraveSpan wrap(brave.Span span) {
        if (span == null) throw new NullPointerException("span == null");
        return new BraveSpan(span);
    }

    /**
     * Converts an existing {@linkplain brave.Span} for use in OpenTracing apis
     */
    public final brave.Span unwrap() {
        return delegate;
    }

    private final brave.Span delegate;
    private final SpanContext context;

    private BraveSpan(brave.Span delegate) {
        this.delegate = delegate;
        this.context = BraveSpanContext.wrap(delegate.context());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpanContext context() {
        return context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finish() {
        delegate.finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finish(long finishMicros) {
        delegate.finish(finishMicros);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        delegate.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span setTag(String key, String value) {
        delegate.tag(key, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span setTag(String key, boolean value) {
        return setTag(key, Boolean.toString(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span setTag(String key, Number value) {
        return setTag(key, value.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(Map<String, ?> fields) {
        if (fields.isEmpty()) return this;
        // in real life, do like zipkin-go-opentracing: "key1=value1 key2=value2"
        return log(toAnnotation(fields));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        if (fields.isEmpty()) return this;
        // in real life, do like zipkin-go-opentracing: "key1=value1 key2=value2"
        return log(timestampMicroseconds, toAnnotation(fields));
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(String event) {
        delegate.annotate(event);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(long timestampMicroseconds, String event) {
        delegate.annotate(timestampMicroseconds, event);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(String eventName, Object ignored) {
        return log(eventName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(long timestampMicroseconds, String eventName, Object ignored) {
        return log(timestampMicroseconds, eventName);
    }

    /**
     * This is a NOOP as neither <a href="https://github.com/openzipkin/b3-propagation">B3</a>
     * nor Brave include baggage support.
     */
    // OpenTracing could one day define a way to plug-in arbitrary baggage handling similar to how
    // it has feature-specific apis like active-span
    @Override
    public Span setBaggageItem(String key, String value) {
        // brave does not support baggage
        return this;
    }

    /**
     * Returns null as neither <a href="https://github.com/openzipkin/b3-propagation">B3</a>
     * nor Brave include baggage support.
     */
    // OpenTracing could one day define a way to plug-in arbitrary baggage handling similar to how
    // it has feature-specific apis like active-span
    @Override
    public String getBaggageItem(String key) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span setOperationName(String operationName) {
        delegate.name(operationName);
        return this;
    }
}
