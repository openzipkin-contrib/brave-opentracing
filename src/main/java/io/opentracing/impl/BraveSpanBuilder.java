/**
 * Copyright 2016 The OpenZipkin Authors
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
package io.opentracing.impl;

import java.util.Optional;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import io.opentracing.References;


final class BraveSpanBuilder extends AbstractSpanBuilder implements BraveSpanContext {

    Long traceId = null;
    Long parentSpanId = null;
    ServerTracer serverTracer = null;

    private final Brave brave;

    static BraveSpanBuilder create(Brave brave, String operationName) {
        return new BraveSpanBuilder(brave, operationName);
    }

    private BraveSpanBuilder(Brave brave, String operationName) {
        super(operationName);
        this.brave = brave;
    }

    @Override
    protected BraveSpan createSpan() {
        BraveSpanContext parent = getParent();
        if (null != parent) {
            traceId = parent.getContextTraceId();
            parentSpanId = parent.getContextSpanId();
            Long parentParentId = parent.getContextParentSpanId();

            // push this into the serverSpanState as the current span as that is where new localSpans find their parents
            brave.serverTracer().setStateCurrentTrace(traceId, parentSpanId, parentParentId, operationName);
        }
        if (null == traceId && null == parentSpanId) {
            brave.serverTracer().clearCurrentSpan();
        }

        BraveSpan span = BraveSpan.create(
                brave,
                operationName,
                Optional.ofNullable(parent),
                start,
                Optional.ofNullable(serverTracer));

        // push this into the serverSpanState as the current span as that is where new localSpans find their parents
        brave.serverTracer().setStateCurrentTrace(
                span.spanId.traceId,
                span.spanId.spanId,
                span.spanId.parentId,
                span.getOperationName());

        assert (null == traceId && null == parentSpanId) || (null != traceId && null != parentSpanId);
        assert null == traceId || span.spanId.traceId == traceId;
        assert null == parentSpanId || parentSpanId.equals(span.spanId.nullableParentId());

        if (null == traceId && null == parentSpanId) {
            // called through tracer.buildSpan(..), as opposed to builder.extract(..)
            brave.serverTracer().setStateCurrentTrace(
                    span.spanId.traceId,
                    span.spanId.spanId,
                    span.spanId.nullableParentId(),
                    operationName);
        }

        return span;
    }

    BraveSpanBuilder withServerTracer(ServerTracer serverTracer) {
        this.serverTracer = serverTracer;
        return this;
    }

    /** @Nullable **/
    private BraveSpanContext getParent() {
        for (Reference reference : references) {
            if (References.CHILD_OF.equals(reference.getReferenceType())) {
                return (BraveSpanContext) reference.getReferredTo();
            }
        }
        return null;
    }

    @Override
    boolean isTraceState(String key, Object value) {
        return null != valueOf(key);
    }

    private static BraveHttpHeaders valueOf(String key) {
        for (BraveHttpHeaders header : BraveHttpHeaders.values()) {
            if (header.getName().equals(key)) {
                return header;
            }
        }
        return null;
    }

    @Override
    BraveSpanBuilder withStateItem(String key, Object value) {
        BraveHttpHeaders header = valueOf(key);
        if (null == header) {
            throw new IllegalArgumentException(key + " is not a valid brave header");
        }
        switch (header) {
            case TraceId:
                traceId = value instanceof Number
                        ? ((Number)value).longValue()
                        : IdConversion.convertToLong(value.toString());
                break;
            case SpanId:
                parentSpanId = value instanceof Number
                        ? ((Number)value).longValue()
                        : IdConversion.convertToLong(value.toString());
                break;
            case ParentSpanId:
                if (null == parentSpanId) {
                    parentSpanId = value instanceof Number
                            ? ((Number)value).longValue()
                            : IdConversion.convertToLong(value.toString());
                }
                break;
        }
        return this;
    }

    @Override
    public long getContextTraceId() {
        return traceId;
    }

    @Override
    public long getContextSpanId() {
        return parentSpanId;
    }

    /** null **/
    @Override
    public Long getContextParentSpanId() {
        return null;
    }
}
