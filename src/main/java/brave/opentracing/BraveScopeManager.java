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

import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

/** This integrates with Brave's {@link CurrentTraceContext}. */
public final class BraveScopeManager implements ScopeManager {

  private static final Logger logger = Logger.getLogger(BraveScopeManager.class.getName());

  final ThreadLocal<Deque<BraveScope>> currentScopes = new ThreadLocal<Deque<BraveScope>>() {
    @Override protected Deque<BraveScope> initialValue() {
      return new ArrayDeque<>();
    }
  };
  private final Tracer tracer;

  BraveScopeManager(Tracing tracing) {
    tracer = tracing.tracer();
  }

  /**
   * This api's only purpose is to retrieve the {@link Scope#span() span}.
   *
   * Calling {@link Scope#close() close } on the returned scope has no effect on the active span
   */
  @Override public Scope active() {
    BraveSpan span = currentSpan();
    if (span == null) return null;
    return new Scope() {
      @Override public void close() {
        // no-op
      }

      @Override public Span span() {
        return span;
      }
    };
  }

  /** Attempts to get a span from the current api, falling back to brave's native one */
  BraveSpan currentSpan() {
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

  @Override public BraveScope activate(Span span, boolean finishSpanOnClose) {
    if (span == null) return null;
    if (!(span instanceof BraveSpan)) {
      throw new IllegalArgumentException(
          "Span must be an instance of brave.opentracing.BraveSpan, but was " + span.getClass());
    }
    return newScope((BraveSpan) span, finishSpanOnClose);
  }

  BraveScope newScope(BraveSpan span, boolean finishSpanOnClose) {
    BraveScope result = new BraveScope(
        this,
        tracer.withSpanInScope(span.delegate),
        span,
        finishSpanOnClose,
        getTracker(span)
    );
    currentScopes.get().addFirst(result);
    return result;
  }

  boolean deregister(BraveScope scope) {
    Deque<BraveScope> scopeStack = currentScopes.get();
    BraveScope currentScope = scopeStack.peekFirst();
    if (scope.equals(currentScope)) {
      scopeStack.pollFirst();
      return true;
    }

    //scope mismatch
    if (currentScope != null) {
      scopeStack.remove(scope);//try our best to remove something to avoid memory leak
    }

    if (scope.tracker == null) {
      return false; // only do further logging against tracker exist.
    }

    if (Thread.currentThread().getId() != scope.tracker.threadId) {
      logger.log(Level.WARNING,
          "Closing scope in wrong thread: " + Thread.currentThread().getName(),
          scope.tracker.caller);
      return false;
    }

    //scope mismatch
    if (currentScope != null && currentScope.tracker != null) {
      logger.log(Level.WARNING, "Child scope not closed when closing: " + scope,
          currentScope.tracker.caller);
    }

    return false;
  }

  private ScopeCloseTracker getTracker(BraveSpan span) {
    if (logger.isLoggable(Level.FINE)) {
      return new ScopeCloseTracker(span);
    }
    return null;
  }

  //This object is expensive
  static class ScopeCloseTracker {

    final long threadId;
    final Throwable caller;

    ScopeCloseTracker(BraveSpan span) {
      this.threadId = Thread.currentThread().getId();
      this.caller = new Error(String.format("Thread %s opened BraveScope for %s here:",
          Thread.currentThread().getName(), span));
    }
  }
}
