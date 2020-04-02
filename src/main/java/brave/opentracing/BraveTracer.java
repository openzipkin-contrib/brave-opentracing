/*
 * Copyright 2016-2020 The OpenZipkin Authors
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
import brave.baggage.BaggageField;
import brave.internal.InternalBaggage;
import brave.propagation.B3SingleFormat;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.BinaryExtract;
import io.opentracing.propagation.BinaryInject;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.opentracing.propagation.Format.Builtin.BINARY;
import static io.opentracing.propagation.Format.Builtin.BINARY_EXTRACT;
import static io.opentracing.propagation.Format.Builtin.BINARY_INJECT;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP_EXTRACT;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP_INJECT;

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
 * <h3>Propagation</h3>
 * This uses the same propagation as defined in zipkin for text formats. <a
 * href="https://github.com/openzipkin/b3-propagation#single-header">B3 Single</a> is used for
 * binary formats.
 *
 * @see BraveSpan
 * @see Propagation
 */
public final class BraveTracer implements Tracer {
  final Tracing tracing;
  final brave.Tracer delegate;
  final BraveScopeManager scopeManager;

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
    // This is the only public entrypoint into the brave-opentracing bridge. The following will
    // raise an exception when using an incompatible version of opentracing-api. Notably, this
    // unwraps ExceptionInInitializerError to avoid confusing users, as this is an implementation
    // detail of the version singleton.
    try {
      OpenTracingVersion.get();
    } catch (ExceptionInInitializerError e) {
      if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
      throw e;
    }
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

  final Map<Format<?>, Injector<?>> formatToInjector = new LinkedHashMap<>();
  final Map<Format<?>, Extractor<?>> formatToExtractor = new LinkedHashMap<>();

  BraveTracer(Builder b) {
    tracing = b.tracing;
    delegate = b.tracing.tracer();
    scopeManager = OpenTracingVersion.get().scopeManager(b.tracing);
    BaggageField.create("foo"); // ensure the below instance exists
    Set<String> allKeyNames = InternalBaggage.instance.allKeyNames(tracing.propagationFactory());
    for (Map.Entry<Format<TextMap>, Propagation<String>> entry : b.formatToPropagation.entrySet()) {
      formatToInjector.put(entry.getKey(), entry.getValue().injector(TEXT_MAP_SETTER));
      formatToExtractor.put(entry.getKey(), new ExtractorAdaptor(entry.getValue(), allKeyNames));
    }

    // Now, go back and make sure the special inject/extract forms work
    for (Propagation<String> propagation : b.formatToPropagation.values()) {
      formatToInjector.put(TEXT_MAP_INJECT, propagation.injector(TEXT_MAP_SETTER));
      formatToExtractor.put(TEXT_MAP_EXTRACT, new ExtractorAdaptor(propagation, allKeyNames));
    }

    // Finally add binary support
    formatToInjector.put(BINARY, BinaryCodec.INSTANCE);
    formatToInjector.put(BINARY_INJECT, BinaryCodec.INSTANCE);
    formatToExtractor.put(BINARY, BinaryCodec.INSTANCE);
    formatToExtractor.put(BINARY_EXTRACT, BinaryCodec.INSTANCE);
  }

  /** Returns the underlying {@link Tracing} instance used to configure this. */
  public Tracing unwrap() {
    return tracing;
  }

  @Override public BraveScopeManager scopeManager() {
    return scopeManager;
  }

  @Override public BraveSpan activeSpan() {
    return scopeManager.activeSpan();
  }

  @Override public BraveScope activateSpan(Span span) {
    return scopeManager.activate(span);
  }

  @Override public BraveSpanBuilder buildSpan(String operationName) {
    return OpenTracingVersion.get().spanBuilder(this, operationName);
  }

  /**
   * Injects the underlying context using B3 encoding by default.
   */
  @Override public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
    Injector<C> injector = (Injector<C>) formatToInjector.get(format);
    if (injector == null) {
      throw new UnsupportedOperationException(format + " not in " + formatToInjector.keySet());
    }
    TraceContext traceContext = ((BraveSpanContext) spanContext).unwrap();
    injector.inject(traceContext, carrier);
  }

  /**
   * Extracts the underlying context using B3 encoding by default. Null is returned when there is no
   * encoded context in the carrier, or upon error extracting it.
   */
  @Override public <C> BraveSpanContext extract(Format<C> format, C carrier) {
    Extractor<C> extractor = (Extractor<C>) formatToExtractor.get(format);
    if (extractor == null) {
      throw new UnsupportedOperationException(format + " not in " + formatToExtractor.keySet());
    }
    TraceContextOrSamplingFlags extractionResult = extractor.extract(carrier);
    return BraveSpanContext.create(extractionResult);
  }

  @Override public void close() {
    tracing.close();
  }

  static final Setter<TextMap, String> TEXT_MAP_SETTER = new Setter<TextMap, String>() {
    @Override public void put(TextMap carrier, String key, String value) {
      carrier.put(key, value);
    }

    @Override public String toString() {
      return "TextMap::put";
    }
  };

  static final Getter<Map<String, String>, String> LC_MAP_GETTER =
      new Getter<Map<String, String>, String>() {
        @Override public String get(Map<String, String> carrier, String key) {
          return carrier.get(key.toLowerCase(Locale.ROOT));
        }

        @Override public String toString() {
          return "Map::getLowerCase";
        }
      };

  /**
   * Eventhough TextMap is named like Map, it doesn't have a retrieve-by-key method.
   *
   * <p>See https://github.com/opentracing/opentracing-java/issues/305
   */
  static final class ExtractorAdaptor implements Extractor<TextMap> {
    final Set<String> allNames;
    final Extractor<Map<String, String>> delegate;

    ExtractorAdaptor(Propagation<String> propagation, Set<String> allNames) {
      this.allNames = allNames;
      this.delegate = propagation.extractor(LC_MAP_GETTER);
    }

    /** Performs case-insensitive lookup */
    @Override public TraceContextOrSamplingFlags extract(TextMap entries) {
      Map<String, String> cache = new LinkedHashMap<>();
      for (Iterator<Map.Entry<String, String>> it = entries.iterator(); it.hasNext(); ) {
        Map.Entry<String, String> next = it.next();
        String inputKey = next.getKey().toLowerCase(Locale.ROOT);
        if (allNames.contains(inputKey)) {
          cache.put(inputKey, next.getValue());
        }
      }
      return delegate.extract(cache);
    }
  }

  // Temporary until https://github.com/openzipkin/brave/issues/928
  enum BinaryCodec implements Injector<BinaryInject>, Extractor<BinaryExtract> {
    INSTANCE;

    final Charset ascii = Charset.forName("US-ASCII");

    @Override public TraceContextOrSamplingFlags extract(BinaryExtract binaryExtract) {
      try {
        return B3SingleFormat.parseB3SingleFormat(ascii.decode(binaryExtract.extractionBuffer()));
      } catch (RuntimeException e) {
        return TraceContextOrSamplingFlags.EMPTY;
      }
    }

    @Override public void inject(TraceContext traceContext, BinaryInject binaryInject) {
      byte[] injected = B3SingleFormat.writeB3SingleFormatAsBytes(traceContext);
      binaryInject.injectionBuffer(injected.length).put(injected);
    }
  }
}
