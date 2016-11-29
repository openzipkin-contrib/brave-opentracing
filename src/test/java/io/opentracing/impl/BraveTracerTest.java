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

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyMap;


public final class BraveTracerTest {

    @Test
    public void test_buildSpan() {
        String operationName = "test-test_buildSpan";
        BraveTracer tracer = new BraveTracer();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilder builder = (BraveSpanBuilder) tracer.buildSpan(operationName);

        assertCurrentLocalSpanIdIsNull(tracer);
        assertCurrentServerSpanIdIsNull(tracer);
        assertCurrentClientSpanIdIsNull(tracer);
        
        BraveSpan span = builder.createSpan();
        
        assert null != span.spanId;
        assert 0 != span.spanId.spanId : span.spanId.spanId;
        assert 0 != span.spanId.traceId : span.spanId.traceId;
        assert null == span.spanId.nullableParentId() : span.spanId.nullableParentId();
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();
        assert span.getStart().isBefore(Instant.now().plusMillis(1));
        
        assertCurrentLocalSpanIdEquals(tracer, span.spanId);
        assertCurrentServerSpanIdEquals(tracer, span.spanId);
        assertCurrentClientSpanIdIsNull(tracer);

        span.finish();
        
        assertCurrentLocalSpanIdIsNull(tracer);
        assertCurrentServerSpanIdEquals(tracer, span.spanId);
        assertCurrentClientSpanIdIsNull(tracer);
    }

    @Test
    public void test_buildSpan_child() {
        String parentOperationName = "test-test_buildSpan_child-parent";
        String operationName = "test-test_buildSpan_child";
        BraveTracer tracer = new BraveTracer();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilder builder = (BraveSpanBuilder) tracer.buildSpan(parentOperationName);

        BraveSpan parent = builder.createSpan();

        assert null != parent.spanId;
        assert 0 != parent.spanId.spanId : parent.spanId.spanId;
        assert 0 != parent.spanId.traceId : parent.spanId.traceId;
        assert null == parent.spanId.nullableParentId() : parent.spanId.nullableParentId();
        assert parentOperationName.equals(parent.getOperationName()) : "span.operationName was " + parent.getOperationName();
        assert !parent.parent.isPresent();
        assert !parent.serverTracer.isPresent();
        assert parent.getBaggage().isEmpty();
        assert parent.getStart().isBefore(Instant.now().plusMillis(1));

        assertCurrentLocalSpanIdEquals(tracer, parent.spanId);
        assertCurrentServerSpanIdEquals(tracer, parent.spanId);
        assertCurrentClientSpanIdIsNull(tracer);
        
        builder = (BraveSpanBuilder) tracer.buildSpan(operationName).asChildOf(parent);
        BraveSpan span = builder.createSpan();
        
        assertCurrentLocalSpanIdEquals(tracer, span.spanId);
        assertCurrentServerSpanIdEquals(tracer, span.spanId);
        assertCurrentClientSpanIdIsNull(tracer);

        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assertChildToParent(span, parent, false);

        span.finish();
        
        assertCurrentLocalSpanIdIsNull(tracer); // TODO expected? shouldn't it be equal to parent.spanId?
        assertCurrentServerSpanIdEquals(tracer, span.spanId); // TODO expected? Is it still pointing to child span after it finished?
        assertCurrentClientSpanIdIsNull(tracer);
        
        parent.finish();
        
        assertCurrentLocalSpanIdIsNull(tracer);
        assertCurrentServerSpanIdEquals(tracer, span.spanId); // TOOD expected? all spans finished and it's still pointing to the child one 
        assertCurrentClientSpanIdIsNull(tracer);
    }

