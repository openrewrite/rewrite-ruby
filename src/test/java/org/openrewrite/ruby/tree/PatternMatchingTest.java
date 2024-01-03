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

/**
 * For a comprehensive summary of the history of pattern matching in Ruby, see
 * <a href="https://www.alchemists.io/articles/ruby_pattern_matching">this blog post</a>.
 */
public class PatternMatchingTest implements RewriteTest {

    @Test
    void booleanCheck() {
        rewriteRun(
          ruby(
            """
              basket = [{kind: "apple", quantity: 1}, {kind: "peach", quantity: 5}]
                            
              basket.any? { |fruit| fruit in {kind: /app/}}      # true
              basket.any? { |fruit| fruit in {kind: /berry/}}  # false
              """
          )
        );
    }

    @Test
    void hash() {
        rewriteRun(
          ruby(
            """
              { foo: 1, bar: 2 } in { foo: f }
              """
          )
        );
    }

    @Test
    void rightwardAssignment() {
        rewriteRun(
          ruby(
            """
              value => Numeric
              """
          )
        );
    }
}
