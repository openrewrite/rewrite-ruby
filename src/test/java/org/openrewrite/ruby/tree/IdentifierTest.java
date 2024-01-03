package org.openrewrite.ruby.tree;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class IdentifierTest implements RewriteTest {

    @Test
    void nil() {
        rewriteRun(
          ruby(
            """
              nil
              """
          )
        );
    }
}
