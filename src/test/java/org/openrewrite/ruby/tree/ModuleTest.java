package org.openrewrite.ruby.tree;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class ModuleTest implements RewriteTest {

    @Test
    void module() {
        rewriteRun(
          ruby(
            """
              module Base64
                  DIGITS = '0123456789'
              end
              """
          )
        );
    }
}
