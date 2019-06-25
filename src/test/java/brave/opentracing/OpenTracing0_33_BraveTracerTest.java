/*
 * Copyright 2016-2019 The OpenZipkin Authors
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

import brave.Span;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import io.opentracing.Scope;
import io.opentracing.propagation.BinaryAdapters;
import io.opentracing.propagation.BinaryInject;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapAdapter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Annotation;

import static io.opentracing.propagation.BinaryAdapters.extractionCarrier;
import static io.opentracing.propagation.BinaryAdapters.injectionCarrier;
import static io.opentracing.propagation.Format.Builtin.BINARY;
import static io.opentracing.propagation.Format.Builtin.BINARY_EXTRACT;
import static io.opentracing.propagation.Format.Builtin.BINARY_INJECT;
import static io.opentracing.propagation.Format.Builtin.HTTP_HEADERS;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP_EXTRACT;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP_INJECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assert.assertEquals;

/**
 * This shows how one might make an OpenTracing adapter for Brave, and how to navigate in and out of
 * the core concepts.
 */
@RunWith(DataProviderRunner.class)
public class OpenTracing0_33_BraveTracerTest {
  TraceContext context = TraceContext.newBuilder()
      .traceId(1L)
      .spanId(2L)
      .sampled(true).build();

  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing brave = Tracing.newBuilder()
      .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(StrictScopeDecorator.create())
          .build())
      .propagationFactory(ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
          .addPrefixedFields("baggage-", Arrays.asList("country-code", "user-id"))
          .build())
      .spanReporter(spans::add)
      .build();
  BraveTracer opentracing = BraveTracer.create(brave);

  @After public void clear() {
    brave.close();
  }

  @Test public void versionIsCorrect() {
    assertThat(OpenTracingVersion.get())
        .isInstanceOf(OpenTracingVersion.v0_32.class);
  }

  @Test public void startWithOpenTracingAndFinishWithBrave() {
    io.opentracing.Span openTracingSpan = opentracing.buildSpan("encode")
        .withTag("lc", "codec")
        .withStartTimestamp(1L)
        .start();

    Span braveSpan = ((BraveSpan) openTracingSpan).unwrap();

    braveSpan.annotate(2L, "pump fake");
    braveSpan.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test public void startWithBraveAndFinishWithOpenTracing() {
    Span braveSpan = brave.tracer().newTrace().name("encode")
        .tag("lc", "codec")
        .start(1L);

    io.opentracing.Span openTracingSpan = new BraveSpan(brave.tracer(), braveSpan);

    openTracingSpan.log(2L, "pump fake");
    openTracingSpan.finish(3L);

    checkSpanReportedToZipkin();
  }

  @DataProvider public static Object[] dataProviderExtractTextFormats() {
    return new Object[] {HTTP_HEADERS, TEXT_MAP, TEXT_MAP_EXTRACT};
  }

  @Test @UseDataProvider("dataProviderExtractTextFormats")
  public void extractTraceContext(Format format) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");

    assertExtractedContext(format, new TextMapAdapter(map));
  }

  @Test @UseDataProvider("dataProviderExtractTextFormats")
  public void extractBaggage(Format format) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");
    map.put("baggage-country-code", "FO");

    BraveSpanContext otContext = opentracing.extract(format, new TextMapAdapter(map));

    assertThat(otContext.baggageItems())
        .containsExactly(entry("country-code", "FO"));
  }

  @Test @UseDataProvider("dataProviderExtractTextFormats")
  public void extractOnlyBaggage(Format format) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("baggage-country-code", "FO");

    BraveSpanContext otContext = opentracing.extract(format, new TextMapAdapter(map));

    assertThat(otContext.toTraceId()).isNull();
    assertThat(otContext.toSpanId()).isNull();
    assertThat(otContext.unwrap()).isNull();
    assertThat(otContext.baggageItems())
        .containsExactly(entry("country-code", "FO"));
  }

  @Test @UseDataProvider("dataProviderExtractTextFormats")
  public void extractOnlySampled(Format format) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-Sampled", "1");

    BraveSpanContext otContext = opentracing.extract(format, new TextMapAdapter(map));

    assertThat(otContext.toTraceId()).isNull();
    assertThat(otContext.toSpanId()).isNull();
    assertThat(otContext.unwrap()).isNull();
  }

  @Test @UseDataProvider("dataProviderExtractTextFormats")
  public void extractTraceContextCaseInsensitive(Format format) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("x-b3-spanid", "0000000000000002");
    map.put("x-b3-SaMpLeD", "1");
    map.put("other", "1");

    assertExtractedContext(format, new TextMapAdapter(map));
  }

  <C> void assertExtractedContext(Format<C> format, C carrier) {
    BraveSpanContext otContext = opentracing.extract(format, carrier);

    assertThat(otContext.toTraceId())
        .isEqualTo(otContext.unwrap().traceIdString());
    assertThat(otContext.toSpanId())
        .isEqualTo(otContext.unwrap().spanIdString());
    assertThat(otContext.unwrap())
        .isEqualTo(TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build());
  }

  @DataProvider public static Object[] dataProviderInjectTextFormats() {
    return new Object[] {HTTP_HEADERS, TEXT_MAP, TEXT_MAP_INJECT};
  }

  @Test @UseDataProvider("dataProviderInjectTextFormats")
  public void injectTraceContext(Format format) {
    Map<String, String> map = new LinkedHashMap<>();
    TextMapAdapter carrier = new TextMapAdapter(map);
    opentracing.inject(BraveSpanContext.create(context), format, carrier);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "0000000000000002"),
        entry("X-B3-Sampled", "1")
    );
  }

  @Test @UseDataProvider("dataProviderInjectTextFormats")
  public void injectTraceContext_baggage(Format format) {
    BraveSpan span = opentracing.buildSpan("foo").start();
    span.setBaggageItem("country-code", "FO");

    Map<String, String> map = new LinkedHashMap<>();
    TextMapAdapter carrier = new TextMapAdapter(map);
    opentracing.inject(span.context(), format, carrier);

    assertThat(map).containsEntry("baggage-country-code", "FO");
  }

  @Test public void unsupportedFormat() {
    Map<String, String> map = new LinkedHashMap<>();
    TextMapAdapter carrier = new TextMapAdapter(map);
    Format<TextMap> B3 = new Format<TextMap>() {
    };

    try {
      opentracing.inject(BraveSpanContext.create(context), B3, carrier);
      failBecauseExceptionWasNotThrown(UnsupportedOperationException.class);
    } catch (UnsupportedOperationException e) {
    }

    try {
      opentracing.extract(B3, carrier);
      failBecauseExceptionWasNotThrown(UnsupportedOperationException.class);
    } catch (UnsupportedOperationException e) {
    }
  }

  @Test public void canUseCustomFormatKeys() {
    Map<String, String> map = new LinkedHashMap<>();
    TextMapAdapter carrier = new TextMapAdapter(map);
    Format<TextMap> B3 = new Format<TextMap>() {
    };

    opentracing = BraveTracer.newBuilder(brave)
        .textMapPropagation(B3, Propagation.B3_SINGLE_STRING).build();

    opentracing.inject(BraveSpanContext.create(context), B3, carrier);

    assertThat(map).containsEntry("b3", "0000000000000001-0000000000000002-1");

    assertExtractedContext(B3, new TextMapAdapter(map));
  }

  @Test public void binaryFormat() {
    ByteBuffer buffer = ByteBuffer.allocate(128);

    opentracing.inject(BraveSpanContext.create(context), BINARY_INJECT, injectionCarrier(buffer));
    buffer.rewind();

    assertThat(opentracing.extract(BINARY_EXTRACT, extractionCarrier(buffer)).unwrap())
        .isEqualTo(context);
  }

  void checkSpanReportedToZipkin() {
    assertThat(spans).first().satisfies(s -> {
          assertThat(s.name()).isEqualTo("encode");
          assertThat(s.timestamp()).isEqualTo(1L);
          assertThat(s.annotations())
              .containsExactly(Annotation.create(2L, "pump fake"));
          assertThat(s.tags())
              .containsExactly(entry("lc", "codec"));
          assertThat(s.duration()).isEqualTo(2L);
        }
    );
  }

  @Test public void subsequentChildrenNestProperly_OTStyle() {
    // this test is semantically identical to subsequentChildrenNestProperly_BraveStyle, but uses
    // the OpenTracingAPI instead of the Brave API.

    Long idOfSpanA;
    Long shouldBeIdOfSpanA;
    Long idOfSpanB;
    Long shouldBeIdOfSpanB;
    Long parentIdOfSpanB;
    Long parentIdOfSpanC;

    BraveSpan spanA = opentracing.buildSpan("spanA").start();
    try (Scope scopeA = opentracing.activateSpan(spanA)) {
      idOfSpanA = brave.currentTraceContext().get().spanId();

      BraveSpan spanB = opentracing.buildSpan("spanB").start();
      try (Scope scopeB = opentracing.activateSpan(spanB)) {
        idOfSpanB = brave.currentTraceContext().get().spanId();
        parentIdOfSpanB = brave.currentTraceContext().get().parentId();
        shouldBeIdOfSpanB = brave.currentTraceContext().get().spanId();
      }
      shouldBeIdOfSpanA = brave.currentTraceContext().get().spanId();

      BraveSpan spanC = opentracing.buildSpan("spanC").start();
      try (Scope scopeC = opentracing.activateSpan(spanC)) {
        parentIdOfSpanC = brave.currentTraceContext().get().parentId();
      }
    }

    assertEquals("SpanA should have been active again after closing B", idOfSpanA,
        shouldBeIdOfSpanA);
    assertEquals("SpanB should have been active prior to its closure", idOfSpanB,
        shouldBeIdOfSpanB);
    assertEquals("SpanB's parent should be SpanA", idOfSpanA, parentIdOfSpanB);
    assertEquals("SpanC's parent should be SpanA", idOfSpanA, parentIdOfSpanC);
  }

  @Test public void subsequentChildrenNestProperly_BraveStyle() {
    // this test is semantically identical to subsequentChildrenNestProperly_OTStyle, but uses
    // the Brave API instead of the OpenTracing API.

    Long shouldBeIdOfSpanA;
    Long idOfSpanB;
    Long shouldBeIdOfSpanB;
    Long parentIdOfSpanB;
    Long parentIdOfSpanC;

    Span spanA = brave.tracer().newTrace().name("spanA").start();
    Long idOfSpanA = spanA.context().spanId();
    try (SpanInScope scopeA = brave.tracer().withSpanInScope(spanA)) {

      Span spanB = brave.tracer().newChild(spanA.context()).name("spanB").start();
      idOfSpanB = spanB.context().spanId();
      parentIdOfSpanB = spanB.context().parentId();
      try (SpanInScope scopeB = brave.tracer().withSpanInScope(spanB)) {
        shouldBeIdOfSpanB = brave.currentTraceContext().get().spanId();
      } finally {
        spanB.finish();
      }

      shouldBeIdOfSpanA = brave.currentTraceContext().get().spanId();

      Span spanC = brave.tracer().newChild(spanA.context()).name("spanC").start();
      parentIdOfSpanC = spanC.context().parentId();
      try (SpanInScope scopeC = brave.tracer().withSpanInScope(spanC)) {
        // nothing to do here
      } finally {
        spanC.finish();
      }
    } finally {
      spanA.finish();
    }

    assertEquals("SpanA should have been active again after closing B", idOfSpanA,
        shouldBeIdOfSpanA);
    assertEquals("SpanB should have been active prior to its closure", idOfSpanB,
        shouldBeIdOfSpanB);
    assertEquals("SpanB's parent should be SpanA", idOfSpanA, parentIdOfSpanB);
    assertEquals("SpanC's parent should be SpanA", idOfSpanA, parentIdOfSpanC);
  }

  @Test public void activeSpan() {
    BraveSpan spanA = opentracing.buildSpan("spanA").start();
    try (Scope scopeA = opentracing.activateSpan(spanA)) {
      assertThat(opentracing.activeSpan())
          .isEqualToComparingFieldByField(opentracing.scopeManager().activeSpan());
    }
  }

  @Test public void implicitParentFromSpanManager_start() {
    BraveSpan spanA = opentracing.buildSpan("spanA").start();
    try (Scope scopeA = opentracing.activateSpan(spanA)) {
      BraveSpan span = opentracing.buildSpan("spanB").start();
      assertThat(span.unwrap().context().parentId())
          .isEqualTo(brave.currentTraceContext().get().spanId());
    }
  }

  @Test public void implicitParentFromSpanManager_start_ignoreActiveSpan() {
    BraveSpan spanA = opentracing.buildSpan("spanA").start();
    try (Scope scopeA = opentracing.activateSpan(spanA)) {
      BraveSpan span = opentracing.buildSpan("spanB")
          .ignoreActiveSpan().start();
      assertThat(span.unwrap().context().parentId())
          .isNull(); // new trace
    }
  }

  @Test public void ignoresErrorFalseTag_beforeStart() {
    opentracing.buildSpan("encode")
        .withTag("error", false)
        .start().finish();

    assertThat(spans.get(0).tags())
        .isEmpty();
  }

  @Test public void ignoresErrorFalseTag_afterStart() {
    opentracing.buildSpan("encode")
        .start()
        .setTag("error", false)
        .finish();

    assertThat(spans.get(0).tags())
        .isEmpty();
  }
}
