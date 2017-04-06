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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import brave.propagation.TraceContext;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

/**
 * Uses by the underlying {@linkplain brave.Tracer} to create a {@linkplain BraveSpan} wrapped {@linkplain brave.Span}
 *
 * Brave does not support multiple parents so this has been implemented to use the first parent defined.
 */
public final class BraveSpanBuilder implements Tracer.SpanBuilder {

    private final brave.Tracer braveTracer;
    private final Map<String, String> tags = new LinkedHashMap<>();

    private String operationName;
    private long timestamp;
    private TraceContext parent;

    BraveSpanBuilder(brave.Tracer braveTracer, String operationName) {
        this.braveTracer = braveTracer;
        this.operationName = operationName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        return addReference(References.CHILD_OF, parent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder asChildOf(Span parent) {
        return asChildOf(parent.context());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        if (parent != null) {
            return this;
        }
        if (References.CHILD_OF.equals(referenceType) || References.FOLLOWS_FROM.equals(referenceType)) {
            this.parent = ((BraveSpanContext) referencedContext).unwrap();
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder withTag(String key, String value) {
        tags.put(key, value);

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder withTag(String key, boolean value) {
        return withTag(key, Boolean.toString(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder withTag(String key, Number value) {
        return withTag(key, value.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        this.timestamp = microseconds;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BraveSpan start() {
        brave.Span span = parent == null ? braveTracer.newTrace() :
                parent.shared() ? braveTracer.joinSpan(parent) : braveTracer.newChild(parent);

        if (operationName != null) span.name(operationName);
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            span.tag(tag.getKey(), tag.getValue());

            if (Tags.SPAN_KIND.getKey().equals(tag.getKey()) && Tags.SPAN_KIND_CLIENT.equals(tag.getValue())) {
                span.kind(brave.Span.Kind.CLIENT);
            } else if (Tags.SPAN_KIND.getKey().equals(tag.getKey()) && Tags.SPAN_KIND_SERVER.equals(tag.getValue())) {
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

    /**
     * Returns zero values as neither <a href="https://github.com/openzipkin/b3-propagation">B3</a>
     * nor Brave include baggage support.
     */
    // OpenTracing could one day define a way to plug-in arbitrary baggage handling similar to how
    // it has feature-specific apis like active-span
    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        // brave doesn't support baggage
        return Collections.EMPTY_SET;
    }
}
