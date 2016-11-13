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

import java.util.Map;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import io.opentracing.NoopSpanBuilder;
import io.opentracing.Span;
import io.opentracing.SpanContext;

import io.opentracing.propagation.Format;
import java.util.HashMap;

/**
 * See project README.md for examples on how to use the API.
 *
 * Design notes:
 *  All spans that are explicitly created are done so with the local tracer.
 *  CS+CR and SR+SS annotations are done around the inject and extract methods.
 *
 * There is a mismatch between the two APIs in how state of the current span is held.
 * OpenTracing does not hold any such state and passes in the current span intended to be a parent
 * is an explicit action in the API, while in Brave this state is known and such action not required.
 * OpenTracing's expectations are honoured here and Brave's internal state is overridden as needed.
 *
 * It is noted that it probably would have been simpler to have implemented against lower APIs in brave,
 * like directly against the collector and spans, than the top level Brave api.
 */
public final class BraveTracer extends AbstractTracer {

    final Brave brave;

    public BraveTracer() {
        this(new Brave.Builder());
    }

    public BraveTracer(Brave.Builder builder) {
        brave = builder.build();
    }

    @Override
    BraveSpanBuilder createSpanBuilder(String operationName) {
        return BraveSpanBuilder.create(brave, operationName);
    }

    @Override
    Map<String, Object> getTraceState(SpanContext spanContext) {
        Span span = (Span)spanContext;

        return new HashMap<String,Object>() {{
            SpanId spanId = ((BraveSpan)span).spanId;
            put(BraveHttpHeaders.Sampled.getName(), "1");
            put(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(spanId.getTraceId()));
            put(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId.getSpanId()));
            if (null != spanId.getParentSpanId()) {
                put(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(spanId.getParentSpanId()));
            }
        }};
    }

    Map<String, String> getBaggage(Span span) {
        return ((BraveSpan)span).getBaggage();
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        brave.clientTracer().startNewSpan(((BraveSpan)spanContext).getOperationName());
        brave.clientTracer().setClientSent();
        super.inject(spanContext, format, carrier);
        ((BraveSpan)spanContext).setClientTracer(brave.clientTracer());
    }

    @Override
    public <C> SpanBuilder extract(Format<C> format, C carrier) {

        BraveSpanBuilder builder = (BraveSpanBuilder) super.extract(format, carrier);

        if (null != builder.traceId
                && null != builder.parentSpanId
                && null != builder.operationName) {

            brave.serverTracer().setStateCurrentTrace(
                    builder.traceId,
                    builder.parentSpanId,
                    null,
                    builder.operationName);

            brave.serverTracer().setServerReceived();
            builder.withServerTracer(brave.serverTracer());
            return builder;
        }
        return NoopSpanBuilder.INSTANCE;
    }

}
