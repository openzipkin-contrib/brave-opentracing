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

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanId;
import io.opentracing.References;

import java.util.Optional;
import java.util.concurrent.TimeUnit;


final class BraveSpanBuilder extends AbstractSpanBuilder {
    ServerTracer serverTracer;
    BraveTracer tracer;

    private final Brave brave;

    static BraveSpanBuilder create(Brave brave, String operationName, BraveTracer tracer) {
        return new BraveSpanBuilder(brave, operationName, tracer);
    }

    private BraveSpanBuilder(Brave brave, String operationName, BraveTracer tracer) {
        super(operationName);
        this.brave = brave;
        this.tracer = tracer;
    }

    @Override
    protected BraveSpan createSpan() {
        // TODO should it be possible to call createSpan multiple times on the same builder? what should be the behavior? test it
        BraveSpanContext parent = getParent();
        Long parentTraceId = null;
        Long parentSpanId = null;
        
        // TODO it would be nice if there was a way to specify custom IDs (if there is no parent)
        if (null != parent) {
            parentTraceId = parent.getContextTraceId();
            parentSpanId = parent.getContextSpanId();
            Long parentParentId = parent.getContextParentSpanId();

            // push this into the serverSpanState as the current span as that is where new localSpans find their parents
            brave.serverTracer().setStateCurrentTrace(parentTraceId, parentSpanId, parentParentId, operationName);
        }
        if (null == parentTraceId && null == parentSpanId) {
            brave.serverTracer().clearCurrentSpan();
        }
        
        // TODO decide who should maintain Brave state - Span or SpanBuilder. It seems that calling setStateCurrentTrace during
        // span creation belongs in the builder, but don't we need to do something when finishing a span?
        // Also find other calls to brave and decide if they are in the right spot
        SpanId spanId = brave.localTracer().startNewSpan(
                "jvm",
                operationName,
                TimeUnit.SECONDS.toMicros(start.getEpochSecond()) + TimeUnit.NANOSECONDS.toMicros(start.getNano()));
        
        BraveSpanContext context = new BraveSpanContext(spanId, baggage, tracer);

        BraveSpan span = BraveSpan.create(
                brave,
                operationName,
                context,
                Optional.ofNullable(parent),
                start,
                Optional.ofNullable(serverTracer));

        // push this into the serverSpanState as the current span as that is where new localSpans find their parents
        brave.serverTracer().setStateCurrentTrace(spanId, operationName);

        assert (null == parentTraceId && null == parentSpanId) || (null != parentTraceId && null != parentSpanId);
        assert null == parentTraceId || spanId.traceId == parentTraceId;
        assert (null == spanId.nullableParentId() && null == parentSpanId) || parentSpanId.equals(spanId.nullableParentId());

        return span;
    }

    BraveSpanBuilder withServerTracer(ServerTracer serverTracer) {
        this.serverTracer = serverTracer;
        return this;
    }

    /** @Nullable **/
    private BraveSpanContext getParent() {
        for (Reference reference : references) {
            if (References.CHILD_OF.equals(reference.getReferenceType()) 
                    && !(reference.getReferredTo() instanceof NoopSpanContext)) {
                return (BraveSpanContext) reference.getReferredTo();
            }
        }
        return null;
    }
}
