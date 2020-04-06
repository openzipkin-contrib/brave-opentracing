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
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.propagation.B3Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tag;
import io.opentracing.tag.Tags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Endpoint;
import zipkin2.Span.Kind;

import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static io.opentracing.tag.Tags.SAMPLING_PRIORITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@RunWith(DataProviderRunner.class)
public class OpenTracing0_33_BraveSpanTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing brave;
  BraveTracer tracer;

  @Before public void init() {
    init(Tracing.newBuilder());
  }

  void init(Tracing.Builder tracingBuilder) {
    if (brave != null) brave.close();
    brave = tracingBuilder
        .localServiceName("tracer")
        .currentTraceContext(StrictCurrentTraceContext.create())
        .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
            .add(SingleBaggageField.remote(BaggageField.create("client-id")))
            .build())
        .spanReporter(spans::add).build();
    tracer = BraveTracer.create(brave);
  }

  @After public void clear() {
    brave.close();
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
        .start().finish();

    zipkin2.Span span = spans.get(0);
    assertThat(span.kind())
        .isEqualTo(kind);

    assertThat(span.tags())
        .isEmpty();
  }

  @Test public void spanKind_beforeStart_mismatch() {
    tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), "antelope")
        .start().finish();

    zipkin2.Span span = spans.get(0);
    assertThat(span.kind())
        .isNull();

    assertThat(span.tags())
        .containsEntry("span.kind", "antelope");
  }

  @Test @UseDataProvider("dataProviderKind")
  public void spanKind_afterStart(String tagValue, Kind kind) {
    tracer.buildSpan("foo")
        .start()
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
        .start()
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
    Span span = tracer.buildSpan("foo").start();
    span.setTag("hello", "monster");
    span.finish();

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(entry("hello", "monster"));
  }

  @Test public void afterFinish_dataIgnored() {
    Span span = tracer.buildSpan("foo").start();
    span.finish();
    spans.clear();

    span.setOperationName("bar");
    span.setTag("hello", "monster");
    span.log("alarming");
    span.finish();

    assertThat(spans)
        .isEmpty();
  }

  @Test public void childSpanWhenParentIsExtracted() {
    Span spanClient = tracer.buildSpan("foo")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .start();

    Map<String, String> carrier = new LinkedHashMap<>();
    tracer.inject(spanClient.context(), TEXT_MAP, new TextMapAdapter(carrier));

    BraveTracer tracer2 = BraveTracer.create(
        Tracing.newBuilder()
            .localServiceName("tracer2")
            .spanReporter(spans::add).build()
    );

    SpanContext extractedContext = tracer2.extract(TEXT_MAP, new TextMapAdapter(carrier));

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

    assertThat(spans).hasSize(3);
    assertThat(spans.get(0).traceId()).isEqualTo(spans.get(1).traceId())
        .isEqualTo(spans.get(2).traceId());
    assertThat(spans.get(1).id()).isEqualTo(spans.get(2).id()); // supportsJoin is default
    assertThat(spans.get(0).id()).isNotEqualTo(spans.get(1).id());

    // child first
    assertThat(spans.get(0).localServiceName()).isEqualTo("tracer2");
    assertThat(spans.get(1).localServiceName()).isEqualTo("tracer2");
    assertThat(spans.get(2).localServiceName()).isEqualTo("tracer");
  }

  @Test public void extractDoesntDropBaggage() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put("client-id", "aloha");

    SpanContext extractedContext =
        tracer.extract(TEXT_MAP, new TextMapAdapter(carrier));

    assertThat(extractedContext.baggageItems())
        .contains(entry("client-id", "aloha"));

    Span serverSpan = tracer.buildSpan("foo")
        .asChildOf(extractedContext)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .start();

    assertThat(serverSpan.getBaggageItem("client-id"))
        .isEqualTo("aloha");

    serverSpan.finish();
  }

  @Test public void samplingPriority_sampledWhenAtStart() {
    init(Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE));

    BraveSpan span = tracer.buildSpan("foo")
        .withTag(SAMPLING_PRIORITY.getKey(), 1)
        .start();

    assertThat(span.context().unwrap().sampled())
        .isTrue();

    span.finish();
    assertThat(spans).hasSize(1);
  }

  @Test public void samplingPriority_unsampledWhenAtStart() {
    BraveSpan span = tracer.buildSpan("foo")
        .withTag(SAMPLING_PRIORITY.getKey(), 0)
        .start();

    assertThat(span.context().unwrap().sampled())
        .isFalse();

    span.finish();
    assertThat(spans).isEmpty();
  }

  @Test public void samplingPriority_abandonsAndUnsampledAfterStart() {
    BraveSpan span = tracer.buildSpan("foo")
        .start();

    assertThat(span.context().unwrap().sampled())
        .isTrue();

    // this is a known race-condition as sampling decision could have been propagated downstream!
    SAMPLING_PRIORITY.set(span, 0);
    assertThat(span.context().unwrap().sampled())
        .isFalse();

    assertThat(span.delegate.isNoop())
        .isTrue();

    span.finish();
    assertThat(spans).isEmpty();
  }

  @Test public void setPeerTags_beforeStart() {
    tracer.buildSpan("encode")
        .withTag(Tags.PEER_SERVICE.getKey(), "jupiter")
        .withTag(Tags.PEER_HOST_IPV4.getKey(), "1.2.3.4")
        .withTag(Tags.PEER_HOST_IPV6.getKey(), "2001:db8::c001")
        .withTag(Tags.PEER_PORT.getKey(), 8080)
        .start().finish();

    assertThat(spans.get(0).remoteEndpoint())
        .isEqualTo(Endpoint.newBuilder()
            .serviceName("jupiter")
            .ip("2001:db8::c001")
            .port(8080).build());
  }

  @Test public void setPeerTags_afterStart() {
    tracer.buildSpan("encode")
        .start()
        .setTag(Tags.PEER_SERVICE.getKey(), "jupiter")
        .setTag(Tags.PEER_HOST_IPV4.getKey(), "1.2.3.4")
        .setTag(Tags.PEER_HOST_IPV6.getKey(), "2001:db8::c001")
        .setTag(Tags.PEER_PORT.getKey(), 8080)
        .finish();

    assertThat(spans.get(0).remoteEndpoint())
        .isEqualTo(Endpoint.newBuilder()
            .serviceName("jupiter")
            .ip("2001:db8::c001")
            .port(8080).build());
  }

  @Test public void withTag() {
    tracer.buildSpan("encode")
        .withTag(Tags.HTTP_METHOD.getKey(), "GET")
        .withTag(Tags.ERROR.getKey(), true)
        .withTag(Tags.HTTP_STATUS.getKey(), 404)
        .start().finish();

    assertContainsTags();
  }

  @Test public void withTag_object() {
    tracer.buildSpan("encode")
        .withTag(Tags.HTTP_METHOD, "GET")
        .withTag(Tags.ERROR, true)
        .withTag(Tags.HTTP_STATUS, 404)
        .start().finish();

    assertContainsTags();
  }

  @Test public void setTag() {
    tracer.buildSpan("encode").start()
        .setTag(Tags.HTTP_METHOD.getKey(), "GET")
        .setTag(Tags.ERROR.getKey(), true)
        .setTag(Tags.HTTP_STATUS.getKey(), 404)
        .finish();

    assertContainsTags();
  }

  @Test public void setTag_object() {
    tracer.buildSpan("encode").start()
        .setTag(Tags.HTTP_METHOD, "GET")
        .setTag(Tags.ERROR, true)
        .setTag(Tags.HTTP_STATUS, 404)
        .finish();

    assertContainsTags();
  }

  void assertContainsTags() {
    assertThat(spans.get(0).tags())
        .containsEntry("http.method", "GET")
        .containsEntry("error", "true")
        .containsEntry("http.status_code", "404");
  }

  Tag<Exception> exceptionTag = new Tag<Exception>() {
    @Override public String getKey() {
      return "exception";
    }

    @Override public void set(Span span, Exception value) {
      span.setTag(getKey(), value.getClass().getSimpleName());
    }
  };

  @Test public void setTag_custom() {
    tracer.buildSpan("encode").start()
        .setTag(exceptionTag, new RuntimeException("ice cream")).finish();

    assertThat(spans.get(0).tags())
        .containsEntry("exception", "RuntimeException");
  }

  /** There is no javadoc, but we were told only string, bool or number? */
  @Test(expected = IllegalArgumentException.class)
  public void withTag_custom_unsupported() {
    tracer.buildSpan("encode")
        .withTag(exceptionTag, new RuntimeException("ice cream"));
  }
}
