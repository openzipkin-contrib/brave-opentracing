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
import brave.propagation.StrictCurrentTraceContext;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Endpoint;
import zipkin2.Span.Kind;

import static io.opentracing.tag.Tags.SAMPLING_PRIORITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@RunWith(DataProviderRunner.class)
public class BraveSpanTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  BraveTracer tracer = BraveTracer.create(
      Tracing.newBuilder()
          .localServiceName("tracer")
          .currentTraceContext(new StrictCurrentTraceContext())
          .spanReporter(spans::add).build()
  );

  /** OpenTracing span implements auto-closeable, and implies reporting on close */
  @Test public void autoCloseOnTryFinally() {
    try (Scope scope = tracer.buildSpan("foo").startActive()) {
    }

    assertThat(spans)
        .hasSize(1);
  }

  @Test public void autoCloseOnTryFinally_doesntReportTwice() {
    try (Scope scope = tracer.buildSpan("foo").startActive()) {
      scope.span().finish(); // user closes and also auto-close closes
    }

    assertThat(spans)
        .hasSize(1);
  }

  @DataProvider
  public static Object[][] dataProviderKind() {
    return new Object[][] {
        {Tags.SPAN_KIND_CLIENT, Kind.CLIENT},
        {Tags.SPAN_KIND_SERVER, Kind.SERVER},
        {Tags.SPAN_KIND_PRODUCER, Kind.PRODUCER},
        {Tags.SPAN_KIND_CONSUMER, Kind.CONSUMER}
    };
  }

  @Test @UseDataProvider("dataProviderKind")
  public void spanKind_beforeStart(String tagValue, Kind kind) {
    tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), tagValue)
        .startManual().finish();

    zipkin2.Span span = spans.get(0);
    assertThat(span.kind())
        .isEqualTo(kind);

    assertThat(span.tags())
        .isEmpty();
  }

  @Test public void spanKind_beforeStart_mismatch() {
    tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), "antelope")
        .startManual().finish();

    zipkin2.Span span = spans.get(0);
    assertThat(span.kind())
        .isNull();

    assertThat(span.tags())
        .containsEntry("span.kind", "antelope");
  }

  @Test @UseDataProvider("dataProviderKind")
  public void spanKind_afterStart(String tagValue, Kind kind) {
    tracer.buildSpan("foo")
        .startManual()
        .setTag(Tags.SPAN_KIND.getKey(), tagValue)
        .finish();

    zipkin2.Span span = spans.get(0);
    assertThat(span.kind())
        .isEqualTo(kind);

    assertThat(span.tags())
        .isEmpty();
  }

  @Test public void spanKind_afterStart_mismatch() {
    tracer.buildSpan("foo")
        .startManual()
        .setTag(Tags.SPAN_KIND.getKey(), "antelope")
        .finish();

    zipkin2.Span span = spans.get(0);
    assertThat(span.kind())
        .isNull();

    assertThat(span.tags())
        .containsEntry("span.kind", "antelope");
  }

  /** Tags end up as string binary annotations */
  @Test public void startedSpan_setTag() {
    Span span = tracer.buildSpan("foo").startManual();
    span.setTag("hello", "monster");
    span.finish();

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsExactly(entry("hello", "monster"));
  }

  @Test public void childSpanWhenParentIsExtracted() throws IOException {
    Span spanClient = tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .startManual();

    Map<String, String> carrier = new LinkedHashMap<>();
    tracer.inject(spanClient.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(carrier));

    BraveTracer tracer2 = BraveTracer.create(
        Tracing.newBuilder()
            .localServiceName("tracer2")
            .spanReporter(spans::add).build()
    );

    SpanContext extractedContext =
        tracer2.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(carrier));

    Span spanServer = tracer2.buildSpan("foo")
        .asChildOf(extractedContext)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .startManual();

    tracer2.buildSpan("bar")
        .asChildOf(spanServer)
        .startManual()
        .finish();

    spanServer.finish();
    spanClient.finish();

    assertThat(spans).hasSize(3);
    assertThat(spans.get(0).traceId()).isEqualTo(spans.get(1).traceId())
        .isEqualTo(spans.get(2).traceId());
    assertThat(spans.get(1).id()).isNotEqualTo(spans.get(2).id());
    assertThat(spans.get(0).id()).isNotEqualTo(spans.get(1).id());

    // child first
    assertThat(spans.get(0).localServiceName()).isEqualTo("tracer2");
    assertThat(spans.get(1).localServiceName()).isEqualTo("tracer2");
    assertThat(spans.get(2).localServiceName()).isEqualTo("tracer");
  }

  @Test public void samplingPriority_unsampledWhenAtStart() {
    BraveSpan span = tracer.buildSpan("foo")
        .withTag(SAMPLING_PRIORITY.getKey(), 0)
        .startManual();

    assertThat(span.context().unwrap().sampled())
        .isFalse();

    span.finish();
    assertThat(spans).isEmpty();
  }

  @Test public void samplingPriority_abandonsAndUnsampledAfterStart() {
    BraveSpan span = tracer.buildSpan("foo")
        .startManual();

    assertThat(span.context().unwrap().sampled())
        .isTrue();

    // this is a known race-condition as sampling decision could have been propagated downstream!
    SAMPLING_PRIORITY.set(span, 0);
    assertThat(span.context().unwrap().sampled())
        .isFalse();

    assertThat(span.delegate)
        .isInstanceOf(AbandonedSpan.class);

    span.finish();
    assertThat(spans).isEmpty();
  }

  @Test public void setPeerTags_beforeStart() {
    tracer.buildSpan("encode")
        .withTag(Tags.PEER_SERVICE.getKey(), "jupiter")
        .withTag(Tags.PEER_HOST_IPV4.getKey(), "1.2.3.4")
        .withTag(Tags.PEER_HOST_IPV6.getKey(), "2001:db8::c001")
        .withTag(Tags.PEER_PORT.getKey(), 8080)
        .startManual().finish();

    assertThat(spans.get(0).remoteEndpoint())
        .isEqualTo(Endpoint.newBuilder()
            .serviceName("jupiter")
            .ip("1.2.3.4")
            .ip("2001:db8::c001")
            .port(8080).build());
  }

  @Test public void setPeerTags_afterStart() {
    tracer.buildSpan("encode")
        .startManual()
        .setTag(Tags.PEER_SERVICE.getKey(), "jupiter")
        .setTag(Tags.PEER_HOST_IPV4.getKey(), "1.2.3.4")
        .setTag(Tags.PEER_HOST_IPV6.getKey(), "2001:db8::c001")
        .setTag(Tags.PEER_PORT.getKey(), 8080)
        .finish();

    assertThat(spans.get(0).remoteEndpoint())
        .isEqualTo(Endpoint.newBuilder()
            .serviceName("jupiter")
            .ip("1.2.3.4")
            .ip("2001:db8::c001")
            .port(8080).build());
  }

  @After public void clear() {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }
}
