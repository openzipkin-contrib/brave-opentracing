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

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;

/**
 * Temporary fix. This functionality should be migrated into the Brave library.
 *
 * @author jrobb
 * @version $Id$
 */
public class BraveOpenTracingCurrentTraceContext extends CurrentTraceContext {
  static final InheritableThreadLocal<ScopeAndTraceContext> local = new InheritableThreadLocal<>();

  @Override public TraceContext get() {
    ScopeAndTraceContext current = local.get();
    if (current == null) return null;
    return current.getTraceContext();
  }

  public Scope getScope() {
    ScopeAndTraceContext current = local.get();
    if (current == null) return null;
    return current.getScope();
  }

  @Override public Scope newScope(TraceContext currentSpan) {
    final ScopeAndTraceContext previous = local.get();

    if (previous != null && previous.getTraceContext().equals(currentSpan)) {
      // it's important that we don't add multiple identical spans to the chain;
      // otherwise, deactivating doesn't unwind correctly
      return previous.getScope();
    }

    ScopeAndTraceContext newValue = new ScopeAndTraceContext(() -> local.set(previous), currentSpan);
    local.set(newValue);
    return newValue.getScope();
  }

  private static class ScopeAndTraceContext {
    private final Scope scope;
    private final TraceContext traceContext;

    private ScopeAndTraceContext(Scope scope, TraceContext traceContext) {
      this.scope = scope;
      this.traceContext = traceContext;
    }

    private Scope getScope() {
      return scope;
    }

    private TraceContext getTraceContext() {
      return traceContext;
    }
  }
}
