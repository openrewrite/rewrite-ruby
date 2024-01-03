package integ;

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

    // 12
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
}
