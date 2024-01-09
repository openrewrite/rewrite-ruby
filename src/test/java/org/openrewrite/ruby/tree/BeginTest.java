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

public class BeginTest implements RewriteTest {

    @Test
    void beginExpression() {
        rewriteRun(
          ruby(
            """
              def method(a)
                @registry_client ||= begin
                  hostname = dependency_source_details.fetch(:registry_hostname)
                  RegistryClient.new(hostname: hostname, credentials: credentials)
                end
              end
              """
          )
        );
    }
}
