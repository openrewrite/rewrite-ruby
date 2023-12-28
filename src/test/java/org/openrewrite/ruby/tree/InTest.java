package org.openrewrite.ruby.tree;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class InTest implements RewriteTest {

    @Test
    void inCheck() {
        rewriteRun(
          ruby(
            """
              if user in {role: 'admin', login:}
                puts "Granting admin scope: #{login}"
              end
              """
          )
        );
    }
}
