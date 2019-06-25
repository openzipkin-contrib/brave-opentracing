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

import brave.Tracer.SpanInScope;
import io.opentracing.Scope;

/**
 * {@link BraveScope} is a simple {@link Scope} implementation that wraps the corresponding
 * BraveSpan.
 *
 * @see BraveSpan
 * @see BraveScopeManager
 */
public final class BraveScope implements Scope {
  private final BraveScopeManager source;
  private final SpanInScope scope;
  private final BraveSpan wrapped;
  private final boolean finishSpanOnClose;

  /**
   * @param source the BraveActiveSpanSource that created this BraveActiveSpan
   * @param scope a SpanInScope to be closed upon deactivation of this ActiveSpan
   * @param wrapped the wrapped BraveSpan to which we will delegate all span operations
   */
  BraveScope(BraveScopeManager source, SpanInScope scope, BraveSpan wrapped,
      boolean finishSpanOnClose) {
    this.source = source;
    this.scope = scope;
    this.wrapped = wrapped;
    this.finishSpanOnClose = finishSpanOnClose;
  }

  @Override public void close() {
    if (finishSpanOnClose) {
      wrapped.finish();
    }
    scope.close();
    source.deregister(this);
  }

  /* @Override deprecated 0.32 method: Intentionally no override to ensure 0.33 works! */
  @Deprecated public BraveSpan span() {
    return wrapped;
  }

  @Override public String toString() {
    return "BraveScope{scope=" + scope + ", wrapped=" + wrapped.delegate + '}';
  }
}