    @Test
    public void test_buildSpan_seperate_traces() {
        String parentOperationName = "test-test_buildSpan_seperate_traces-parent";
        String operationName = "test-test_buildSpan_seperate_traces";
        BraveTracer tracer = new BraveTracer();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilder builder = (BraveSpanBuilder) tracer.buildSpan(parentOperationName);

        BraveSpan first = builder.createSpan();

        assert null != first.spanId;
        assert 0 != first.spanId.spanId : first.spanId.spanId;
        assert 0 != first.spanId.traceId : first.spanId.traceId;
        assert null == first.spanId.nullableParentId() : first.spanId.nullableParentId();
        assert parentOperationName.equals(first.getOperationName()) : "span.operationName was " + first.getOperationName();
        assert !first.parent.isPresent();
        assert !first.serverTracer.isPresent();
        assert first.getBaggage().isEmpty();

        first.finish();
        builder = (BraveSpanBuilder) tracer.buildSpan(operationName);
        BraveSpan second = builder.createSpan();

        assert null != second.spanId;
        assert 0 != second.spanId.spanId : second.spanId.spanId;
        assert 0 != second.spanId.spanId : second.spanId.spanId;
        assert 0 != second.spanId.traceId : second.spanId.traceId;

        assert first.spanId.traceId != second.spanId.traceId
                : "child: " + first.spanId.traceId + " ; parent: " + second.spanId.traceId;

        assert null == second.spanId.nullableParentId() : second.spanId.nullableParentId();
        assert operationName.equals(second.getOperationName()) : "span.operationName was " + second.getOperationName();
        assert !first.parent.isPresent();
        assert !second.serverTracer.isPresent();
        assert second.getBaggage().isEmpty();

        second.finish();
    }

    @Test
    public void test_buildSpan_same_trace() {
        String parentOperationName = "test-test_buildSpan_same_trace-parent";
        String operationName1 = "test-test_buildSpan_same_trace-1";
        String operationName2 = "test-test_buildSpan_same_trace-2";
        BraveTracer tracer = new BraveTracer();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilder builder = (BraveSpanBuilder) tracer.buildSpan(parentOperationName);

        assert parentOperationName.equals(builder.operationName) : builder.operationName;
        assert builder.references.isEmpty();
        assert null == builder.serverTracer : builder.serverTracer;

        BraveSpan parent = builder.createSpan();

        assert null != parent.spanId;
        assert 0 != parent.spanId.spanId : parent.spanId.spanId;
        assert 0 != parent.spanId.traceId : parent.spanId.traceId;
        assert null == parent.spanId.nullableParentId() : parent.spanId.nullableParentId();
        assert parentOperationName.equals(parent.getOperationName()) : "span.operationName was " + parent.getOperationName();
        assert !parent.parent.isPresent();
        assert !parent.serverTracer.isPresent();
        assert parent.getBaggage().isEmpty();

        builder = (BraveSpanBuilder) tracer.buildSpan(operationName1).asChildOf((Span)parent);
        BraveSpan first = builder.createSpan();

        assert operationName1.equals(first.getOperationName()) : "span.operationName was " + first.getOperationName();
        assertChildToParent(first, parent, false);

        first.finish();
        builder = (BraveSpanBuilder) tracer.buildSpan(operationName2).asChildOf((Span)parent);
        BraveSpan second = builder.createSpan();

        assert operationName2.equals(second.getOperationName()) : "span.operationName was " + second.getOperationName();
        assertChildToParent(second, parent, false);

        second.finish();
        parent.finish();
    }

