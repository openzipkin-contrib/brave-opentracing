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

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class BraveTracer implements Tracer {
    /**
     * Return a new SpanBuilder for a Span with the given `operationName`.
     * <p>
     * <p>You can override the operationName later via {@link Span#setOperationName(String)}.
     * <p>
     * <p>A contrived example:
     * <pre>{@code
     * Tracer tracer = ...
     *
     * Span parentSpan = tracer.buildSpan("DoWork")
     * .start();
     *
     * Span http = tracer.buildSpan("HandleHTTPRequest")
     * .asChildOf(parentSpan.context())
     * .withTag("user_agent", req.UserAgent)
     * .withTag("lucky_number", 42)
     * .start();
     * }</pre>
     *
     * @param operationName
     */
    @Override
    public SpanBuilder buildSpan(String operationName) {
        return null;
    }

    /**
     * Inject a SpanContext into a `carrier` of a given type, presumably for propagation across process boundaries.
     * <p>
     * <p>Example:
     * <pre>{@code
     * Tracer tracer = ...
     * Span clientSpan = ...
     * TextMap httpHeadersCarrier = new AnHttpHeaderCarrier(httpRequest);
     * tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, httpHeadersCarrier);
     * }</pre>
     *
     * @param spanContext the SpanContext instance to inject into the carrier
     * @param format      the Format of the carrier
     * @param carrier     the carrier for the SpanContext state. All Tracer.inject() implementations must support io.opentracing.propagation.TextMap and java.nio.ByteBuffer.
     * @see Format
     * @see Format.Builtin
     */
    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {

    }

    /**
     * Extract a SpanContext from a `carrier` of a given type, presumably after propagation across a process boundary.
     * <p>
     * <p>Example:
     * <pre>{@code
     * Tracer tracer = ...
     * TextMap httpHeadersCarrier = new AnHttpHeaderCarrier(httpRequest);
     * SpanContext spanCtx = tracer.extract(Format.Builtin.HTTP_HEADERS, httpHeadersCarrier);
     * tracer.buildSpan('...').asChildOf(spanCtx).start();
     * }</pre>
     * <p>
     * If the span serialized state is invalid (corrupt, wrong version, etc) inside the carrier this will result in an
     * IllegalArgumentException.
     *
     * @param format  the Format of the carrier
     * @param carrier the carrier for the SpanContext state. All Tracer.extract() implementations must support
     *                io.opentracing.propagation.TextMap and java.nio.ByteBuffer.
     * @return the SpanContext instance holding context to create a Span.
     * @see Format
     * @see Format.Builtin
     */
    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        return null;
    }
}
