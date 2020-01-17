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
package brave.propagation;

import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class CombinedPropagationTests {
  private static final long TRACE_ID = 1234;
  private static final long TRACE_ID2 = 1235;
  private static final long SPAN_ID = 5678;
  private static final String ENCODED_TRACE_ID = "00000000000004d2";
  private static final String ENCODED_SPAN_ID = "000000000000162e";
  private static final Propagation.Setter<Map<String, String>, String> SETTER = Map::put;
  private static final Propagation.Getter<Map<String, String>, String> GETTER = Map::get;
  private static final Propagation<String> COMBINED_B3_LS_PROPAGATION = CombinedPropagation.newFactory(Arrays.asList(
          B3Propagation.FACTORY,
          LightStepPropagation.FACTORY
  )).create(Propagation.KeyFactory.STRING);
  private static final TraceContext NOT_SAMPLED_CONTEXT = TraceContext.newBuilder()
          .traceId(TRACE_ID)
          .spanId(SPAN_ID)
          .build();
  private static final TraceContext SAMPLED_CONTEXT = NOT_SAMPLED_CONTEXT.toBuilder()
          .sampled(true)
          .build();

  @Test public void testKeys() {
    final Map<String, String> fields1 = new HashMap<>();
    fields1.put("a", "1");
    fields1.put("b", "2");
    final Map<String, String> fields2 = new HashMap<>();
    fields1.put("a", "3");
    fields1.put("c", "4");
    final Propagation<String> propagation = new CombinedPropagation<>(Arrays.asList(
            new MapInjectingPropagation(fields1),
            new MapInjectingPropagation(fields2))
    );
    assertThat(propagation.keys()).containsExactly("a", "b", "c");
  }

  @Test public void testInject() {
    final Map<String, String> carrier = new HashMap<>();
    COMBINED_B3_LS_PROPAGATION.injector(SETTER).inject(SAMPLED_CONTEXT, carrier);
    assertThat(carrier)
            .contains(
                    entry("X-B3-TraceId", ENCODED_TRACE_ID),
                    entry("X-B3-SpanId", ENCODED_SPAN_ID),
                    entry("X-B3-Sampled", "1"),
                    entry("ot-tracer-traceid", ENCODED_TRACE_ID),
                    entry("ot-tracer-spanid", ENCODED_SPAN_ID),
                    entry("ot-tracer-sampled", "true")
            );
  }

  @Test public void testInjectOrder() {
    final Map<String, String> fields1 = new HashMap<>();
    fields1.put("a", "1");
    fields1.put("b", "2");
    final Map<String, String> fields2 = new HashMap<>();
    fields1.put("a", "3");
    fields1.put("c", "4");
    final Propagation<String> propagation = new CombinedPropagation<>(Arrays.asList(
            new MapInjectingPropagation(fields1),
            new MapInjectingPropagation(fields2))
    );
    final Map<String, String> carrier = new HashMap<>();
    propagation.injector(SETTER).inject(SAMPLED_CONTEXT, carrier);
    assertThat(carrier).contains(
            entry("a", "3"),
            entry("b", "2"),
            entry("c", "4")
    );
  }

  @Test public void testExtract() {
    final TraceContext.Extractor<Map<String, String>> extractor = COMBINED_B3_LS_PROPAGATION.extractor(GETTER);

    final Map<String, String> b3Carrier = new HashMap<>();
    b3Carrier.put("X-B3-TraceId", ENCODED_TRACE_ID);
    b3Carrier.put("X-B3-SpanId", ENCODED_SPAN_ID);
    b3Carrier.put("X-B3-Sampled", "1");
    assertThat(extractor.extract(b3Carrier)).isEqualTo(TraceContextOrSamplingFlags.create(SAMPLED_CONTEXT));

    final Map<String, String> lsCarrier = new HashMap<>();
    lsCarrier.put("ot-tracer-traceid", ENCODED_TRACE_ID);
    lsCarrier.put("ot-tracer-spanid", ENCODED_SPAN_ID);
    lsCarrier.put("ot-tracer-sampled", "1");
    assertThat(extractor.extract(lsCarrier)).isEqualTo(TraceContextOrSamplingFlags.create(SAMPLED_CONTEXT));
  }

  @Test public void testExtractOrder() {
    final TraceContext.Extractor<Map<String, String>> extractor1 = new CombinedPropagation<>(Arrays.asList(
            new FieldExtractingPropagation("a"),
            new FieldExtractingPropagation("b")
    )).extractor(GETTER);
    final TraceContext.Extractor<Map<String, String>> extractor2 = new CombinedPropagation<>(Arrays.asList(
            new FieldExtractingPropagation("b"),
            new FieldExtractingPropagation("a")
    )).extractor(GETTER);

    final Map<String, String> carrier = new HashMap<>();
    carrier.put("a", String.valueOf(TRACE_ID));
    carrier.put("b", String.valueOf(TRACE_ID2));

    assertThat(extractor1.extract(carrier))
            .isEqualTo(TraceContextOrSamplingFlags.create(TraceContext.newBuilder()
                    .traceId(TRACE_ID)
                    .spanId(TRACE_ID)
                    .build()));

    assertThat(extractor2.extract(carrier))
            .isEqualTo(TraceContextOrSamplingFlags.create(TraceContext.newBuilder()
                    .traceId(TRACE_ID2)
                    .spanId(TRACE_ID2)
                    .build()));
  }
}

class MapInjectingPropagation implements Propagation<String> {
  private final Map<String, String> fields;

  MapInjectingPropagation(final Map<String, String> fields) {
    this.fields = fields;
  }

  @Override public List<String> keys() {
    return fields.keySet().stream().collect(Collectors.toList());
  }

  @Override public <C> TraceContext.Injector<C> injector(final Setter<C, String> setter) {
    return (traceContext, carrier) -> fields.forEach((key, value) -> setter.put(carrier, key, value));
  }

  @Override public <C> TraceContext.Extractor<C> extractor(final Getter<C, String> getter) {
    throw new UnsupportedOperationException();
  }
}

class FieldExtractingPropagation implements Propagation<String> {
  private final String key;

  FieldExtractingPropagation(final String key) {
    this.key = key;
  }

  @Override public List<String> keys() {
    return Collections.singletonList(key);
  }

  @Override public <C> TraceContext.Injector<C> injector(final Setter<C, String> setter) {
    throw new UnsupportedOperationException();
  }

  @Override public <C> TraceContext.Extractor<C> extractor(final Getter<C, String> getter) {
    return carrier -> {
      final long value = Long.parseLong(getter.get(carrier, key));
      return TraceContextOrSamplingFlags.create(TraceContext.newBuilder()
              .traceId(value)
              .spanId(value)
              .build());
    };
  }
}
