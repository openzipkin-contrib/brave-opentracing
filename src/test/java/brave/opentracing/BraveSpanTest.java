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
import brave.propagation.StrictCurrentTraceContext;
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
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

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

  /** Span kind should be set at builder, not after start */
  @Test public void spanKind_client() {
    tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .startManual().finish();

    assertThat(spans)
        .flatExtracting(zipkin2.Span::kind)
        .containsExactly(zipkin2.Span.Kind.CLIENT);
  }

  /** Span kind should be set at builder, not after start */
  @Test public void spanKind_server() {
    tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .startManual().finish();

    assertThat(spans)
        .flatExtracting(zipkin2.Span::kind)
        .containsExactly(zipkin2.Span.Kind.SERVER);
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

  @Test public void testNotSampled_spanBuilder_newTrace() {
    tracer.buildSpan("foo")
        .withTag(Tags.SAMPLING_PRIORITY.getKey(), 0)
        .startManual().finish();

    assertThat(spans).isEmpty();
  }
}