    @Test
    public void test_buildSpan_notChild_seperate_traces() {
        String parentOperationName = "test-test_buildSpan_notChild_seperate_traces-parent";
        String operationName = "test-test_buildSpan_notChild_seperate_traces";
        BraveTracer tracer = new BraveTracer();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilder builder = (BraveSpanBuilder) tracer.buildSpan(parentOperationName);

        assert parentOperationName.equals(builder.operationName) : builder.operationName;
        assert builder.references.isEmpty();
        assert null == builder.serverTracer : builder.serverTracer;

        BraveSpan first = builder.createSpan();

        assert null != first.spanId;
        assert 0 != first.spanId.spanId : first.spanId.spanId;
        assert 0 != first.spanId.traceId : first.spanId.traceId;
        assert null == first.spanId.nullableParentId() : first.spanId.nullableParentId();
        assert parentOperationName.equals(first.getOperationName()) : "span.operationName was " + first.getOperationName();
        assert !first.parent.isPresent();
        assert !first.serverTracer.isPresent();
        assert first.getBaggage().isEmpty();

        builder = (BraveSpanBuilder) tracer.buildSpan(operationName);
        BraveSpan second = builder.createSpan();

        assert null != second.spanId;
        assert 0 != second.spanId.spanId : second.spanId.spanId;
        assert 0 != second.spanId.spanId : second.spanId.spanId;
        assert 0 != second.spanId.traceId : second.spanId.traceId;

        assert first.spanId.traceId != second.spanId.traceId
                : "child: " + first.spanId.traceId + " ; parent: " + second.spanId.traceId;

        assert null == second.spanId.nullableParentId() : second.spanId.nullableParentId();
        assert operationName.equals(second.getOperationName()) : "span.operationName was " + second.getOperationName();
        assert !first.parent.isPresent();
        assert !second.serverTracer.isPresent();
        assert second.getBaggage().isEmpty();

        first.finish();
        second.finish();
    }

    @Test
    public void testGetTraceState() {
        BraveTracer tracer = new BraveTracer();
        BraveSpan span = (BraveSpan) tracer.buildSpan("test-testGetTraceState").start();
        
        Map<String, Object> state = tracer.getTraceState(span.context());
        assert state.containsKey(BraveHttpHeaders.TraceId.getName());
        assert state.containsKey(BraveHttpHeaders.SpanId.getName());
        assert state.containsKey(BraveHttpHeaders.Sampled.getName());

        assert state.get(BraveHttpHeaders.TraceId.getName())
                .equals(IdConversion.convertToString(span.spanId.traceId));

        assert state.get(BraveHttpHeaders.SpanId.getName())
                .equals(IdConversion.convertToString(span.spanId.spanId));

        span.finish();
    }

    @Test
    public void testGetBaggage() {
        String operationName = "test-testGetBaggage";
        BraveTracer tracer = new BraveTracer();

        BraveSpanBuilder builder = (BraveSpanBuilder) tracer.buildSpan(operationName);

        Span span = builder.withBaggageItem("getBaggage()-key-1", "getBaggage()-value-1").start();

        assert tracer.getBaggage(span).containsKey("getBaggage()-key-1");
        assert "getBaggage()-value-1".equals(tracer.getBaggage(span).get("getBaggage()-key-1"));

        span.finish();
    }

    @Test
    public void testInject() {
        String operationName = "test-testInject";
        BraveTracer tracer = new BraveTracer();
        BraveSpanBuilder builder = (BraveSpanBuilder) tracer.buildSpan(operationName);

        assert operationName.equals(builder.operationName) : builder.operationName;
        assert builder.references.isEmpty();
        assert null == builder.serverTracer : builder.serverTracer;

        BraveSpan span = builder.createSpan();

        assert null != span.spanId;
        assert 0 != span.spanId.spanId : span.spanId.spanId;
        assert 0 != span.spanId.traceId : span.spanId.traceId;
        assert null == span.spanId.nullableParentId() : span.spanId.nullableParentId();
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();

        Map<String,String> map = new HashMap<>();
        TextMapInjectAdapter adapter = new TextMapInjectAdapter(map);
        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, adapter);

        assert map.containsKey(BraveHttpHeaders.TraceId.getName());
        assert map.containsKey(BraveHttpHeaders.SpanId.getName());

