/*
 * Copyright 2016-2019 The OpenZipkin Authors
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
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class OpenTracing0_31_UnsupportedTest {
  @Test public void unsupported() {
    try (Tracing brave = Tracing.newBuilder().build()) {
      BraveTracer.create(brave);

      failBecauseExceptionWasNotThrown(ExceptionInInitializerError.class);
    } catch (UnsupportedOperationException e) {
      assertThat(e.getMessage()).startsWith("OpenTracing 0.31 detected.");
    }
  }
}
