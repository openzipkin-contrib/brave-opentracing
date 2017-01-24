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
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class BraveTracer implements Tracer {

    private brave.Tracer brave;
    private TraceContext.Injector injector;
    private TraceContext.Extractor extractor;

    /**
     * {@inheritDoc}
     */
    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new BraveSpanBuilder(brave, operationName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        // TODO select an injector for the specific FORMAT

        injector.inject(((BraveSpanContext) spanContext).unwrap(), carrier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        // TODO select an extractor for the specific FORMAT

        TraceContextOrSamplingFlags contextOrSamplingFlags = extractor.extract(carrier);

        return contextOrSamplingFlags.context() != null
                ? BraveSpanContext.wrap(contextOrSamplingFlags.context())
                : null;
    }
}
