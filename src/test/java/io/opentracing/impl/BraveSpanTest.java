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

import com.github.kristofa.brave.*;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Optional;

import static java.util.Collections.emptyMap;
import static org.mockito.Mockito.mock;

public final class BraveSpanTest {

    private SpanCollector mockSpanCollector;
    private Brave brave;

    @Before
    public void setup() {
        mockSpanCollector = mock(SpanCollector.class);
        // -1062731775 = 192.168.0.1
        final Brave.Builder builder = new Brave.Builder(-1062731775, 8080, "unknown");
        brave = builder.spanCollector(mockSpanCollector).traceSampler(Sampler.create(1)).build();
    }

    @Test
    public void test() {
        String operationName = "test-test";
        Optional<BraveSpanContext> parent = Optional.empty();
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.empty();

        BraveSpanContext originalContext = new BraveSpanContext(SpanId.builder().traceId(3).spanId(5).build(), emptyMap(), null);
        BraveSpan span = BraveSpan.create(brave, operationName, originalContext, parent, start, serverTracer);
        BraveSpanContext context = (BraveSpanContext) span.context();
        assert context.getContextSpanId() == 5;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();

        span.finish();

        assert context.getContextSpanId() == 5;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();
    }

    @Test
    public void test_withServerTracer() {
        String operationName = "test-test_withServerTracer";
        Optional<BraveSpanContext> parent = Optional.empty();
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.of(brave.serverTracer());

        BraveSpanContext originalContext = new BraveSpanContext(SpanId.builder().traceId(3).spanId(5).build(), emptyMap(), null);
        BraveSpan span = BraveSpan.create(brave, operationName, originalContext, parent, start, serverTracer);
        BraveSpanContext context = (BraveSpanContext) span.context();

        assert context.getContextSpanId() == 5;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();

        span.finish();

        assert context.getContextSpanId() == 5;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();
    }

    @Test
    public void test_withParent() {
        String operationName = "test-test_withParent";
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.empty();

        Optional<BraveSpanContext> parent = Optional.of(
                new BraveSpanContext(SpanId.builder().traceId(3).spanId(1).build(), emptyMap(), null));
        
        BraveSpanContext originalContext = new BraveSpanContext(SpanId.builder().traceId(3).spanId(5).build(), emptyMap(), null);
        BraveSpan span = BraveSpan.create(brave, operationName, originalContext, parent, start, serverTracer);
        BraveSpanContext context = (BraveSpanContext) span.context();

        assert context.getContextSpanId() == 5;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert !span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();

        span.finish();

        assert context.getContextSpanId() == 5;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert !span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();
    }

    @Test
    public void test_withParent_withServerTracer() {
        String operationName = "test-test_withParent_withServerTracer";
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.of(brave.serverTracer());

        Optional<BraveSpanContext> parent = Optional.of(
                new BraveSpanContext(SpanId.builder().traceId(3).spanId(1).build(), emptyMap(), null)
                    .withServerTracer(serverTracer.get()));
        
        BraveSpanContext originalContext = new BraveSpanContext(SpanId.builder().traceId(3).spanId(5).build(), emptyMap(), null);
        BraveSpan span = BraveSpan.create(brave, operationName, originalContext, parent, start, serverTracer);
        BraveSpanContext context = (BraveSpanContext) span.context();

        assert context.getContextSpanId() == 5;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();

        span.finish();

        assert context.getContextSpanId() == 5;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();
    }

}
