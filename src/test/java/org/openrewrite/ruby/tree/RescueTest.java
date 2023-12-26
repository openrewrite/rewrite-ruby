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

public class RescueTest implements RewriteTest {

    @Test
    void rescueExceptionUnnamed() {
        rewriteRun(
          ruby(
            """
              begin
                x = factorial(1)
              rescue Exception
                puts "Try again with a value >= 1"
              end
              """
          )
        );
    }

    @Test
    void rescueExceptionUnnamedMultipleStatements() {
        rewriteRun(
          ruby(
            """
              begin
                x = factorial(1)
              rescue Exception
                puts "Try again with a value >= 1"
                puts "Or try something else"
              end
              """
          )
        );
    }

    @Test
    void rescueExceptionNamed() {
        rewriteRun(
          ruby(
            """
              begin
                x = factorial(1)
              rescue Exception => ex
                puts "Try again with a value >= 1"
              end
              """
          )
        );
    }

    @Test
    void retry() {
        rewriteRun(
          ruby(
            """
              begin
                x = factorial(1)
              rescue Exception
                retry
              end
              """
          )
        );
    }

    @Test
    void multipleRescuesWithTypesAndNames() {
        rewriteRun(
          ruby(
            """
              begin
                x = factorial(1)
              rescue ArgumentError => ex
                puts "Try again with a value >= 1"
              rescue TypeError => ex
                puts "Try again with an integer"
              end
              """
          )
        );
    }

    @Test
    void multipleTypesInOneRescue() {
        rewriteRun(
          ruby(
            """
              begin
                x = factorial(1)
              rescue ArgumentError, TypeError => ex
                puts "Try again"
              end
              """
          )
        );
    }

    @Test
    void ensure() {
        rewriteRun(
          ruby(
            """
              begin
                x = factorial(1)
              rescue Exception
                puts "Try again with a value >= 1"
              ensure
                puts "Ensured"
              end
              """
          )
        );
    }

    @Test
    void elseClause() {
        rewriteRun(
          ruby(
            """
              begin
                x = factorial(1)
              rescue Exception
                puts "Try again with a value >= 1"
              else
                puts "Else"
              end
              """
          )
        );
    }

    // FIXME see 5.6.6 since this is also possible on class and module defs as well
    @Test
    void rescueOnMethodDef() {
        rewriteRun(
          ruby(
            """
              def sum(a, b)
                  a + b
              rescue Exception
                  puts "Not reachable"
              end
              """
          )
        );
    }
}
