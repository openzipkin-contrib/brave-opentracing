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
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import io.opentracing.Span;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public final class BraveSpanBuilderTest {

    private List<zipkin.Span> spans = new ArrayList<>();
    // -1062731775 = 192.168.0.1
    private Brave brave = new Brave.Builder(-1062731775, 8080, "unknown")
        .reporter(spans::add).build();

    @Before
    public void setup() {
        ThreadLocalServerClientAndLocalSpanState.clear();
    }

    @Test
    public void testCreateSpan() {
        String operationName = "test-testCreateSpan";
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

        BraveSpan parent = BraveSpan.create(
                brave,
                operationName + "-parent",
                Optional.empty(),
                start.minusMillis(100),
                Optional.of(brave.serverTracer()));

        brave.serverTracer().setStateCurrentTrace(
                parent.spanId,
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
