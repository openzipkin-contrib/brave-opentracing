/**
 * Copyright 2016-2017 The OpenZipkin Authors
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
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public final class BraveSpanTest {

    private List<zipkin.Span> spans = new ArrayList<>();
    // -1062731775 = 192.168.0.1
    private Brave brave = new Brave.Builder(-1062731775, 8080, "unknown")
        .reporter(spans::add).build();

    @Before
    public void setup() {
        ThreadLocalServerClientAndLocalSpanState.clear();
    }

    @Test
    public void test() {
        String operationName = "test-test";
        Optional<BraveSpanContext> parent = Optional.empty();
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.empty();

        BraveSpan span = BraveSpan.create(brave, operationName, parent, start, serverTracer);

        assert null != span.spanId;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();

        span.finish();

        assert null != span.spanId;
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

        BraveSpan span = BraveSpan.create(brave, operationName, parent, start, serverTracer);

        assert null != span.spanId;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert !span.parent.isPresent();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();

        span.finish();

        assert null != span.spanId;
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
                BraveSpan.create(brave, operationName, Optional.empty(), start.minusMillis(100), serverTracer));

        BraveSpan span = BraveSpan.create(brave, operationName, parent, start, serverTracer);

        assert null != span.spanId;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert !span.serverTracer.isPresent();
        assert span.getBaggage().isEmpty();

        span.finish();

        assert null != span.spanId;
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
                BraveSpan.create(brave, operationName, Optional.empty(), start.minusMillis(100), serverTracer));

        BraveSpan span = BraveSpan.create(brave, operationName, parent, start, serverTracer);

        assert null != span.spanId;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();

        span.finish();

        assert null != span.spanId;
        assert operationName.equals(span.getOperationName()) : "span.operationName was " + span.getOperationName();
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.getBaggage().isEmpty();
    }

}
