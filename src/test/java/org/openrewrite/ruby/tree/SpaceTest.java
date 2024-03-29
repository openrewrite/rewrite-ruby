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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.ruby.Assertions.ruby;

public class SpaceTest implements RewriteTest {

    @Test
    void newlineEscape() {
        rewriteRun(
          ruby(
            """
              a = 1 \\
                + 2
              """,
            spec -> spec.afterRecipe(cu -> assertThat(cu.getStatements()).hasSize(1))
          )
        );
    }

    @Test
    void singleLineComment() {
        rewriteRun(
          ruby(
            """
              #!/usr/bin/ruby -w
              # This is a single line comment.
                            
              puts "Hello, Ruby!"
              """
          )
        );
    }

    @Test
    void trailingComment() {
        rewriteRun(
          ruby(
            """
              counter = 42    # keeps track times page has been hit
              """
          )
        );
    }

    @Test
    void multiLineComment() {
        rewriteRun(
          ruby(
            """
              #!/usr/bin/ruby -w
                                  
              puts("Hello, Ruby!")
                                 
              =begin
              This is a multiline comment and can span as many lines as you
              like. But =begin and =end should come in the first line only.\s
              =end
              """
          )
        );
    }
}
