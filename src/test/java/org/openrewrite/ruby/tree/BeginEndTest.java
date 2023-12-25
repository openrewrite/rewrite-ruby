package org.openrewrite.ruby.tree;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class BeginEndTest implements RewriteTest {

    @ParameterizedTest
    @ValueSource(strings = {"BEGIN", "END"})
    void singleStatement(String beginOrEnd) {
        rewriteRun(
          ruby(
            """
              %s {
                puts "Hello, World!"
              }
              """.formatted(beginOrEnd)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"BEGIN", "END"})
    void multipleStatement(String beginOrEnd) {
        rewriteRun(
          ruby(
            """
              %s {
                puts "Hello, World!"
                puts "Goodbye, World!"
              }
              """.formatted(beginOrEnd)
          )
        );
    }
}
