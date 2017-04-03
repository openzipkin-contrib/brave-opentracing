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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import brave.Tracer;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import zipkin.Constants;

public class BraveSpanTest {
  List<zipkin.Span> spans = new ArrayList();
  BraveTracer tracer = BraveTracer.wrap(Tracer.newBuilder().reporter(spans::add).build());

  /** OpenTracing span implements auto-closeable, and implies reporting on close */
  @Test public void autoCloseOnTryFinally() {
    try (Span span = tracer.buildSpan("foo").start()) {
    }

    assertThat(spans).hasSize(1);
  }

  @Test public void autoCloseOnTryFinally_doesntReportTwice() {
    try (Span span = tracer.buildSpan("foo").start()) {
      span.finish(); // user closes and also auto-close closes
    }

    assertThat(spans).hasSize(1);
  }

  @Test public void clientSendRecvFromOTClientTagAtBuilder() {
    tracer.buildSpan("foo")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .start().finish();

    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).annotations.get(0).value).isEqualTo(Constants.CLIENT_SEND);
    assertThat(spans.get(0).annotations.get(1).value).isEqualTo(Constants.CLIENT_RECV);
  }

  @Test public void clientSendRecvFromOTClientTagAtSpan() {
    Span span = tracer.buildSpan("foo")
            .start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
    span.finish();

    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).annotations.get(0).value).isEqualTo(Constants.CLIENT_SEND);
    assertThat(spans.get(0).annotations.get(1).value).isEqualTo(Constants.CLIENT_RECV);
  }

  @Test public void serverRecvSendFromOTClientTagAtBuilder() {
    tracer.buildSpan("foo")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .start().finish();

    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).annotations.get(0).value).isEqualTo(Constants.SERVER_RECV);
    assertThat(spans.get(0).annotations.get(1).value).isEqualTo(Constants.SERVER_SEND);
  }

  @Test public void serverRecvSendFromOTClientTagAtSpan() {
    Span span = tracer.buildSpan("foo")
            .start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
    span.finish();

    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).annotations.get(0).value).isEqualTo(Constants.SERVER_RECV);
    assertThat(spans.get(0).annotations.get(1).value).isEqualTo(Constants.SERVER_SEND);
  }
}
