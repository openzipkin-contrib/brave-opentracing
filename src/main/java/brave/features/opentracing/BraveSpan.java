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

import io.opentracing.Span;
import io.opentracing.SpanContext;

import java.util.Map;

public class BraveSpan implements Span {

    static BraveSpan wrap(brave.Span span) {
        if (span == null) throw new NullPointerException("span == null");
        return new BraveSpan(span);
    }

    final brave.Span unwrap() {
        return delegate;
    }

    final brave.Span delegate;
    final SpanContext context;

    private BraveSpan(brave.Span delegate) {
        this.delegate = delegate;
        this.context = BraveSpanContext.wrap(delegate.context());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpanContext context() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finish() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finish(long finishMicros) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span setTag(String key, String value) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span setTag(String key, boolean value) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span setTag(String key, Number value) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(Map<String, ?> fields) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(String event) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(long timestampMicroseconds, String event) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span setBaggageItem(String key, String value) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBaggageItem(String key) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span setOperationName(String operationName) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(String eventName, Object payload) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span log(long timestampMicroseconds, String eventName, Object payload) {
        return null;
    }
}