        span.finish();
    }

    @Test
    public void testExtract() {

        Map<String,String> map = new HashMap<String,String>() {{
            put(BraveHttpHeaders.Sampled.getName(), "1");
            put(BraveHttpHeaders.TraceId.getName(), "123");
            put(BraveHttpHeaders.SpanId.getName(), "234");
        }};

        TextMapExtractAdapter adapter = new TextMapExtractAdapter(map);
        BraveTracer tracer = new BraveTracer();
        BraveSpanContext context = (BraveSpanContext) tracer.extract(Format.Builtin.TEXT_MAP, adapter);

        assert context.getContextTraceId() == 0x123 : context.getContextTraceId();
        assert context.getContextSpanId() == 0x234;
        assert context.getContextParentSpanId() == null : context.getContextParentSpanId();
    }

    @Test
    public void testExtractAsParent() throws Exception {
        Map<String,String> map = new HashMap<String,String>() {{
            put(BraveHttpHeaders.Sampled.getName(), "1");
            put(BraveHttpHeaders.TraceId.getName(), "123");
            put(BraveHttpHeaders.SpanId.getName(), "234");
        }};
        TextMapExtractAdapter adapter = new TextMapExtractAdapter(map);
        BraveTracer tracer = new BraveTracer();
        SpanContext parent = tracer.extract(Format.Builtin.TEXT_MAP, adapter);
        BraveSpan span = (BraveSpan) tracer.buildSpan("child").asChildOf(parent).start();
        
        BraveSpanContext childContext = (BraveSpanContext) span.context();
        assert childContext.getContextTraceId() == 0x123 : childContext.getContextTraceId();
        assert childContext.getContextSpanId() != 0;
        assert childContext.getContextParentSpanId() == 0x234 : childContext.getContextParentSpanId();
    }

    @Test
    public void testExtractOfNoParent() throws Exception {
        TextMapExtractAdapter adapter = new TextMapExtractAdapter(Collections.emptyMap());
        BraveTracer tracer = new BraveTracer();
        NoopSpanContext parent = (NoopSpanContext) tracer.extract(Format.Builtin.TEXT_MAP, adapter);

        assert NoopSpanContext.class.isAssignableFrom(parent.getClass())
                : "Expecting NoopSpanContext: " + parent.getClass();

        Span child = tracer.buildSpan("child").asChildOf(parent).start();
        assert NoopSpan.class.isAssignableFrom(child.getClass()) : "Expecting NoopSpan: " + child.getClass();
    }

    @Test
    public void test_stack() throws InterruptedException {

        BraveTracer tracer = new BraveTracer();
        tracer.brave.serverTracer().clearCurrentSpan();

        long start = System.currentTimeMillis() - 10000;

        // start a span
        try ( Span span0 = tracer.buildSpan("span-0")
                .withStartTimestamp(start)
                .withTag("description", "top level initial span in the original process")
                .start() ) {


            try ( Span span1 = tracer.buildSpan("span-1")
                    .withStartTimestamp(start +100)
                    .asChildOf(span0)
                    .withTag("description", "the first inner span in the original process")
                    .start() ) {

                assertChildToParent((BraveSpan) span1, (BraveSpan) span0, false);

                try ( Span span2 = tracer.buildSpan("span-2")
                        .withStartTimestamp(start +200)
                        .asChildOf(span1)
                        .withTag("description", "the second inner span in the original process")
                        .start() ) {

                    assertChildToParent((BraveSpan) span2, (BraveSpan) span1, false);

                    // cross process boundary
                    Map<String,String> map = new HashMap<>();
                    tracer.inject(span2.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(map));

                    SpanContext span3parent = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(map));
                    try ( Span span3 = tracer.buildSpan("span3op")
                            .withStartTimestamp(start +300)
                            .withTag("description", "the third inner span in the second process")
                            .asChildOf(span3parent)
                            .start() ) {

                        assertChildToParent((BraveSpan) span3, (BraveSpan) span2, true);

                        try ( Span span4 = tracer.buildSpan("span-4")
                                .withStartTimestamp(start +400)
                                .asChildOf(span3)
                                .withTag("description", "the fourth inner span in the second process")
                                .start() ) {

                            assertChildToParent((BraveSpan) span4, (BraveSpan) span3, false);

                            // cross process boundary
                            map = new HashMap<>();
                            tracer.inject(span4.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(map));

                            SpanContext span5parent = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(map));
                            try ( Span span5 = tracer.buildSpan("span5op")
                                    .withStartTimestamp(start +500)
                                    .withTag("description", "the fifth inner span in the third process")
                                    .asChildOf(span5parent)
                                    .start() ) {

                                assertChildToParent((BraveSpan) span5, (BraveSpan) span4, true);

                                try ( Span span6 = tracer.buildSpan("span-6")
                                        .withStartTimestamp(start +600)
                                        .asChildOf(span5)
                                        .withTag("description", "the sixth inner span in the third process")
                                        .start() ) {

                                    assertChildToParent((BraveSpan) span6, (BraveSpan) span5, false);

                                    try ( Span span7 = tracer.buildSpan("span-7")
                                            .withStartTimestamp(start +700)
                                            .asChildOf(span6)
                                            .withTag("description", "the seventh span in the third process")
                                            .start() ) {

                                        assertChildToParent((BraveSpan) span7, (BraveSpan) span6, false);

                                        // cross process boundary
                                        map = new HashMap<>();

                                        tracer.inject(
                                                span7.context(),
                                                Format.Builtin.TEXT_MAP,
                                                new TextMapInjectAdapter(map));

                                        SpanContext span8parent = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(map));
                                        try ( Span span8 = tracer.buildSpan("span8op")
                                                .withStartTimestamp(start +800)
                                                .withTag("description", "the eight inner span in the fourth process")
                                                .asChildOf(span8parent)
                                                .start() ) {

                                            assertChildToParent((BraveSpan) span8, (BraveSpan) span7, true);

                                            try ( Span span9 = tracer.buildSpan("span-9")
                                                    .withStartTimestamp(start +900)
                                                    .asChildOf(span8)
                                                    .withTag("description", "the ninth inner span in the fouth process")
                                                    .start() ) {

                                                assertChildToParent((BraveSpan) span9, (BraveSpan) span8, false);
                                                Thread.sleep(10);
                                            }
                                            Thread.sleep(10);
                                        }
                                        Thread.sleep(10);
                                    }
                                    Thread.sleep(10);
                                }
                                Thread.sleep(10);
                            }
                            Thread.sleep(10);
                        }
                        Thread.sleep(10);
                    }
                    Thread.sleep(10);
                }
                Thread.sleep(10);
            }
            Thread.sleep(10);
        }
    }
    
    @Test
    public void testIsTraceState() {
        BraveTracer tracer = new BraveTracer();
        String operationName = "test-testCreateSpan";

        for (BraveHttpHeaders header : BraveHttpHeaders.values()) {
            assert tracer.isTraceState(header.getName(), "any-value")
                    : header.getName() + " should be a trace state key";
        }

        assert !tracer.isTraceState("not-a-zipkin-header", "any-value");
    }
    
    @Test
    public void testParsingStateItems_withoutParent() {
        BraveTracer tracer = new BraveTracer();
        tracer.brave.serverTracer().clearCurrentSpan();
        
        Map<String, Object> stateItems = new HashMap<>();
        stateItems.put(BraveHttpHeaders.TraceId.getName(), "ff");
        stateItems.put(BraveHttpHeaders.SpanId.getName(), "aa");
        
        
        BraveSpanContext context = (BraveSpanContext) tracer.createSpanContext(stateItems, emptyMap());

        assert context.getBraveSpanId().traceId == 0xff;
        assert context.getBraveSpanId().spanId == 0xaa;
        assert context.getContextSpanId() == 0xaa;
        assert context.getContextTraceId() == 0xff;
        assert context.getBraveSpanId().nullableParentId() == null;
        assert context.serverTracer == tracer.brave.serverTracer();
        assert !context.baggageItems().iterator().hasNext();
    }
    
    @Test
    public void testParsingStateItems_withParent() {
        BraveTracer tracer = new BraveTracer();
        tracer.brave.serverTracer().clearCurrentSpan();
        
        Map<String, Object> stateItems = new HashMap<>();
        stateItems.put(BraveHttpHeaders.TraceId.getName(), "ff");
        stateItems.put(BraveHttpHeaders.SpanId.getName(), "aa");
        stateItems.put(BraveHttpHeaders.ParentSpanId.getName(), "cc");
        
        BraveSpanContext context = (BraveSpanContext) tracer.createSpanContext(stateItems, emptyMap());

        assert context.getBraveSpanId().traceId == 0xff;
        assert context.getBraveSpanId().spanId == 0xaa;
        assert context.getContextSpanId() == 0xaa;
        assert context.getContextTraceId() == 0xff;
        assert context.getBraveSpanId().nullableParentId() == 0xcc;
        assert context.serverTracer == tracer.brave.serverTracer();
        assert !context.baggageItems().iterator().hasNext();
    }

    private void assertChildToParent(BraveSpan span, BraveSpan parent, boolean extracted) {
        assert null != span.spanId;
        assert 0 != span.spanId.spanId : span.spanId.spanId;

        assert parent.spanId.traceId == span.spanId.traceId
                : "parent: " + parent.spanId.traceId + " ; child: " + span.spanId.traceId;

        assert parent.spanId.spanId == span.spanId.nullableParentId()
                : "parent: " + parent.spanId.spanId + " ; child: " + span.spanId.nullableParentId();

        assert extracted || span.parent.isPresent();
        assert extracted || span.parent.get().equals(parent.context());
        assert !extracted || span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();
    }
    
    private void assertCurrentLocalSpanIdEquals(BraveTracer tracer, SpanId spanId) {
        com.twitter.zipkin.gen.Span localSpan = tracer.brave.localSpanThreadBinder().getCurrentLocalSpan();
        assert localSpan != null : "localSpan is null";
        assert localSpan.getId() == spanId.spanId;
        assert localSpan.getTrace_id() == spanId.traceId;
        assert localSpan.getTrace_id_high() == spanId.traceIdHigh;
        assert Objects.equals(localSpan.getParent_id(), spanId.nullableParentId()) : localSpan.getParent_id();
    }
    
    private void assertCurrentServerSpanIdEquals(BraveTracer tracer, SpanId spanId) {
        ServerSpan serverSpan = tracer.brave.serverSpanThreadBinder().getCurrentServerSpan();
        assert serverSpan.getSpan() != null;
        assert serverSpan.getSpan().getId() == spanId.spanId : serverSpan.getSpan().getId() + " != " + spanId.spanId;
        assert serverSpan.getSpan().getTrace_id() == spanId.traceId;
        assert serverSpan.getSpan().getTrace_id_high() == spanId.traceIdHigh;
        assert Objects.equals(serverSpan.getSpan().getParent_id(), spanId.nullableParentId()) 
            : serverSpan.getSpan().getParent_id();
    }
    
    private void assertCurrentServerSpanIdIsNull(BraveTracer tracer) {
        assert tracer.brave.serverSpanThreadBinder().getCurrentServerSpan() == ServerSpan.EMPTY 
                : tracer.brave.serverSpanThreadBinder().getCurrentServerSpan();
    }

    private void assertCurrentLocalSpanIdIsNull(BraveTracer tracer) {
        assert tracer.brave.localSpanThreadBinder().getCurrentLocalSpan() == null 
                : tracer.brave.localSpanThreadBinder().getCurrentLocalSpan();
    }
    
    private void assertCurrentClientSpanIdIsNull(BraveTracer tracer) {
        assert tracer.brave.clientSpanThreadBinder().getCurrentClientSpan() == null
                : tracer.brave.clientSpanThreadBinder().getCurrentClientSpan();
    }
}
