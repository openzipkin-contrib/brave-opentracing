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

import brave.internal.Nullable;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.TracerAdapter;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BraveTracer implements Tracer {

    static final List<String> PROPAGATION_KEYS = Propagation.B3_STRING.keys();
    static final TraceContext.Injector<TextMap> INJECTOR = Propagation.B3_STRING.injector(TextMap::put);
    static final TraceContext.Extractor<TextMapView> EXTRACTOR = Propagation.B3_STRING.extractor(TextMapView::get);

    private brave.Tracer brave4;
    private Brave brave3;

    static BraveTracer wrap(brave.Tracer brave4) {
        if (brave4 == null) throw new NullPointerException("brave tracer == null");
        return new BraveTracer(brave4);
    }

    BraveTracer(brave.Tracer brave4) {
        this.brave4 = brave4;
        this.brave3 = TracerAdapter.newBrave(this.brave4);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new BraveSpanBuilder(brave4, operationName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        if (format != Format.Builtin.HTTP_HEADERS) {
            throw new UnsupportedOperationException(format + " != Format.Builtin.HTTP_HEADERS");
        }
        TraceContext traceContext = ((BraveSpanContext) spanContext).unwrap();
        INJECTOR.inject(traceContext, (TextMap) carrier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        if (format != Format.Builtin.HTTP_HEADERS) {
            throw new UnsupportedOperationException(format.toString());
        }
        TraceContextOrSamplingFlags result =
                EXTRACTOR.extract(new TextMapView(PROPAGATION_KEYS, (TextMap) carrier));
        TraceContext context = result.context() != null
                ? result.context().toBuilder().shared(true).build()
                : brave4.newTrace(result.samplingFlags()).context();
        return BraveSpanContext.wrap(context);
    }


    /**
     * Eventhough TextMap is named like Map, it doesn't have a retrieve-by-key method
     */
    static final class TextMapView {
        final Iterator<Map.Entry<String, String>> input;
        final Map<String, String> cache = new LinkedHashMap<>();
        final List<String> fields;

        TextMapView(List<String> fields, TextMap input) {
            this.fields = fields;
            this.input = input.iterator();
        }

        @Nullable
        String get(String key) {
            String result = cache.get(key);
            if (result != null) return result;
            while (input.hasNext()) {
                Map.Entry<String, String> next = input.next();
                if (next.getKey().equals(key)) {
                    return next.getValue();
                } else if (fields.contains(next.getKey())) {
                    cache.put(next.getKey(), next.getValue());
                }
            }
            return null;
        }
    }
}
