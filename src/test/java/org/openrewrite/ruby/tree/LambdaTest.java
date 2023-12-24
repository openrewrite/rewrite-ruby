package org.openrewrite.ruby.tree;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class LambdaTest implements RewriteTest {

    @Test
    void passBlock() {
        rewriteRun(
          ruby(
            """
              printer = lambda {|&b| puts b.call }
              printer.call { "hi" }
              """
          )
        );
    }
}
