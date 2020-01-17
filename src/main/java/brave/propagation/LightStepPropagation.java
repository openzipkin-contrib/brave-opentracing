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

import brave.internal.HexCodec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Implements LightStep propagation, which is a vendor-specific propagation. In case of HTTP, this uses the
 * following headers: ot-tracer-traceid, ot-tracer-spanid, ot-tracer-sampled.
 */
public class LightStepPropagation<K> implements Propagation<K> {
  public static final Factory FACTORY = new Factory() {
    @Override public <L> Propagation<L> create(final KeyFactory<L> keyFactory) {
      return new LightStepPropagation<>(keyFactory);
    }

    @Override public String toString() {
      return "LightStepPropagationFactory";
    }
  };

  private static final String PREFIX_TRACER_STATE = "ot-tracer-";
  private static final String FIELD_NAME_TRACE_ID = PREFIX_TRACER_STATE + "traceid";
  private static final String FIELD_NAME_SPAN_ID = PREFIX_TRACER_STATE + "spanid";
  private static final String FIELD_NAME_SAMPLED = PREFIX_TRACER_STATE + "sampled";

  private final K traceIdKey;
  private final K spanIdKey;
  private final K sampledKey;
  private final List<K> fields;

  public LightStepPropagation(final KeyFactory<K> keyFactory) {
    traceIdKey = keyFactory.create(FIELD_NAME_TRACE_ID);
    spanIdKey = keyFactory.create(FIELD_NAME_SPAN_ID);
    sampledKey = keyFactory.create(FIELD_NAME_SAMPLED);
    fields = Collections.unmodifiableList(Arrays.asList(traceIdKey, spanIdKey, sampledKey));
  }

  @Override public List<K> keys() {
    return fields;
  }

  @Override public <C> TraceContext.Injector<C> injector(final Setter<C, K> setter) {
    return new TraceContext.Injector<C>() {
      @Override
      public void inject(final TraceContext traceContext, final C carrier) {
        setter.put(carrier, traceIdKey, HexCodec.toLowerHex(traceContext.traceId()));
        setter.put(carrier, spanIdKey, traceContext.spanIdString());
        final Boolean sampled = traceContext.sampled();
        if (sampled != null) {
          setter.put(carrier, sampledKey, sampled.toString());
        }
      }
    };
  }

  @Override public <C> TraceContext.Extractor<C> extractor(final Getter<C, K> getter) {
    return new TraceContext.Extractor<C>() {
      @Override
      public TraceContextOrSamplingFlags extract(final C carrier) {
        final String sampledStr = getter.get(carrier, sampledKey);
        final Boolean sampled = sampledStr == null ? null : "true".equalsIgnoreCase(sampledStr) || "1".equals(sampledStr);

        final String traceIdString = getter.get(carrier, traceIdKey);
        // It is ok to go without a trace ID, if sampling or debug is set
        if (traceIdString == null) return TraceContextOrSamplingFlags.create(sampled, false);

        // Try to parse the trace IDs into the context
        final TraceContext.Builder result = TraceContext.newBuilder().sampled(sampled);
        if (result.parseTraceId(traceIdString, traceIdKey) && result.parseSpanId(getter, carrier, spanIdKey)) {
          return TraceContextOrSamplingFlags.create(result.build());
        }
        return TraceContextOrSamplingFlags.EMPTY; // trace context is malformed so return empty
      }
    };
  }
}
