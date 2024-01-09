package org.openrewrite.ruby.tree;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class BeginTest implements RewriteTest {

    @Test
    void beginExpression() {
        rewriteRun(
          ruby(
            """
              @registry_client ||= begin
                hostname = dependency_source_details.fetch(:registry_hostname)
                RegistryClient.new(hostname: hostname, credentials: credentials)
              end
              """
          )
        );
    }
}
