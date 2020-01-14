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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Allows multiple {@link Propagation}s to be used. Upon injection, all {@link Propagation}s are used, while at
 * extraction time {@link Propagation}s will be tried one by one in the specified order until one is successful,
 * at which point the remaining {@link Propagation}s will not be called. If all {@link Propagation}s fail to extract,
 * {@link TraceContextOrSamplingFlags#EMPTY} is returned.
 */
public class CombinedPropagation<K> implements Propagation<K> {
  public static Factory newFactory(final List<Factory> propagationFactories) {
    return new Factory() {
      @Override public <K> Propagation<K> create(final KeyFactory<K> keyFactory) {
        final List<Propagation<K>> propagations = new ArrayList<>();
        for (final Factory factory : propagationFactories) {
          propagations.add(factory.create(keyFactory));
        }
        return new CombinedPropagation<>(propagations);
      }

      @Override public String toString() {
        final StringBuilder stringBuilder = new StringBuilder("CombinedPropagationFactory{");
        for (int i = 0; i < propagationFactories.size(); ++i) {
          stringBuilder.append(propagationFactories.get(i));
          if (i < propagationFactories.size() - 1) stringBuilder.append(',');
        }
        return stringBuilder.append('}').toString();
      }
    };
  }

  private final List<Propagation<K>> propagations;
  private final List<K> keys;

  public CombinedPropagation(final List<Propagation<K>> propagations) {
    this.propagations = propagations;
    final Set<K> keySet = new LinkedHashSet<>();
    for (final Propagation<K> propagation : propagations) {
      keySet.addAll(propagation.keys());
    }
    keys = new ArrayList<>(keySet);
  }

  @Override public List<K> keys() {
    return keys;
  }

  @Override public <C> TraceContext.Injector<C> injector(final Setter<C, K> setter) {
    return new TraceContext.Injector<C>() {
      @Override
      public void inject(final TraceContext traceContext, final C carrier) {
        for (final Propagation<K> propagation : propagations) {
          propagation.injector(setter).inject(traceContext, carrier);
        }
      }
    };
  }

  @Override public <C> TraceContext.Extractor<C> extractor(final Getter<C, K> getter) {
    return new TraceContext.Extractor<C>() {
      @Override
      public TraceContextOrSamplingFlags extract(final C carrier) {
        for (final Propagation<K> propagation : propagations) {
          final TraceContextOrSamplingFlags result = propagation.extractor(getter).extract(carrier);
          if (SamplingFlags.EMPTY != result.samplingFlags()) {
            return result;
          }
        }
        return TraceContextOrSamplingFlags.EMPTY;
      }
    };
  }
}
