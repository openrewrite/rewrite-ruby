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

public class IfTest implements RewriteTest {

    @Test
    void nextUnless() {
        rewriteRun(
          ruby(
            """
              next unless true
              """
          )
        );
    }

    @Test
    void ifModifier() {
        rewriteRun(
          ruby(
            """
              a = 0
              puts "hello" if a == 0
              """
          )
        );
    }

    @Test
    void ifModifierImplicitStatement() {
        rewriteRun(
          ruby(
            """
              latest_version_for_git_dependency if git_dependency?
              """
          )
        );
    }

    @Test
    void singleIf() {
        rewriteRun(
          ruby(
            """
              if n == 42 then
                  puts "42"
              end
              """
          )
        );
    }

    @Test
    void ifElseIfElse() {
        rewriteRun(
          ruby(
            """
              if n == 42 then
                  puts "42"
              elsif n > 42 then
                  puts "greater 42"
              elsif n < 42
                  puts "less 42"
              else
                  puts "something else"
              end
              """
          )
        );
    }

    @Test
    void ifElseIf() {
        rewriteRun(
          ruby(
            """
              if n == 42 then
                  puts "42"
              elsif n < 42
                  puts "less 42"
              end
              """
          )
        );
    }

    @Test
    void ifElse() {
        rewriteRun(
          ruby(
            """
              if n == 42 then
                  puts "42"
              else
                  puts "less 42"
              end
              """
          )
        );
    }

    @Test
    void match() {
        rewriteRun(
          ruby(
            """
              if /lit/ then
              end
              """
          )
        );
    }

    @Test
    void match2() {
        rewriteRun(
          ruby(
            """
              if /#{recv}/ then
              end
              """
          )
        );
    }

    @Test
    void ternary() {
        rewriteRun(
          ruby(
            """
              a = 0
              source_address ? 1 : 2
              """
          )
        );
    }

    @Test
    void noThen() {
        rewriteRun(
          ruby(
            """
              if current_version &&
                 version_class.correct?(current_version) &&
                 version_class.new(current_version).prerelease?
                return true
              end
              """
          )
        );
    }
}
