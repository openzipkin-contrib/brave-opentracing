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
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class OpenTracing0_33_BraveSpanBuilderTest {
  Tracing tracing = Tracing.newBuilder().build();

  @After public void clear() {
    tracing.close();
  }

  /** Ensures when the caller invokes with null, nothing happens */
  @Test public void asChildOf_nullParentContext_noop() {
    BraveSpanBuilder builder = newSpanBuilder().asChildOf((SpanContext) null);

    assertThat(builder)
        .isEqualToComparingFieldByFieldRecursively(newSpanBuilder());
  }

  /** Ensures when the caller invokes with null, nothing happens */
  @Test public void asChildOf_nullParent_noop() {
    BraveSpanBuilder builder = newSpanBuilder().asChildOf((Span) null);

    assertThat(builder)
        .isEqualToComparingFieldByFieldRecursively(newSpanBuilder());
  }

  /** Ensures when the caller invokes with null, nothing happens */
  @Test public void addReference_nullContext_noop() {
    BraveSpanBuilder builder = newSpanBuilder().addReference(References.CHILD_OF, null);

    assertThat(builder)
        .isEqualToComparingFieldByFieldRecursively(newSpanBuilder());
  }

  BraveSpanBuilder newSpanBuilder() {
    // hijacking nullability as tracer isn't referenced until build, making easier comparisons
    return new BraveSpanBuilder(tracing, "foo");
  }
}
