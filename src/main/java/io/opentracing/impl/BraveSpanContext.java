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

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanId;

import java.util.Collections;
import java.util.Map;

class BraveSpanContext extends AbstractSpanContext {
    SpanId braveSpanId;
    ServerTracer serverTracer;
    ClientTracer clientTracer;
    
    public BraveSpanContext(Map<String, Object> traceState, SpanId spanId, Map<String, String> baggage, BraveTracer tracer) {
        // TODO passing traceState to AbstractSpanContext constructor does not give us anything. Is it useful at all?
        // maybe AbstractSpanContext should not know about traceState and its subclasses should handle it? 
        super(traceState, baggage, tracer);
        this.braveSpanId = spanId;
    }
    
    public BraveSpanContext(SpanId spanId, Map<String, String> baggage, BraveTracer tracer) {
        // TODO add specific constructor to AbstractSpanContext, this is ugly - does not keep the consistency of having
        // valid traceState
        super(Collections.emptyMap(), baggage, tracer); 
        braveSpanId = spanId;
    }

    // TODO rename, no need to have "context" here
    public long getContextTraceId() {
        return braveSpanId.traceId;
    }

    // TODO rename, no need to have "context" here
    public long getContextSpanId() {
        return braveSpanId.spanId;
    }

    // TODO rename, no need to have "context" here
    public Long getContextParentSpanId() {
        return braveSpanId.nullableParentId();
    }

    // TODO Do we need it here? (It's also on the Span) If yes - make sure that BraveSpanContext is immutable
    public BraveSpanContext withServerTracer(ServerTracer serverTracer) {
        this.serverTracer = serverTracer;
        return this;
    }

    // TODO Do we need it here? (It's also on the Span) If yes - make sure that BraveSpanContext is immutable
    public BraveSpanContext setClientTracer(ClientTracer clientTracer) {
        this.clientTracer = clientTracer;
        return this;
    }
    
    // TODO public?
    SpanId getBraveSpanId() {
        return braveSpanId;
    }
}
