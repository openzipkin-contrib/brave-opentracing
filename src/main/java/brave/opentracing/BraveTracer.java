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

import brave.internal.Nullable;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Using a tracer, you can create a spans, inject span contexts into a transport, and extract span
 * contexts from a transport
 *
 * Here's an example:
 * <pre>
 *     Tracer tracer = BraveTracer.wrap(brave4);
 *
 *     Span span = tracer.buildSpan("DoWork").start();
 *     tracer.inject(span.context());
 *
 *     ...
 *
 *     SpanContext clientContext = tracer.extract(Format.Builtin.HTTP_HEADERS,
 * request.getHeaders());
 *     Span clientSpan = tracer.buildSpan('...').asChildOf(clientContext).start();
 * </pre>
 *
 * @see BraveSpan
 * @see Propagation
 */
public final class BraveTracer implements Tracer {

  static final List<String> PROPAGATION_KEYS = Propagation.B3_STRING.keys();
  static final TraceContext.Injector<TextMap> INJECTOR =
      Propagation.B3_STRING.injector(TextMap::put);
  static final TraceContext.Extractor<TextMapView> EXTRACTOR =
      Propagation.B3_STRING.extractor(TextMapView::get);
  static final Set<String> FIELDS_LOWER_CASE = lowercaseSet(PROPAGATION_KEYS);

  private final brave.Tracer brave4;

  /**
   * Returns an implementation of {@linkplain io.opentracing.Tracer} which delegates
   * the the provided Brave Tracer.
   */
  public static BraveTracer wrap(brave.Tracer brave4) {
    if (brave4 == null) throw new NullPointerException("brave tracer == null");
    return new BraveTracer(brave4);
  }

  private BraveTracer(brave.Tracer brave4) {
    this.brave4 = brave4;
  }

  /**
   * {@inheritDoc}
   */
  @Override public SpanBuilder buildSpan(String operationName) {
    return new BraveSpanBuilder(brave4, operationName);
  }

  /**
   * Injects the underlying context using B3 encoding.
   */
  @Override public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
    if (format != Format.Builtin.HTTP_HEADERS && format != Format.Builtin.TEXT_MAP) {
      throw new UnsupportedOperationException(format.toString()
          + " != Format.Builtin.HTTP_HEADERS or Format.Builtin.TEXT_MAP");
    }
    TraceContext traceContext = ((BraveSpanContext) spanContext).unwrap();
    INJECTOR.inject(traceContext, (TextMap) carrier);
  }

  /**
   * Extracts the underlying context using B3 encoding. A new trace context is provisioned when
   * there is no B3-encoded context in the carrier, or upon error extracting it.
   */
  @Override public <C> SpanContext extract(Format<C> format, C carrier) {
    if (format != Format.Builtin.HTTP_HEADERS && format != Format.Builtin.TEXT_MAP) {
      throw new UnsupportedOperationException(format.toString()
          + " != Format.Builtin.HTTP_HEADERS or Format.Builtin.TEXT_MAP");
    }
    TraceContextOrSamplingFlags result =
        EXTRACTOR.extract(new TextMapView(FIELDS_LOWER_CASE, (TextMap) carrier));
    TraceContext context = result.context() != null
        ? result.context()
        : brave4.newTrace(result.samplingFlags()).context();
    return BraveSpanContext.wrap(context);
  }

  static Set<String> lowercaseSet(List<String> fields) {
    Set<String> lcSet = new LinkedHashSet<>();
    for (String f : fields) {
      lcSet.add(f.toLowerCase());
    }
    return lcSet;
  }

  /**
   * Eventhough TextMap is named like Map, it doesn't have a retrieve-by-key method
   * Lookups will be case insensitive
   */
  static final class TextMapView {
    final Iterator<Map.Entry<String, String>> input;
    final Map<String, String> cache = new LinkedHashMap<>();
    final Set<String> fields;

    TextMapView(Set<String> fields, TextMap input) {
      this.input = input.iterator();
      this.fields = fields;
    }

    @Nullable String get(String key) {
      while (input.hasNext()) {
        Map.Entry<String, String> next = input.next();
        String inputKey = next.getKey().toLowerCase();
        if (fields.contains(inputKey)) {
          cache.put(inputKey, next.getValue());
        }
      }
      return cache.get(key.toLowerCase());
    }
  }
}
