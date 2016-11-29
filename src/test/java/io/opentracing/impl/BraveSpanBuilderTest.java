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
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.SpanId;
import org.junit.Before;
import org.junit.Test;

import static java.util.Collections.emptyMap;
import static org.mockito.Mockito.mock;

public final class BraveSpanBuilderTest {

    private SpanCollector mockSpanCollector;
    private Brave brave;
    private BraveTracer tracer;

    @Before
    public void setup() {
        mockSpanCollector = mock(SpanCollector.class);
        // -1062731775 = 192.168.0.1
        final Brave.Builder builder = new Brave.Builder(-1062731775, 8080, "unknown");
        brave = builder.spanCollector(mockSpanCollector).traceSampler(Sampler.create(1)).build();
        tracer = new BraveTracer();
    }

    @Test
    public void testCreateSpan() {
        String operationName = "test-testCreateSpan";
        brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilder builder = BraveSpanBuilder.create(brave, operationName, tracer);
        BraveSpan span = builder.createSpan();
        
        // TODO can we do something about these casts? so that we don't need to cast?
        BraveSpanContext context = (BraveSpanContext) span.context();

        assert context.braveSpanId != null;
        assert 0 != context.braveSpanId.spanId : context.braveSpanId.spanId;
        assert 0 != context.braveSpanId.traceId : context.braveSpanId.traceId;
        assert null == context.braveSpanId.nullableParentId() : context.braveSpanId.nullableParentId();
        assert operationName.equals(span.getOperationName()) : "span.getOperationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();
    }

    @Test
    public void testWithServerTracer() {
        String operationName = "test-testWithServerTracer";
        brave.serverTracer().clearCurrentSpan();

        BraveSpanBuilder builder = BraveSpanBuilder
                .create(brave, operationName, tracer)
                .withServerTracer(brave.serverTracer());

        BraveSpan span = builder.createSpan();
        BraveSpanContext context = (BraveSpanContext) span.context();

        assert context.braveSpanId != null;
        assert 0 != context.braveSpanId.spanId : context.braveSpanId.spanId;
        assert 0 != context.braveSpanId.traceId : context.braveSpanId.traceId;
        assert null == context.braveSpanId.nullableParentId() : context.braveSpanId.nullableParentId();
        assert operationName.equals(span.getOperationName()) : "span.getOperationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();
    }

    @Test
    public void testWithServerTracer_withParentSpanContext() {
        String operationName = "test-testWithServerTracer_withParentSpanContext";
        brave.serverTracer().clearCurrentSpan();

        SpanId parentSpanId = SpanId.builder().spanId(1).traceId(3).build();
        BraveSpanContext parent = new BraveSpanContext(parentSpanId, emptyMap(), tracer); 

        brave.serverTracer().setStateCurrentTrace(parentSpanId, "parent-op-name");

        BraveSpanBuilder builder = (BraveSpanBuilder) BraveSpanBuilder
                .create(brave, operationName, tracer)
                .withServerTracer(brave.serverTracer())
                .asChildOf(parent);

        BraveSpan span = builder.createSpan();

        BraveSpanContext context = (BraveSpanContext) span.context();

        assert context.braveSpanId != null;
        assert 0 != context.braveSpanId.spanId : context.braveSpanId.spanId;
        assert context.braveSpanId.traceId == 3;
        assert context.braveSpanId.nullableParentId() == 1;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert parent.equals(span.parent.get());
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();
    }
    
    @Test
    public void testWithServerTracer_withParentSpan() {
        String operationName = "test-testWithServerTracer_withParentSpan";
        brave.serverTracer().clearCurrentSpan();

        BraveSpan parentSpan = BraveSpanBuilder.create(brave, "parent-op-name", tracer)
            .withServerTracer(brave.serverTracer())
            .createSpan();
        
        BraveSpanContext parentContext = (BraveSpanContext) parentSpan.context();


        BraveSpanBuilder builder = (BraveSpanBuilder) BraveSpanBuilder
                .create(brave, operationName, tracer)
                .withServerTracer(brave.serverTracer())
                .asChildOf(parentSpan);

        BraveSpan span = builder.createSpan();

        BraveSpanContext context = (BraveSpanContext) span.context();

        assert context.braveSpanId != null;
        assert 0 != context.braveSpanId.spanId : context.braveSpanId.spanId;
        assert context.getContextTraceId() == parentContext.getContextTraceId() : context.braveSpanId.traceId;
        assert context.braveSpanId.nullableParentId().equals(parentContext.getContextSpanId());
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert parentContext.equals(span.parent.get());
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();
    }
}
