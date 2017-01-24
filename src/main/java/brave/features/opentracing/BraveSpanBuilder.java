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

    private String operationName;

    public BraveSpanBuilder(String operationName) {
        this.operationName = operationName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder asChildOf(Span parent) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder withTag(String key, String value) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder withTag(String key, boolean value) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder withTag(String key, Number value) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span start() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return null;
    }
}
