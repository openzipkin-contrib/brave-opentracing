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

import brave.Span;
import brave.Tracer.SpanInScope;
import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link BraveActiveSpan} is a simple {@link ActiveSpan} implementation that wraps the corresponding BraveSpan.
 *
 * @see BraveSpan
 * @see BraveActiveSpanSource
 * @see Tracer#activeSpan()
 */
public class BraveActiveSpan implements ActiveSpan {
    private final SpanInScope scope;
    private final BraveSpan wrapped;
    private final AtomicInteger refCount;
    private final boolean original;

    /**
     * @param scope a SpanInScope to be closed upon deactivation of this ActiveSpan
     * @param wrapped the wrapped BraveSpan to which we will delegate all span operations
     * @param refCount the total number of Continuations of this ActiveSpan (new instances should pass 1)
     * @param original whether this ActiveSpan instance was created when the span was started (if false, deactivate is a no-op)
     */
    BraveActiveSpan(SpanInScope scope, BraveSpan wrapped, AtomicInteger refCount, boolean original) {
        this.scope = scope;
        this.wrapped = wrapped;
        this.refCount = refCount;
        this.original = original;
    }

    @Override
    public void deactivate() {
        if (original) {
            if (0 == refCount.decrementAndGet()) {
                wrapped.finish();
                scope.close();
            }
        }
    }

    @Override
    public BraveActiveSpan.Continuation capture() {
        return new BraveActiveSpan.Continuation();
    }

    @Override
    public void close() {
        deactivate();
    }

    public Span unwrap() {
        return wrapped.unwrap();
    }

    @Override
    public SpanContext context() {
        return wrapped.context();
    }

    @Override
    public BraveActiveSpan setTag(String key, String value) {
        wrapped.setTag(key, value);
        return this;
    }

    @Override
    public BraveActiveSpan setTag(String key, boolean value) {
        wrapped.setTag(key, value);
        return this;
    }

    @Override
    public BraveActiveSpan setTag(String key, Number value) {
        wrapped.setTag(key, value);
        return this;
    }

    @Override
    public BraveActiveSpan log(Map<String, ?> fields) {
        wrapped.log(fields);
        return this;
    }

    @Override
    public BraveActiveSpan log(long timestampMicroseconds, Map<String, ?> fields) {
        wrapped.log(timestampMicroseconds, fields);
        return this;
    }

    public static String toAnnotation(Map<String, ?> fields) {
        return BraveSpan.toAnnotation(fields);
    }

    public static String joinOnEqualsSpace(Map<String, ?> fields) {
        return BraveSpan.joinOnEqualsSpace(fields);
    }

    @Override
    public BraveActiveSpan log(String event) {
        wrapped.log(event);
        return this;
    }

    @Override
    public BraveActiveSpan log(long timestampMicroseconds, String event) {
        wrapped.log(timestampMicroseconds, event);
        return this;
    }

    @Override
    public BraveActiveSpan log(String eventName, Object ignored) {
        wrapped.log(eventName, ignored);
        return this;
    }

    @Override
    public BraveActiveSpan log(long timestampMicroseconds, String eventName, Object ignored) {
        wrapped.log(timestampMicroseconds, eventName, ignored);
        return this;
    }

    @Override
    public BraveActiveSpan setBaggageItem(String key, String value) {
        wrapped.setBaggageItem(key, value);
        return this;
    }

    @Override
    public String getBaggageItem(String key) {
        return wrapped.getBaggageItem(key);
    }

    @Override
    public BraveActiveSpan setOperationName(String operationName) {
        wrapped.setOperationName(operationName);
        return this;
    }

    public class Continuation implements ActiveSpan.Continuation {
        Continuation() {
            refCount.incrementAndGet();
        }

        @Override
        public ActiveSpan activate() {
            return new BraveActiveSpan(scope, wrapped, refCount, original);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BraveActiveSpan that = (BraveActiveSpan) o;
        return Objects.equals(scope, that.scope) &&
               Objects.equals(wrapped, that.wrapped) &&
               Objects.equals(refCount, that.refCount) &&
               Objects.equals(original, that.original);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, wrapped, refCount, original);
    }

    @Override
    public String toString() {
        return "BraveActiveSpan{" + "scope=" + scope +
               ", wrapped=" + wrapped +
               ", refCount=" + refCount +
               ", original=" + original +
               '}';
    }
}
