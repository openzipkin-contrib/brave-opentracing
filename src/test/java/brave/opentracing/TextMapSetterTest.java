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

import brave.propagation.Propagation.Setter;
import brave.test.propagation.PropagationSetterTest;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapAdapter;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Verifies the method reference {@link TextMap#put} works as a Brave propagation setter */
public class TextMapSetterTest extends PropagationSetterTest<TextMap> {
  LinkedHashMap<String, String> delegate = new LinkedHashMap<>();
  TextMap request = new TextMapAdapter(delegate);

  @Override protected TextMap request() {
    return request;
  }

  @Override protected Setter<TextMap, String> setter() {
    return TextMap::put;
  }

  @Override protected Iterable<String> read(TextMap carrier, String key) {
    String value = delegate.get(key);
    return value != null ? Collections.singletonList(value) : Collections.emptyList();
  }
}
