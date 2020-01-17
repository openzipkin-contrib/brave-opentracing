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

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@RunWith(DataProviderRunner.class)
public class LightStepPropagationTests {
  private static final long TRACE_ID = 1234;
  private static final long SPAN_ID = 5678;
  private static final String ENCODED_TRACE_ID = "00000000000004d2";
  private static final String ENCODED_SPAN_ID = "000000000000162e";
  private static final LightStepPropagation<String> LIGHT_STEP_PROPAGATION = new LightStepPropagation<>(Propagation.KeyFactory.STRING);
  private static final TraceContext.Injector<Map<String, String>> INJECTOR = LIGHT_STEP_PROPAGATION.injector(Map::put);
  private static final TraceContext.Extractor<Map<String, String>> EXTRACTOR = LIGHT_STEP_PROPAGATION.extractor(Map::get);
  private static final TraceContext NOT_SAMPLED_CONTEXT = TraceContext.newBuilder()
          .traceId(TRACE_ID)
          .spanId(SPAN_ID)
          .build();
  private static final TraceContext SAMPLED_CONTEXT = NOT_SAMPLED_CONTEXT.toBuilder()
          .sampled(true)
          .build();

  @Test public void testKeys() {
    assertThat(LIGHT_STEP_PROPAGATION.keys())
            .containsExactlyInAnyOrder("ot-tracer-traceid", "ot-tracer-spanid", "ot-tracer-sampled");
  }

  @Test public void testInjectorNotSampled() {
    final Map<String, String> carrier = new HashMap<>();
    INJECTOR.inject(NOT_SAMPLED_CONTEXT, carrier);
    assertThat(carrier)
            .doesNotContainKey("ot-tracer-sampled")
            .contains(
                    entry("ot-tracer-traceid", ENCODED_TRACE_ID),
                    entry("ot-tracer-spanid", ENCODED_SPAN_ID)
            );
  }

  @Test public void testInjectorSampled() {
    final Map<String, String> carrier = new HashMap<>();
    INJECTOR.inject(SAMPLED_CONTEXT, carrier);
    assertThat(carrier)
            .contains(
                    entry("ot-tracer-traceid", ENCODED_TRACE_ID),
                    entry("ot-tracer-spanid", ENCODED_SPAN_ID),
                    entry("ot-tracer-sampled", "true")
            );
  }

  @Test public void testExtractorEmpty() {
    assertThat(EXTRACTOR.extract(Collections.emptyMap())).isEqualTo(TraceContextOrSamplingFlags.EMPTY);
  }

  @DataProvider
  public static String[] dataProviderFalsy() {
    return new String[]{"", "false", "foobar"};
  }

  @Test @UseDataProvider("dataProviderFalsy")
  public void testExtractorNotSampled(final String value) {
    assertThat(EXTRACTOR.extract(Collections.singletonMap("ot-tracer-sampled", value)))
            .isEqualTo(TraceContextOrSamplingFlags.create(false, false));
  }

  @DataProvider
  public static String[] dataProviderTruthy() {
    return new String[]{"1", "true", "True"};
  }

  @Test @UseDataProvider("dataProviderTruthy")
  public void testExtractorSampled(final String value) {
    assertThat(EXTRACTOR.extract(Collections.singletonMap("ot-tracer-sampled", value)))
            .isEqualTo(TraceContextOrSamplingFlags.create(true, false));
  }

  @Test public void testExtractor() {
    final Map<String, String> carrier = new HashMap<>();
    carrier.put("ot-tracer-traceid", ENCODED_TRACE_ID);
    carrier.put("ot-tracer-spanid", ENCODED_SPAN_ID);
    carrier.put("ot-tracer-sampled", "1");
    assertThat(EXTRACTOR.extract(carrier)).isEqualTo(TraceContextOrSamplingFlags.create(SAMPLED_CONTEXT));
  }

  @Test public void testExtractorMalformed() {
    final Map<String, String> carrier = new HashMap<>();
    carrier.put("ot-tracer-traceid", "");
    carrier.put("ot-tracer-spanid", "");
    carrier.put("ot-tracer-sampled", "1");
    assertThat(EXTRACTOR.extract(carrier)).isEqualTo(TraceContextOrSamplingFlags.EMPTY);
  }
}
