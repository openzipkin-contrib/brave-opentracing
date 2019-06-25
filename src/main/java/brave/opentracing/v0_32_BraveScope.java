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

final class v0_32_BraveScope extends BraveScope {
  final v0_32_BraveScopeManager source;
  final BraveSpan wrapped;
  final boolean finishSpanOnClose;

  /**
   * @param delegate a SpanInScope to be closed upon deactivation of this ActiveSpan
   * @param source the BraveActiveSpanSource that created this BraveActiveSpan
   * @param wrapped the wrapped BraveSpan to which we will delegate all span operations
   */
  v0_32_BraveScope(SpanInScope delegate, v0_32_BraveScopeManager source, BraveSpan wrapped,
      boolean finishSpanOnClose) {
    super(delegate);
    this.source = source;
    this.wrapped = wrapped;
    this.finishSpanOnClose = finishSpanOnClose;
  }

  @Override public void close() {
    super.close();
    if (finishSpanOnClose) wrapped.finish();
    source.deregister(this);
  }

  @Override @Deprecated public BraveSpan span() {
    return wrapped;
  }

  @Override public String toString() {
    return "BraveScope{scope=" + delegate + ", wrapped=" + wrapped.delegate + '}';
  }
}
