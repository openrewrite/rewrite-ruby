/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.ruby;

import org.openrewrite.ruby.tree.Ruby;

public class RubyIsoVisitor<P> extends RubyVisitor<P> {

    @Override
    public Ruby.Array visitArray(Ruby.Array array, P p) {
        return (Ruby.Array) super.visitArray(array, p);
    }

    @Override
    public Ruby.Binary visitBinary(Ruby.Binary binary, P p) {
        return (Ruby.Binary) super.visitBinary(binary, p);
    }

    @Override
    public Ruby.CompilationUnit visitCompilationUnit(Ruby.CompilationUnit compilationUnit, P p) {
        return (Ruby.CompilationUnit) super.visitCompilationUnit(compilationUnit, p);
    }

    @Override
    public Ruby.DelimitedString visitDelimitedString(Ruby.DelimitedString delimitedString, P p) {
        return (Ruby.DelimitedString) super.visitDelimitedString(delimitedString, p);
    }

    @Override
    public Ruby.DelimitedString.Value visitDelimitedStringValue(Ruby.DelimitedString.Value value, P p) {
        return (Ruby.DelimitedString.Value) super.visitDelimitedStringValue(value, p);
    }

    @Override
    public Ruby.Expansion visitExpansion(Ruby.Expansion expansion, P p) {
        return (Ruby.Expansion) super.visitExpansion(expansion, p);
    }

    @Override
    public Ruby.Hash visitHash(Ruby.Hash hash, P p) {
        return (Ruby.Hash) super.visitHash(hash, p);
    }

    @Override
    public Ruby.KeyValue visitKeyValue(Ruby.KeyValue keyValue, P p) {
        return (Ruby.KeyValue) super.visitKeyValue(keyValue, p);
    }

    @Override
    public Ruby.MultipleAssignment visitMultipleAssignment(Ruby.MultipleAssignment multipleAssignment, P p) {
        return (Ruby.MultipleAssignment) super.visitMultipleAssignment(multipleAssignment, p);
    }

    @Override
    public Ruby.Redo visitRedo(Ruby.Redo redo, P p) {
        return (Ruby.Redo) super.visitRedo(redo, p);
    }

    @Override
    public Ruby.Yield visitYield(Ruby.Yield yield, P p) {
        return (Ruby.Yield) super.visitYield(yield, p);
    }
}
