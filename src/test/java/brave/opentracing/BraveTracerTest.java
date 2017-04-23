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

import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import zipkin.Constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.data.MapEntry.entry;
import static zipkin.internal.Util.UTF_8;

/**
 * This shows how one might make an OpenTracing adapter for Brave, and how to navigate in and out
 * of the core concepts.
 */
public class BraveTracerTest {

  List<zipkin.Span> spans = new ArrayList<>();
  Tracing brave = Tracing.newBuilder().reporter(spans::add).build();
  BraveTracer opentracing = BraveTracer.create(brave);

  @Test public void startWithOpenTracingAndFinishWithBrave() {
    io.opentracing.Span openTracingSpan = opentracing.buildSpan("encode")
        .withTag(Constants.LOCAL_COMPONENT, "codec")
        .withStartTimestamp(1L).start();

    brave.Span braveSpan = ((BraveSpan) openTracingSpan).unwrap();

    braveSpan.annotate(2L, "pump fake");
    braveSpan.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test public void startWithBraveAndFinishWithOpenTracing() {
    brave.Span braveSpan = brave.tracer().newTrace().name("encode")
        .tag(Constants.LOCAL_COMPONENT, "codec")
        .start(1L);

    io.opentracing.Span openTracingSpan = BraveSpan.wrap(braveSpan);

    openTracingSpan.log(2L, "pump fake");
    openTracingSpan.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test public void extractTraceContext() throws Exception {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");

    BraveSpanContext openTracingContext =
        (BraveSpanContext) opentracing.extract(Format.Builtin.HTTP_HEADERS,
            new TextMapExtractAdapter(map));

    assertThat(openTracingContext.unwrap())
        .isEqualTo(TraceContext.newBuilder()
            .traceId(1L)
            .spanId(2L)
            .sampled(true).build());
  }

  @Test public void extractTraceContextTextMap() throws Exception {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");

    BraveSpanContext openTracingContext =
        (BraveSpanContext) opentracing.extract(Format.Builtin.TEXT_MAP,
            new TextMapExtractAdapter(map));

    assertThat(openTracingContext.unwrap())
        .isEqualTo(TraceContext.newBuilder()
            .traceId(1L)
            .spanId(2L)
            .sampled(true).build());
  }

  @Test public void extractTraceContextCaseInsensitive() throws Exception {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("x-b3-spanid", "0000000000000002");
    map.put("x-b3-SaMpLeD", "1");
    map.put("other", "1");

    BraveSpanContext openTracingContext =
        (BraveSpanContext) opentracing.extract(Format.Builtin.HTTP_HEADERS,
            new TextMapExtractAdapter(map));

    assertThat(openTracingContext.unwrap())
        .isEqualTo(TraceContext.newBuilder()
            .traceId(1L)
            .spanId(2L)
            .sampled(true).build());
  }

  @Test public void injectTraceContext() throws Exception {
    TraceContext context = TraceContext.newBuilder()
        .traceId(1L)
        .spanId(2L)
        .sampled(true).build();

    Map<String, String> map = new LinkedHashMap<>();
    TextMapInjectAdapter carrier = new TextMapInjectAdapter(map);
    opentracing.inject(BraveSpanContext.wrap(context), Format.Builtin.HTTP_HEADERS, carrier);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "0000000000000002"),
        entry("X-B3-Sampled", "1")
    );
  }

  @Test public void injectTraceContextTextMap() throws Exception {
    TraceContext context = TraceContext.newBuilder()
        .traceId(1L)
        .spanId(2L)
        .sampled(true).build();

    Map<String, String> map = new LinkedHashMap<>();
    TextMapInjectAdapter carrier = new TextMapInjectAdapter(map);
    opentracing.inject(BraveSpanContext.wrap(context), Format.Builtin.TEXT_MAP, carrier);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "0000000000000002"),
        entry("X-B3-Sampled", "1")
    );
  }

  @Test public void canUseCustomFormatKeys() throws Exception {
    Format<TextMap> B3 = new Format<TextMap>() {
    };
    opentracing = BraveTracer.newBuilder(brave)
        .textMapPropagation(B3, Propagation.B3_STRING).build();

    TraceContext context = TraceContext.newBuilder()
        .traceId(1L)
        .spanId(2L)
        .sampled(true).build();

    Map<String, String> map = new LinkedHashMap<>();
    TextMapInjectAdapter carrier = new TextMapInjectAdapter(map);
    opentracing.inject(BraveSpanContext.wrap(context), B3, carrier);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "0000000000000002"),
        entry("X-B3-Sampled", "1")
    );
  }

  void checkSpanReportedToZipkin() {
    assertThat(spans).first().satisfies(s -> {
          assertThat(s.name).isEqualTo("encode");
          assertThat(s.timestamp).isEqualTo(1L);
          assertThat(s.annotations).extracting(a -> a.timestamp, a -> a.value)
              .containsExactly(tuple(2L, "pump fake"));
          assertThat(s.binaryAnnotations).extracting(b -> b.key, b -> new String(b.value, UTF_8))
              .containsExactly(tuple(Constants.LOCAL_COMPONENT, "codec"));
          assertThat(s.duration).isEqualTo(2L);
        }
    );
  }
}
