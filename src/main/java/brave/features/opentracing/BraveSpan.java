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
package brave.features.opentracing;

import io.opentracing.Span;
import io.opentracing.SpanContext;

import java.util.Map;

public class BraveSpan implements Span {
    /**
     * Retrieve the associated SpanContext.
     * <p>
     * This may be called at any time, including after calls to finish().
     *
     * @return the SpanContext that encapsulates Span state that should propagate across process boundaries.
     */
    @Override
    public SpanContext context() {
        return null;
    }

    /**
     * Sets the end timestamp to now and records the span.
     * <p>
     * <p>With the exception of calls to Span.context(), this should be the last call made to the span instance, and to
     * do otherwise leads to undefined behavior.
     *
     * @see Span#context()
     */
    @Override
    public void finish() {

    }

    /**
     * Sets an explicit end timestamp and records the span.
     * <p>
     * <p>With the exception of calls to Span.context(), this should be the last call made to the span instance, and to
     * do otherwise leads to undefined behavior.
     *
     * @param finishMicros an explicit finish time, in microseconds since the epoch
     * @see Span#context()
     */
    @Override
    public void finish(long finishMicros) {

    }

    @Override
    public void close() {

    }

    /**
     * Set a key:value tag on the Span.
     *
     * @param key
     * @param value
     */
    @Override
    public Span setTag(String key, String value) {
        return null;
    }

    /**
     * Same as {@link #setTag(String, String)}, but for boolean values.
     *
     * @param key
     * @param value
     */
    @Override
    public Span setTag(String key, boolean value) {
        return null;
    }

    /**
     * Same as {@link #setTag(String, String)}, but for numeric values.
     *
     * @param key
     * @param value
     */
    @Override
    public Span setTag(String key, Number value) {
        return null;
    }

    /**
     * Log key:value pairs to the Span with the current walltime timestamp.
     * <p>
     * <p><strong>CAUTIONARY NOTE:</strong> not all Tracer implementations support key:value log fields end-to-end.
     * Caveat emptor.
     * <p>
     * <p>A contrived example (using Guava, which is not required):
     * <pre>{@code
     * span.log(
     * ImmutableMap.Builder<String, Object>()
     * .put("event", "soft error")
     * .put("type", "cache timeout")
     * .put("waited.millis", 1500)
     * .build());
     * }</pre>
     *
     * @param fields key:value log fields. Tracer implementations should support String, numeric, and boolean values;
     *               some may also support arbitrary Objects.
     * @return the Span, for chaining
     * @see Span#log(String)
     */
    @Override
    public Span log(Map<String, ?> fields) {
        return null;
    }

    /**
     * Like log(Map&lt;String, Object&gt;), but with an explicit timestamp.
     * <p>
     * <p><strong>CAUTIONARY NOTE:</strong> not all Tracer implementations support key:value log fields end-to-end.
     * Caveat emptor.
     *
     * @param timestampMicroseconds The explicit timestamp for the log record. Must be greater than or equal to the
     *                              Span's start timestamp.
     * @param fields                key:value log fields. Tracer implementations should support String, numeric, and boolean values;
     *                              some may also support arbitrary Objects.
     * @return the Span, for chaining
     * @see Span#log(long, String)
     */
    @Override
    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        return null;
    }

    /**
     * Record an event at the current walltime timestamp.
     * <p>
     * Shorthand for
     * <p>
     * <pre>{@code
     * span.log(Collections.singletonMap("event", event));
     * }</pre>
     *
     * @param event the event value; often a stable identifier for a moment in the Span lifecycle
     * @return the Span, for chaining
     */
    @Override
    public Span log(String event) {
        return null;
    }

    /**
     * Record an event at a specific timestamp.
     * <p>
     * Shorthand for
     * <p>
     * <pre>{@code
     * span.log(timestampMicroseconds, Collections.singletonMap("event", event));
     * }</pre>
     *
     * @param timestampMicroseconds The explicit timestamp for the log record. Must be greater than or equal to the
     *                              Span's start timestamp.
     * @param event                 the event value; often a stable identifier for a moment in the Span lifecycle
     * @return the Span, for chaining
     */
    @Override
    public Span log(long timestampMicroseconds, String event) {
        return null;
    }

    /**
     * Sets a baggage item in the Span (and its SpanContext) as a key/value pair.
     * <p>
     * Baggage enables powerful distributed context propagation functionality where arbitrary application data can be
     * carried along the full path of request execution throughout the system.
     * <p>
     * Note 1: Baggage is only propagated to the future (recursive) children of this SpanContext.
     * <p>
     * Note 2: Baggage is sent in-band with every subsequent local and remote calls, so this feature must be used with
     * care.
     *
     * @param key
     * @param value
     * @return this Span instance, for chaining
     */
    @Override
    public Span setBaggageItem(String key, String value) {
        return null;
    }

    /**
     * @param key
     * @return the value of the baggage item identified by the given key, or null if no such item could be found
     */
    @Override
    public String getBaggageItem(String key) {
        return null;
    }

    /**
     * Sets the string name for the logical operation this span represents.
     *
     * @param operationName
     * @return this Span instance, for chaining
     */
    @Override
    public Span setOperationName(String operationName) {
        return null;
    }

    /**
     * @param eventName
     * @param payload
     * @deprecated use {@link #log(Map)} like this
     * {@code span.log(Map.of("event", "timeout"))}
     * or
     * {@code span.log(timestampMicroseconds, Map.of("event", "exception", "payload", stackTrace))}
     */
    @Override
    public Span log(String eventName, Object payload) {
        return null;
    }

    /**
     * @param timestampMicroseconds
     * @param eventName
     * @param payload
     * @deprecated use {@link #log(Map)} like this
     * {@code span.log(timestampMicroseconds, Map.of("event", "timeout"))}
     * or
     * {@code span.log(timestampMicroseconds, Map.of("event", "exception", "payload", stackTrace))}
     */
    @Override
    public Span log(long timestampMicroseconds, String eventName, Object payload) {
        return null;
    }
}
