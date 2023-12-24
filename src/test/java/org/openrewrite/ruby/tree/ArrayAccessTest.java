/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.ruby.tree;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class ArrayAccessTest implements RewriteTest {

    @Test
    void stringAccess() {
        rewriteRun(
          ruby(
            """
              s = 'hello'
              s[0]
              s[-1]
              s[-s.length]
              s[s.length-1]
              """
          )
        );
    }

    @Test
    void subarray() {
        rewriteRun(
          ruby(
            """
              a = [0, 1, 2]
              a[0, 2]
              """
          )
        );
    }

    @Test
    void range() {
        rewriteRun(
          ruby(
            """
              a = [0, 1, 2]
              a[0..1]
              """
          )
        );
    }
}
