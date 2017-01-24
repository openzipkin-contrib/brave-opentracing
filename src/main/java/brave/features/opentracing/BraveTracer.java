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

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.twitter.zipkin.gen.Endpoint;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class BraveTracer implements Tracer {

    final Brave brave;

    public BraveTracer() {
        this(Endpoint.create("unknown", 0));
    }

    public BraveTracer(Endpoint endpoint) {
        this(new Brave.Builder(new InheritableServerClientAndLocalSpanState(endpoint)));
    }

    public BraveTracer(Brave.Builder builder) {
        brave = builder.build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new BraveSpanBuilder(operationName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        // TODO How to handle `Formats` and `Carriers` for brave
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        // TODO How to handle `Formats` and `Carriers` for brave
        return null;
    }
}
