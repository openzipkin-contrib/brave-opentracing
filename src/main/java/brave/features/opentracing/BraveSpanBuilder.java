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
package brave.features.opentracing;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import java.util.Map;

public class BraveSpanBuilder implements Tracer.SpanBuilder {
    /**
     * A shorthand for addReference(References.CHILD_OF, parent).
     *
     * @param parent
     */
    @Override
    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        return null;
    }

    /**
     * A shorthand for addReference(References.CHILD_OF, parent.context()).
     *
     * @param parent
     */
    @Override
    public Tracer.SpanBuilder asChildOf(Span parent) {
        return null;
    }

    /**
     * Add a reference from the Span being built to a distinct (usually parent) Span. May be called multiple times to
     * represent multiple such References.
     *
     * @param referenceType     the reference type, typically one of the constants defined in References
     * @param referencedContext the SpanContext being referenced; e.g., for a References.CHILD_OF referenceType, the
     *                          referencedContext is the parent
     * @see References
     */
    @Override
    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        return null;
    }

    /**
     * Same as {@link Span#setTag(String, String)}, but for the span being built.
     *
     * @param key
     * @param value
     */
    @Override
    public Tracer.SpanBuilder withTag(String key, String value) {
        return null;
    }

    /**
     * Same as {@link Span#setTag(String, String)}, but for the span being built.
     *
     * @param key
     * @param value
     */
    @Override
    public Tracer.SpanBuilder withTag(String key, boolean value) {
        return null;
    }

    /**
     * Same as {@link Span#setTag(String, String)}, but for the span being built.
     *
     * @param key
     * @param value
     */
    @Override
    public Tracer.SpanBuilder withTag(String key, Number value) {
        return null;
    }

    /**
     * Specify a timestamp of when the Span was started, represented in microseconds since epoch.
     *
     * @param microseconds
     */
    @Override
    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        return null;
    }

    /**
     * Returns the started Span.
     */
    @Override
    public Span start() {
        return null;
    }

    /**
     * @return all zero or more baggage items propagating along with the associated Span
     * @see Span#setBaggageItem(String, String)
     * @see Span#getBaggageItem(String)
     */
    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return null;
    }
}
