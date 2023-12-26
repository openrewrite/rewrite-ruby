package org.openrewrite.ruby.tree;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class AliasTest implements RewriteTest {

    @Test
    void alias() {
        rewriteRun(
          ruby(
            """
              alias new_name old_name
              """
          )
        );
    }
}
