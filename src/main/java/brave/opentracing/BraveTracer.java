/**
 * Copyright 2016-2018 The OpenZipkin Authors
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

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Using a tracer, you can create a spans, inject span contexts into a transport, and extract span
 * contexts from a transport
 *
 * Here's an example:
 * <pre>
 *     Tracer tracer = BraveTracer.wrap(tracing);
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
  private final brave.Tracer brave4;
  private final BraveScopeManager scopeManager;

  /**
   * Returns an implementation of {@link Tracer} which delegates to the provided Brave {@link
   * Tracing} component, which coordinates with Brave's {@link CurrentTraceContext} to implement
   * {@linkplain ScopeManager}.
   */
  public static BraveTracer create(Tracing brave4) {
    return newBuilder(brave4).build();
  }

  /**
   * Returns a {@link Builder} configured with the provided Brave {@link Tracing} provided Brave
   * {@link Tracing} component and uses an instance of {@link BraveScopeManager} for its {@link
   * ScopeManager}.
   */
  public static Builder newBuilder(Tracing brave4) {
    return new Builder(brave4);
  }

  public static final class Builder {
    Tracing tracing;

    Map<Format<TextMap>, Propagation<String>> formatToPropagation = new LinkedHashMap<>();

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("brave tracing component == null");
      this.tracing = tracing;
      formatToPropagation.put(Format.Builtin.HTTP_HEADERS, tracing.propagation());
      formatToPropagation.put(Format.Builtin.TEXT_MAP, tracing.propagation());
    }

    /**
     * By default, {@link Format.Builtin#HTTP_HEADERS} and {@link Format.Builtin#TEXT_MAP} use the
     * propagation mechanism supplied by {@link Tracing#propagation()}, which defaults to {@link
     * Propagation#B3_STRING B3 Propagation}. You can override or add different formats using this
     * method.
     *
     * <p>For example, instead of using implicit format keys in your code, you might want to
     * explicitly declare you are using B3. To do so, you'd do setup the tracer like this:
     * <pre>{@code
     * builder.textMapPropagation(MyFormats.B3, Propagation.B3_STRING);
     *
     * // later, you can ensure B3 is used like this:
     * tracer.extract(MyFormats.B3, textMap);
     * }</pre>
     */
    // special named method because we can't overload later since both format and propagation only
    // differ on generic types. Punting on Format<ByteBuffer> until someone asks for it.
    public Builder textMapPropagation(Format<TextMap> format, Propagation<String> propagation) {
      if (format == null) throw new NullPointerException("format == null");
      if (propagation == null) throw new NullPointerException("propagation == null");
      formatToPropagation.put(format, propagation);
      return this;
    }

    public BraveTracer build() {
      return new BraveTracer(this);
    }
  }

  final Map<Format<TextMap>, Injector<TextMap>> formatToInjector = new LinkedHashMap<>();
  final Map<Format<TextMap>, Extractor<TextMap>> formatToExtractor = new LinkedHashMap<>();

  BraveTracer(Builder b) {
    brave4 = b.tracing.tracer();
    scopeManager = new BraveScopeManager(b.tracing);
    for (Map.Entry<Format<TextMap>, Propagation<String>> entry : b.formatToPropagation.entrySet()) {
      formatToInjector.put(entry.getKey(), entry.getValue().injector(TextMap::put));
      formatToExtractor.put(entry.getKey(), new TextMapExtractorAdaptor(entry.getValue()));
    }
  }

  @Override public BraveScopeManager scopeManager() {
    return scopeManager;
  }

  @Override public BraveSpan activeSpan() {
    Scope scope = this.scopeManager.active();
    return scope != null ? (BraveSpan) scope.span() : null;
  }

  @Override public BraveSpanBuilder buildSpan(String operationName) {
    return new BraveSpanBuilder(this, brave4, operationName);
  }

  /**
   * Injects the underlying context using B3 encoding by default.
   */
  @Override public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
    Injector<TextMap> injector = formatToInjector.get(format);
    if (injector == null) {
      throw new UnsupportedOperationException(format + " not in " + formatToInjector.keySet());
    }
    TraceContext traceContext = ((BraveSpanContext) spanContext).unwrap();
    injector.inject(traceContext, (TextMap) carrier);
  }

  /**
   * Extracts the underlying context using B3 encoding by default. Null is returned when there is no
   * encoded context in the carrier, or upon error extracting it.
   */
  @Override public <C> BraveSpanContext extract(Format<C> format, C carrier) {
    Extractor<TextMap> extractor = formatToExtractor.get(format);
    if (extractor == null) {
      throw new UnsupportedOperationException(format + " not in " + formatToExtractor.keySet());
    }
    TraceContextOrSamplingFlags extractionResult = extractor.extract((TextMap) carrier);
    return BraveSpanContext.create(extractionResult);
  }

  /**
   * Eventhough TextMap is named like Map, it doesn't have a retrieve-by-key method Lookups will be
   * case insensitive
   */
  static final class TextMapExtractorAdaptor implements Extractor<TextMap> {
    final Set<String> fields;
    final Extractor<Map<String, String>> delegate;

    TextMapExtractorAdaptor(Propagation<String> propagation) {
      fields = lowercaseSet(propagation.keys());
      delegate = propagation.extractor((m, k) -> m.get(k.toLowerCase(Locale.ROOT)));
    }

    @Override public TraceContextOrSamplingFlags extract(TextMap entries) {
      Map<String, String> cache = new LinkedHashMap<>();
      for (Iterator<Map.Entry<String, String>> it = entries.iterator(); it.hasNext(); ) {
        Map.Entry<String, String> next = it.next();
        String inputKey = next.getKey().toLowerCase(Locale.ROOT);
        if (fields.contains(inputKey)) {
          cache.put(inputKey, next.getValue());
        }
      }
      return delegate.extract(cache);
    }
  }

  static Set<String> lowercaseSet(List<String> fields) {
    Set<String> lcSet = new LinkedHashSet<>();
    for (String f : fields) {
      lcSet.add(f.toLowerCase());
    }
    return lcSet;
  }
}
