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

final class v0_32_BraveSpanBuilder extends BraveSpanBuilder {
  final BraveScopeManager scopeManager;

  v0_32_BraveSpanBuilder(BraveScopeManager scopeManager, String operationName) {
    super(scopeManager.tracing, operationName);
    this.scopeManager = scopeManager;
  }

  @Override @Deprecated public BraveSpan startManual() {
    return start();
  }

  @Override @Deprecated public BraveScope startActive(boolean finishSpanOnClose) {
    if (!ignoreActiveSpan) {
      BraveSpan parent = scopeManager.activeSpan();
      if (parent != null) asChildOf(parent.context());
    }
    return scopeManager.activate(start(), finishSpanOnClose);
  }
}
