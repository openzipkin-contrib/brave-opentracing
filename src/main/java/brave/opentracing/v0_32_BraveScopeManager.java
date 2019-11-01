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
import brave.propagation.CurrentTraceContext;
import io.opentracing.Scope;
import io.opentracing.Span;
import java.util.ArrayDeque;
import java.util.Deque;

/** This integrates with Brave's {@link CurrentTraceContext}. */
final class v0_32_BraveScopeManager extends BraveScopeManager {
  // This probably needs to be redesigned to stash the OpenTracing span in brave's .extra()
  // We wouldn't have to do this if it weren't a requirement to return the same instance...
  //
  // When scopes are leaked this thread local will prevent this type from being unloaded. This can
  // cause problems in redeployment scenarios. https://github.com/openzipkin/brave/issues/785
  final ThreadLocal<Deque<v0_32_BraveScope>> currentScopes =
      new ThreadLocal<Deque<v0_32_BraveScope>>() {
        @Override protected Deque<v0_32_BraveScope> initialValue() {
          return new ArrayDeque<>();
        }
      };

  v0_32_BraveScopeManager(Tracing tracing) {
    super(tracing);
  }

  @Override @Deprecated public Scope active() {
    BraveSpan span = currentSpan();
    if (span == null) return null;
    return new Scope() {
      @Override public void close() {
        // no-op
      }

      /* @Override deprecated 0.32 method: Intentionally no override to ensure 0.33 works! */
      @Deprecated public Span span() {
        return span;
      }
    };
  }

  @Override @Deprecated BraveSpan currentSpan() {
    BraveScope scope = currentScopes.get().peekFirst();
    if (scope != null) {
      return scope.span();
    } else {
      brave.Span braveSpan = tracer.currentSpan();
      if (braveSpan != null) {
        return new BraveSpan(tracer, braveSpan);
      }
    }
    return null;
  }

  @Override public BraveScope activate(Span span) {
    return activate(span, false);
  }

  @Override @Deprecated public BraveScope activate(Span span, boolean finishSpanOnClose) {
    if (span == null) return null;
    if (!(span instanceof BraveSpan)) {
      throw new IllegalArgumentException(
          "Span must be an instance of brave.opentracing.BraveSpan, but was " + span.getClass());
    }
    return newScope((BraveSpan) span, finishSpanOnClose);
  }

  BraveScope newScope(BraveSpan span, boolean finishSpanOnClose) {
    v0_32_BraveScope result = new v0_32_BraveScope(
        tracer.withSpanInScope(span.delegate), this, span, finishSpanOnClose
    );
    currentScopes.get().addFirst(result);
    return result;
  }

  void deregister(BraveScope span) {
    currentScopes.get().remove(span);
  }
}
