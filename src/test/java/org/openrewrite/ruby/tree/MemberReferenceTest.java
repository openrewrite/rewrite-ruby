package org.openrewrite.ruby.tree;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class MemberReferenceTest implements RewriteTest {

    @Test
    void constant() {
        rewriteRun(
          ruby(
            """
              Encoding::UTF_8
              """
          )
        );
    }
}
