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

import brave.propagation.TraceContext;
import io.opentracing.SpanContext;

import java.util.Collections;
import java.util.Map;

public class BraveSpanContext implements SpanContext {

    private TraceContext traceContext;

    static BraveSpanContext wrap(TraceContext traceContext) {
        return new BraveSpanContext(traceContext);
    }

    private BraveSpanContext(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    final TraceContext unwrap() {
        return traceContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        // brave doesn't support baggage
        return Collections.EMPTY_SET;
    }
}
