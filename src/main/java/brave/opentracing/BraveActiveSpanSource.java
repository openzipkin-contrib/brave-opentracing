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
package brave.opentracing;

import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import io.opentracing.ActiveSpan;
import io.opentracing.ActiveSpanSource;
import io.opentracing.Span;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the active Brave span.
 *
 * Note that it is important to use the OpenTracing API exclusively to activate spans. If you fail to do this, querying
 * for the active span using the
 * OpenTracing API will throw an exception.
 */
public class BraveActiveSpanSource implements ActiveSpanSource {
    private final Tracer tracer;

    public BraveActiveSpanSource(Tracing brave4) {
        tracer = brave4.tracer();
    }

    /**
     * @return the current ActiveSpan. Note that calling {@link ActiveSpan#deactivate()} or {@link ActiveSpan#close()}
     * on the result does nothing; to close/deactivate, call one of those methods on the reference returned by
     * {@link #makeActive(Span)}.
     */
    @Override
    public ActiveSpan activeSpan() {
        brave.Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        return new BraveActiveSpan(tracer.withSpanInScope(span),
                                   BraveSpan.wrap(span),
                                   new AtomicInteger(1),
                                   false);
    }

    @Override
    public ActiveSpan makeActive(Span span) {
        if (span == null) {
            return null;
        }

        if (span instanceof BraveSpan) {
            BraveSpan wrappedSpan = (BraveSpan) span;
            brave.Span rawSpan = wrappedSpan.unwrap();
            SpanInScope spanInScope = tracer.withSpanInScope(rawSpan);
            return new BraveActiveSpan(spanInScope,
                                       wrappedSpan,
                                       new AtomicInteger(1),
                                       true);
        }

        throw new IllegalArgumentException("Span must be an instance of brave.opentracing.BraveSpan, but was " + span.getClass());
    }
}
