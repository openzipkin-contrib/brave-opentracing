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
package io.opentracing;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import java.time.Instant;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mock;


public final class BraveSpanBuilderTest {

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
    public void testCreateSpan() {
        String operationName = "test-testCreateSpan";
        brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilder builder = BraveSpanBuilder.create(brave, operationName);
        BraveSpan span = builder.createSpan();

        assert null != span.spanId;
        assert 0 != span.spanId.spanId : span.spanId.spanId;
        assert 0 != span.spanId.traceId : span.spanId.traceId;
        assert null == span.spanId.nullableParentId() : span.spanId.nullableParentId();
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
                .create(brave, operationName)
                .withServerTracer(brave.serverTracer());

        BraveSpan span = builder.createSpan();

        assert null != span.spanId;
        assert 0 != span.spanId.spanId : span.spanId.spanId;
        assert 0 != span.spanId.traceId : span.spanId.traceId;
        assert null == span.spanId.nullableParentId() : span.spanId.nullableParentId();
        assert operationName.equals(span.getOperationName()) : "span.getOperationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();
    }

    @Test
    public void testWithServerTracer_withParent() {
        String operationName = "test-testWithServerTracer_withParent";
        Instant start = Instant.now();
        brave.serverTracer().clearCurrentSpan();

        BraveSpan parent = BraveSpan.create(
                brave,
                operationName + "-parent",
                Optional.empty(),
                start.minusMillis(100),
                Optional.of(brave.serverTracer()));

        brave.serverTracer().setStateCurrentTrace(
                parent.spanId.traceId,
                parent.spanId.spanId,
                null,
                parent.getOperationName());

        BraveSpanBuilder builder = (BraveSpanBuilder) BraveSpanBuilder
                .create(brave, operationName)
                .withServerTracer(brave.serverTracer())
                .asChildOf((Span)parent);

        BraveSpan span = builder.createSpan();

        assert null != span.spanId;
        assert 0 != span.spanId.spanId : span.spanId.spanId;
        assert 0 != span.spanId.traceId : span.spanId.traceId;
        assert null != span.spanId.nullableParentId() : span.spanId.nullableParentId();
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert parent.equals(span.parent.get());
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();
    }

    @Test
    public void testIsTraceState() {
        String operationName = "test-testCreateSpan";
        BraveSpanBuilder builder = BraveSpanBuilder.create(brave, operationName);

        for (BraveHttpHeaders header : BraveHttpHeaders.values()) {
            assert builder.isTraceState(header.getName(), "any-value")
                    : header.getName() + " should be a trace state key";
        }

        assert !builder.isTraceState("not-a-zipkin-header", "any-value");
    }

    @Test
    public void testWithStateItem() {
        String operationName = "test-testWithStateItem";
        brave.serverTracer().clearCurrentSpan();

        BraveSpanBuilder builder = BraveSpanBuilder
                .create(brave, operationName)
                .withServerTracer(brave.serverTracer())
                .withStateItem(BraveHttpHeaders.TraceId.getName(), "123")
                .withStateItem(BraveHttpHeaders.SpanId.getName(), "234");

        brave.serverTracer().setStateCurrentTrace(
                builder.traceId,
                builder.parentSpanId,
                null,
                builder.operationName);

        BraveSpan span = builder.createSpan();

        assert null != span.spanId;
        assert 0 != span.spanId.spanId : span.spanId.spanId;
        assert 291 == span.spanId.traceId : span.spanId.traceId;
        assert 564 == span.spanId.nullableParentId() : span.spanId.nullableParentId();
        assert operationName.equals(span.getOperationName()) :  span.getOperationName();
        assert !span.parent.isPresent();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();
    }

}
