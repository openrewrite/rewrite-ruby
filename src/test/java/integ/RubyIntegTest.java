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
package integ;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.ruby.RubyParser;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.openrewrite.ruby.Assertions.ruby;

public class RubyIntegTest implements RewriteTest {

    static Path REPOSITORY = Paths.get(System.getProperty("user.home"), "Projects/github/ruby/dependabot-core");

    @ParameterizedTest
    @MethodSource("files")
    void parse(Path file) throws IOException {
        System.out.println("file://" + REPOSITORY.resolve(file));
        rewriteRun(
          ruby(
            Files.readString(REPOSITORY.resolve(file))
          )
        );
    }

    static Stream<Path> files() throws IOException {
        RubyParser parser = RubyParser.builder().build();
        //noinspection resource
        return Files.walk(REPOSITORY)
          .filter(parser::accept)
          .map(p -> REPOSITORY.relativize(p));
    }

    @Test
    void test() {
        rewriteRun(
          ruby(
            """
              """
          )
        );
    }
}
