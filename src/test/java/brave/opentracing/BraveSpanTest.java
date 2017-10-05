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

import static java.util.concurrent.TimeUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import brave.Tracing;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.storage.InMemoryStorage;

public class BraveSpanTest {
  InMemoryStorage zipkin = InMemoryStorage.newBuilder().build();
  BraveTracer tracer = BraveTracer.create(
      Tracing.newBuilder()
          .localServiceName("tracer")
          .spanReporter(s -> {
            try {
              zipkin.spanConsumer().accept(Collections.singletonList(s)).execute();
            } catch (IOException e) {
              throw new AssertionError(e);
            }
          }).build()
  );

  @Before public void clear() {
    zipkin.clear();
  }

  /** OpenTracing span implements auto-closeable, and implies reporting on close */
  @Test public void autoCloseOnTryFinally() {
    try (Scope scope = tracer.buildSpan("foo").startActive()) {
    }

    assertThat(zipkin.spanStore().getTraces())
        .hasSize(1);
  }

  @Test public void autoCloseOnTryFinally_doesntReportTwice() {
    try (Scope scope = tracer.buildSpan("foo").startActive()) {
      scope.close(); // user closes and also auto-close closes
    }

    assertThat(zipkin.spanStore().getTraces())
        .hasSize(1);
  }

  /** Span kind should be set at builder, not after start */
  @Test public void spanKind_client() {
    tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .startManual().finish();

    assertThat(zipkin.spanStore().getTraces())
        .flatExtracting(t -> t)
        .flatExtracting(zipkin2.Span::kind)
        .containsExactly(zipkin2.Span.Kind.CLIENT);
  }

  /** Span kind should be set at builder, not after start */
  @Test public void spanKind_server() {
    tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .startManual().finish();

    assertThat(zipkin.spanStore().getTraces())
        .flatExtracting(t -> t)
        .flatExtracting(zipkin2.Span::kind)
        .containsExactly(zipkin2.Span.Kind.SERVER);
  }

  /** Tags end up as string binary annotations */
  @Test public void startedSpan_setTag() {
    Span span = tracer.buildSpan("foo").startManual();
    span.setTag("hello", "monster");
    span.finish();

    assertThat(zipkin.spanStore().getTraces())
        .flatExtracting(t -> t)
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
            .spanReporter(s -> {
              try {
                zipkin.spanConsumer().accept(Collections.singletonList(s)).execute();
              } catch (IOException e) {
                throw new AssertionError(e);
              }
            }).build()
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

    List<zipkin2.Span> spans = zipkin.spanStore().getTraces().get(0);
    assertThat(spans).hasSize(3);
    assertThat(spans.get(0).traceId()).isEqualTo(spans.get(1).traceId())
        .isEqualTo(spans.get(2).traceId());
    assertThat(spans.get(1).id()).isNotEqualTo(spans.get(2).id());
    assertThat(spans.get(0).id()).isNotEqualTo(spans.get(1).id());

    Call<List<DependencyLink>> dependenciesCall =
        zipkin.spanStore().getDependencies(System.currentTimeMillis(), DAYS.toMillis(1));

    assertThat(dependenciesCall.execute()).containsExactly(
        DependencyLink.newBuilder().parent("tracer").child("tracer2").callCount(1L).build()
    );
  }

  @Test public void testNotSampled_spanBuilder_newTrace() {
    tracer.buildSpan("foo")
        .withTag(Tags.SAMPLING_PRIORITY.getKey(), 0)
        .startManual().finish();

    assertThat(zipkin.spanStore().getTraces()).isEmpty();
  }
}
