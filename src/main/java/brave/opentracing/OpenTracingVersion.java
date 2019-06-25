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
import io.opentracing.ScopeManager;
import io.opentracing.Span;

/**
 * Access to version-specific features.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.OpenTracingVersion}
 */
abstract class OpenTracingVersion {
  private static final OpenTracingVersion INSTANCE = findVersion();

  static OpenTracingVersion get() {
    return INSTANCE;
  }

  BraveScopeManager scopeManager(Tracing tracing) {
    return new BraveScopeManager(tracing.tracer());
  }

  BraveSpanBuilder spanBuilder(BraveTracer braveTracer, String operationName) {
    return new BraveSpanBuilder(braveTracer.delegate, operationName);
  }

  /** Attempt to match the host runtime to a capable OpenTracingVersion implementation. */
  private static OpenTracingVersion findVersion() {
    OpenTracingVersion version = v0_32.buildIfSupported();
    if (version != null) return version;

    version = v0_33.buildIfSupported();
    if (version != null) return version;

    throw new UnsupportedOperationException("Unsupported opentracing-api version");
  }

  static class v0_32 extends OpenTracingVersion {
    static v0_32 buildIfSupported() {
      // Find OpenTracing 0.32 deprecated method
      try {
        if (ScopeManager.class.getMethod("activate", Span.class, boolean.class)
            .getAnnotation(Deprecated.class) != null) {
          return new v0_32();
        }
      } catch (NoSuchMethodException e) {
      }
      return null;
    }

    @Override BraveScopeManager scopeManager(Tracing tracing) {
      return new v0_32_BraveScopeManager(tracing.tracer());
    }

    @Override BraveSpanBuilder spanBuilder(BraveTracer braveTracer, String operationName) {
      return new v0_32_BraveSpanBuilder(braveTracer.scopeManager, operationName);
    }

    @Override public String toString() {
      return "v0_32{}";
    }

    v0_32() {
    }
  }

  static class v0_33 extends OpenTracingVersion {
    static v0_33 buildIfSupported() {
      // Find OpenTracing 0.32 new type
      try {
        Class.forName("io.opentracing.tag.Tag");
        return new v0_33();
      } catch (ClassNotFoundException e) {
      }
      return null;
    }

    @Override public String toString() {
      return "v0_33{}";
    }

    v0_33() {
    }
  }
}
