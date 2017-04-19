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

import brave.Tracer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import zipkin.Constants;
import zipkin.DependencyLink;
import zipkin.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class BraveSpanTest {
  InMemoryStorage zipkin = new InMemoryStorage();
  BraveTracer tracer = BraveTracer.wrap(
      Tracer.newBuilder()
          .localServiceName("tracer")
          .reporter(s -> zipkin.spanConsumer().accept(Collections.singletonList(s))).build()
  );

  @Before public void clear() {
    zipkin.clear();
  }

  /** OpenTracing span implements auto-closeable, and implies reporting on close */
  @Test public void autoCloseOnTryFinally() {
    try (Span span = tracer.buildSpan("foo").start()) {
    }

    assertThat(zipkin.spanStore().getRawTraces())
        .hasSize(1);
  }

  @Test public void autoCloseOnTryFinally_doesntReportTwice() {
    try (Span span = tracer.buildSpan("foo").start()) {
      span.finish(); // user closes and also auto-close closes
    }

    assertThat(zipkin.spanStore().getRawTraces())
        .hasSize(1);
  }

  /** Span kind should be set at builder, not after start */
  @Test public void spanKind_client() {
    tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .start().finish();

    assertThat(zipkin.spanStore().getRawTraces())
        .flatExtracting(t -> t)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly(Constants.CLIENT_SEND, Constants.CLIENT_RECV);
  }

  /** Span kind should be set at builder, not after start */
  @Test public void spanKind_server() {
    tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .start().finish();

    assertThat(zipkin.spanStore().getRawTraces())
        .flatExtracting(t -> t)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly(Constants.SERVER_RECV, Constants.SERVER_SEND);
  }

  /** Tags end up as string binary annotations */
  @Test public void startedSpan_setTag() {
    Span span = tracer.buildSpan("foo").start();
    span.setTag("hello", "monster");
    span.finish();

    assertThat(zipkin.spanStore().getRawTraces())
        .flatExtracting(t -> t)
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key, b -> new String(b.value))
        .containsExactly(tuple("hello", "monster"));
  }

  @Test public void shareSpanWhenParentIsExtracted() {
    Span spanClient = tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .start();

    Map<String, String> carrier = new LinkedHashMap<>();
    tracer.inject(spanClient.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(carrier));

    BraveTracer tracer2 = BraveTracer.wrap(
        Tracer.newBuilder()
            .localServiceName("tracer2")
            .reporter(s -> zipkin.spanConsumer().accept(Collections.singletonList(s))).build()
    );

    SpanContext extractedContext =
        tracer2.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(carrier));

    Span spanServer = tracer2.buildSpan("foo")
        .asChildOf(extractedContext)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .start();

    tracer2.buildSpan("bar")
        .asChildOf(spanServer)
        .start()
        .finish();

    spanServer.finish();
    spanClient.finish();

    List<zipkin.Span> spans = zipkin.spanStore().getRawTraces().get(0);
    assertThat(spans).hasSize(3);
    assertThat(spans.get(0).traceId).isEqualTo(spans.get(1).traceId)
        .isEqualTo(spans.get(2).traceId);
    assertThat(spans.get(1).id).isEqualTo(spans.get(2).id);
    assertThat(spans.get(0).id).isNotEqualTo(spans.get(1).id);

    assertThat(zipkin.spanStore().getDependencies(System.currentTimeMillis(), null))
        .containsExactly(DependencyLink.create("tracer", "tracer2", 1L));
  }

  @Test public void testNotSampled_spanBuilder_newTrace() {
    tracer.buildSpan("foo")
        .withTag(Tags.SAMPLING_PRIORITY.getKey(), 0)
        .start().finish();

    assertThat(zipkin.spanStore().getRawTraces()).isEmpty();
  }
}
