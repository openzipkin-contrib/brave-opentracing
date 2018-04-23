/**
 * Copyright 2016-2018 The OpenZipkin Authors
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
import brave.opentracing.BraveScopeManager.ScopeCloseTracker;

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
  final ScopeCloseTracker tracker;
  boolean closedCalled;

  /**
   * @param source the BraveScopeManager that created this BraveScope
   * @param scope a SpanInScope to be closed upon close of this BraveScope
   * @param wrapped the wrapped BraveSpan to which we will delegate all span operations
   * @param finishSpanOnClose whether to finish span when closing this BraveScope
   * @param tracker an tracker object which can help user find the root cause when improperly closing this BraveScope.
   */
  BraveScope(BraveScopeManager source, SpanInScope scope, BraveSpan wrapped,
      boolean finishSpanOnClose,
      ScopeCloseTracker tracker) {
    this.source = source;
    this.scope = scope;
    this.wrapped = wrapped;
    this.finishSpanOnClose = finishSpanOnClose;
    this.tracker = tracker;
  }

  @Override public void close() {
    if (finishSpanOnClose) {
      wrapped.finish();
    }
    scope.close();
    if (!closedCalled) {
      closedCalled = source.deregister(this);
    }
  }

  @Override public BraveSpan span() {
    return wrapped;
  }

  @Override public String toString() {
    return "BraveScope{scope=" + scope + ", wrapped=" + wrapped + '}';
  }
}
