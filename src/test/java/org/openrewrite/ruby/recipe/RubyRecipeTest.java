package org.openrewrite.ruby.recipe;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class RubyRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RubyRecipe("/io_methods.rb"));
    }

    @Test
    void ioMethods() {
        rewriteRun(
          ruby(
            """
              IO.read(path)
              IO.read('path')
              """,
            """
              File.read(path)
              File.read('path')
              """
          )
        );
    }
}
