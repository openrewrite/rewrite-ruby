package org.openrewrite.ruby.recipe;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Set;

// https://dior.ics.muni.cz/~makub/ruby/
@RequiredArgsConstructor
public class RubyRecipe extends Recipe {
    private static final ScriptEngine JRUBY = new ScriptEngineManager().getEngineByName("jruby");

    @Getter
    private final String path;

    @Nullable
    private transient Recipe delegate;

    @Override
    public String getName() {
        return "org.openrewrite.ruby.recipe.RubyRecipe$" + path;
    }

    @Override
    public String getDisplayName() {
        return delegate().getDisplayName();
    }

    @Override
    public String getDescription() {
        return delegate().getDescription();
    }

    @Override
    public Set<String> getTags() {
        return delegate().getTags();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return delegate().getVisitor();
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return delegate().getEstimatedEffortPerOccurrence();
    }

    private Recipe delegate() {
        if (delegate == null) {
            delegate = load(path);
        }
        return delegate;
    }

    private static Recipe load(String path) {
        try (InputStream is = RubyRecipe.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException(path + " does not exist as a resource on the classpath.");
            }
            try (InputStreamReader reader = new InputStreamReader(is);
                 BufferedReader buf = new BufferedReader(reader)) {
                JRUBY.eval(buf);
                Object recipe = JRUBY.eval("IOMethods.new");
                return (Recipe) recipe;
            }
        } catch (ScriptException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
