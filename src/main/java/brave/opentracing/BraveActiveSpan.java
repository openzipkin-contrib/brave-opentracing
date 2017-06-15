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

import brave.Tracer.SpanInScope;
import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link BraveActiveSpan} is a simple {@link ActiveSpan} implementation that wraps the
 * corresponding BraveSpan.
 *
 * @see BraveSpan
 * @see BraveActiveSpanSource
 * @see Tracer#activeSpan()
 */
public final class BraveActiveSpan extends BraveBaseSpan<ActiveSpan> implements ActiveSpan {
  private final BraveActiveSpanSource source;
  private final SpanInScope scope;
  private final BraveSpan wrapped;
  private final AtomicInteger refCount;

  /**
   * @param scope a SpanInScope to be closed upon deactivation of this ActiveSpan
   * @param wrapped the wrapped BraveSpan to which we will delegate all span operations
   * @param refCount the total number of Continuations of this ActiveSpan (new instances should pass 1)
   */
  BraveActiveSpan(BraveActiveSpanSource source, SpanInScope scope, BraveSpan wrapped, AtomicInteger refCount) {
    super(wrapped.unwrap());
    this.source = source;
    this.scope = scope;
    this.wrapped = wrapped;
    this.refCount = refCount;
  }

  @Override
  public void deactivate() {
    if (0 == refCount.decrementAndGet()) {
      wrapped.finish();
      scope.close();
      source.deregisterSpan(wrapped.unwrap());
    }
  }

  @Override
  public BraveActiveSpan.Continuation capture() {
    return new BraveActiveSpan.Continuation();
  }

  @Override
  public void close() {
    deactivate();
  }

  @Override
  public String toString() {
    return "BraveActiveSpan{" + "scope=" + scope +
        ", wrapped=" + wrapped +
        ", refCount=" + refCount +
        '}';
  }

  public final class Continuation implements ActiveSpan.Continuation {
    Continuation() {
      refCount.incrementAndGet();
    }

    @Override
    public ActiveSpan activate() {
      return new BraveActiveSpan(source, scope, wrapped, refCount);
    }
  }
}
