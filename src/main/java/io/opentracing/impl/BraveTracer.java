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
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
        return BraveSpanBuilder.create(brave, operationName, this);
    }
    
    @Override
    AbstractSpanContext createSpanContext(Map<String, Object> traceState, Map<String, String> baggage) {
        Optional<SpanId> braveSpanId = SpanIdExtractor.toBraveId(traceState);
        if(!braveSpanId.isPresent()) {
            return NoopSpanContext.INSTANCE;
        }
        
        BraveSpanContext ctx = new BraveSpanContext(traceState, braveSpanId.get(), baggage, this);
        return ctx.withServerTracer(brave.serverTracer());
    }

    @Override
    Map<String, Object> getTraceState(SpanContext spanContext) {
        BraveSpanContext context = (BraveSpanContext) spanContext;

        return new HashMap<String,Object>() {{
            SpanId spanId = context.getBraveSpanId();
            put(BraveHttpHeaders.Sampled.getName(), "1");
            put(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(spanId.getTraceId()));
            put(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId.getSpanId()));
            if (null != spanId.getParentSpanId()) {
                put(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(spanId.getParentSpanId()));
            }
        }};
    }
    
    @Override
    boolean isTraceState(String key, Object value) {
        return null != braveHttpHeader(key);
    }
    
    BraveHttpHeaders braveHttpHeader(String key) {
        for (BraveHttpHeaders header : BraveHttpHeaders.values()) {
            if (header.getName().equals(key)) {
                return header;
            }
        }
        return null;
    }

    Map<String, String> getBaggage(Span span) {
        return ((BraveSpan)span).getBaggage();
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        brave.clientTracer().startNewSpan("no operation name"); // TODO why is it here??
        brave.clientTracer().setClientSent();
        super.inject(spanContext, format, carrier);
        ((BraveSpanContext)spanContext).setClientTracer(brave.clientTracer());
    }

    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {

        SpanContext spanContext = super.extract(format, carrier);
        if(spanContext instanceof io.opentracing.impl.NoopSpanContext) {
            return spanContext;
        }
        
        BraveSpanContext context = (BraveSpanContext) spanContext; 

        if (context.braveSpanId != null) {

            brave.serverTracer().setStateCurrentTrace(
                    context.getContextTraceId(),
                    context.getContextSpanId(),
                    context.getContextParentSpanId(),
                    "received"); // because there is no operation name defined at this point, it will be defined after calling
                           // BraveSpanBuilder.createSpan
                           // TODO is it ok?

            brave.serverTracer().setServerReceived();
            context.withServerTracer(brave.serverTracer());
            return context;
        }
        return NoopSpanContext.INSTANCE;
    }
}
