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

import brave.Span.Kind;
import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.opentracing.TextMapPropagation.TextMapExtractor;
import brave.propagation.B3SingleFormat;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
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
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

  final Map<Format<?>, Injector<TextMapInject>> formatToInjector = new LinkedHashMap<>();
  final Map<Format<?>, Injector<TextMapInject>> formatToClientInjector = new LinkedHashMap<>();
  final Map<Format<?>, Injector<TextMapInject>> formatToProducerInjector = new LinkedHashMap<>();
  final Map<Format<?>, Injector<TextMapInject>> formatToConsumerInjector = new LinkedHashMap<>();
  final Map<Format<?>, Extractor<TextMapExtract>> formatToExtractor = new LinkedHashMap<>();

  BraveTracer(Builder b) {
    tracing = b.tracing;
    delegate = b.tracing.tracer();
    scopeManager = OpenTracingVersion.get().scopeManager(b.tracing);
    Set<String> lcPropagationKeys = new LinkedHashSet<>();
    for (String keyName : BaggagePropagation.allKeyNames(tracing.propagation())) {
      lcPropagationKeys.add(keyName.toLowerCase(Locale.ROOT));
    }
    for (Map.Entry<Format<TextMap>, Propagation<String>> entry : b.formatToPropagation.entrySet()) {
      formatToInjector.put(entry.getKey(),
          entry.getValue().injector(TextMapPropagation.SETTER));
      formatToClientInjector.put(entry.getKey(),
          entry.getValue().injector(TextMapPropagation.REMOTE_SETTER.CLIENT));
      formatToProducerInjector.put(entry.getKey(),
          entry.getValue().injector(TextMapPropagation.REMOTE_SETTER.PRODUCER));
      formatToConsumerInjector.put(entry.getKey(),
          entry.getValue().injector(TextMapPropagation.REMOTE_SETTER.CONSUMER));
      formatToExtractor.put(entry.getKey(),
          new TextMapExtractor(entry.getValue(), lcPropagationKeys, TextMapPropagation.GETTER));
    }

    // Now, go back and make sure the special inject/extract forms work
    for (Propagation<String> propagation : b.formatToPropagation.values()) {
      formatToInjector.put(TEXT_MAP_INJECT,
          propagation.injector(TextMapPropagation.SETTER));
      formatToClientInjector.put(TEXT_MAP_INJECT,
          propagation.injector(TextMapPropagation.REMOTE_SETTER.CLIENT));
      formatToProducerInjector.put(TEXT_MAP_INJECT,
          propagation.injector(TextMapPropagation.REMOTE_SETTER.PRODUCER));
      formatToConsumerInjector.put(TEXT_MAP_INJECT,
          propagation.injector(TextMapPropagation.REMOTE_SETTER.CONSUMER));
      formatToExtractor.put(TEXT_MAP_EXTRACT,
          new TextMapExtractor(propagation, lcPropagationKeys, TextMapPropagation.GETTER));
    }
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
    BraveSpanContext braveContext = ((BraveSpanContext) spanContext);
    if (carrier instanceof BinaryInject) {
      BinaryCodec.INSTANCE.inject(braveContext.unwrap(), (BinaryInject) carrier);
      return;
    }
    if (!(carrier instanceof TextMapInject)) {
      throw new UnsupportedOperationException(carrier + " not instanceof TextMapInject");
    }
    Kind kind = braveContext.kind;
    Injector<TextMapInject> injector = null;
    if (Kind.CLIENT.equals(kind)) {
      injector = formatToClientInjector.get(format);
    } else if (Kind.PRODUCER.equals(kind)) {
      injector = formatToProducerInjector.get(format);
    } else if (Kind.CONSUMER.equals(kind)) {
      injector = formatToConsumerInjector.get(format);
    }
    if (injector == null) injector = formatToInjector.get(format);
    if (injector == null) {
      throw new UnsupportedOperationException(format + " not in " + formatToInjector.keySet());
    }
    injector.inject(braveContext.unwrap(), (TextMapInject) carrier);
  }

  /**
   * Extracts the underlying context using B3 encoding by default. Null is returned when there is no
   * encoded context in the carrier, or upon error extracting it.
   */
  @Override public <C> BraveSpanContext extract(Format<C> format, C carrier) {
    if (carrier instanceof BinaryExtract) {
      return BraveSpanContext.create(BinaryCodec.INSTANCE.extract((BinaryExtract) carrier));
    }
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
