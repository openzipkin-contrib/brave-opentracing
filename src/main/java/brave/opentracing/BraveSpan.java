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

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import java.util.Iterator;
import java.util.Map;

/**
 * Holds the {@linkplain brave.Span} used by the underlying {@linkplain brave.Tracer}.\
 *
 * <p>This type also includes hooks to integrate with the underlying {@linkplain brave.Tracer}.
 * Ex you can access the underlying span with {@link #unwrap}
 */
public final class BraveSpan extends BraveBaseSpan<Span> implements Span {
  /**
   * Converts an existing {@linkplain brave.Span} for use in OpenTracing apis
   */
  static BraveSpan wrap(brave.Span span) {
    if (span == null) throw new NullPointerException("span == null");
    return new BraveSpan(span);
  }

  private BraveSpan(brave.Span delegate) {
    super(delegate);
  }
}
