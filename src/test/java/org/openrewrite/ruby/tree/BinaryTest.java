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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class BinaryTest implements RewriteTest {

    @ParameterizedTest
    @ValueSource(strings = {"+", "-", "*", "/", "%", "**"})
    void arithmetic(String op) {
        rewriteRun(
          ruby(
            """
              42 %s 1
              """.formatted(op)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"<<", ">>", "&", "|", "^"})
    void bitwise(String op) {
        rewriteRun(
          ruby(
            """
              42 %s 1
              """.formatted(op)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"==", "!=", "===", "<", "<=", ">", ">=", "<=>"})
    void comparison(String op) {
        rewriteRun(
          ruby(
            """
              42 %s 1
              """.formatted(op)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"&&", "and", "or", "||"})
    void logical(String op) {
        rewriteRun(
          ruby(
            """
              42 %s 1
              """.formatted(op)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "..."})
    void range(String op) {
        rewriteRun(
          ruby(
            """
              42 %s 1
              """.formatted(op)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "..."})
    void flipFlop(String op) {
        rewriteRun(
          ruby(
            """
              x = 0
              if x==0%sx<=3 then
                puts "hello"
              end
              """.formatted(op)
          )
        );
    }

    @Test
    void match3() {
        rewriteRun(
          ruby(
            """
              recv =~ /value/
              """
          )
        );
    }
}
