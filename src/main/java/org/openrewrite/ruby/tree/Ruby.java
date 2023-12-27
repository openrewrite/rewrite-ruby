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

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.ruby.RubyVisitor;
import org.openrewrite.ruby.internal.RubyPrinter;

import java.beans.Transient;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.singletonList;

@SuppressWarnings("unused")
public interface Ruby extends J {

    @Override
    default <R extends Tree, P> R accept(TreeVisitor<R, P> v, P p) {
        //noinspection unchecked
        return (R) acceptRuby(v.adapt(RubyVisitor.class), p);
    }

    @Override
    default <P> boolean isAcceptable(TreeVisitor<?, P> v, P p) {
        return v.isAdaptableTo(RubyVisitor.class);
    }

    @Nullable
    default <P> J acceptRuby(RubyVisitor<P> v, P p) {
        return v.defaultValue(this, p);
    }

    default Space getPrefix() {
        return Space.EMPTY;
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class CompilationUnit implements Ruby, SourceFile {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        @Getter
        @With
        Path sourcePath;

        @Getter
        @With
        @Nullable
        FileAttributes fileAttributes;

        @Getter
        @With
        Charset charset;

        @Getter
        @With
        boolean charsetBomMarked;

        @Getter
        @With
        @Nullable
        Checksum checksum;

        List<JRightPadded<Statement>> statements;

        public List<Statement> getStatements() {
            return JRightPadded.getElements(statements);
        }

        public Ruby.CompilationUnit withStatements(List<Statement> statements) {
            return getPadding().withStatements(JRightPadded.withElements(this.statements, statements));
        }

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitCompilationUnit(this, p);
        }

        @Override
        public <P> TreeVisitor<?, PrintOutputCapture<P>> printer(Cursor cursor) {
            return new RubyPrinter<>();
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final Ruby.CompilationUnit t;

            public List<JRightPadded<Statement>> getStatements() {
                return t.statements;
            }

            public Ruby.CompilationUnit withStatements(List<JRightPadded<Statement>> statements) {
                return t.statements == statements ? t : new Ruby.CompilationUnit(t.id, t.prefix, t.markers,
                        t.sourcePath, t.fileAttributes, t.charset, t.charsetBomMarked, t.checksum, statements);
            }
        }
    }

    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class Alias implements Ruby, Statement {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        J.Identifier newName;
        J.Identifier existingName;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitAlias(this, p);
        }

        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class Array implements Ruby, Expression, TypedTree {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        JContainer<Expression> elements;

        public List<Expression> getElements() {
            return elements.getElements();
        }

        public Array withElements(List<Expression> elements) {
            return getPadding().withElements(JContainer.withElements(this.elements, elements));
        }

        public enum Type {
            ArrayLiteral,

            /**
             * When using a splat along with an argument <code>1, *a</code>
             */
            ArgumentConcatenation
        }

        @Getter
        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitArray(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final Array t;

            public JContainer<Expression> getElements() {
                return t.elements;
            }

            public Array withElements(JContainer<Expression> elements) {
                return t.elements == elements ? t : new Array(t.id, t.prefix, t.markers, elements, t.type);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class Block implements Ruby, Expression {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        /**
         * Inline blocks are delimited by { }.
         * Multiline blocks are delimited by do ... end.
         */
        @Getter
        @With
        boolean inline;

        @Nullable
        JContainer<J> parameters;

        @Nullable
        public List<J> getParameters() {
            return parameters == null ? null : parameters.getElements();
        }

        public Ruby.Block withParameters(@Nullable List<J> parameters) {
            return getPadding().withParameters(JContainer.withElementsNullable(this.parameters, parameters));
        }

        @Getter
        @With
        J.Block body;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitBlock(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        @Override
        public @Nullable JavaType getType() {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Ruby.Block withType(@Nullable JavaType type) {
            return this;
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final Ruby.Block t;

            @Nullable
            public JContainer<J> getParameters() {
                return t.parameters;
            }

            public Ruby.Block withParameters(@Nullable JContainer<J> parameters) {
                return t.parameters == parameters ? t : new Ruby.Block(t.id, t.prefix, t.markers, t.inline, parameters, t.body);
            }
        }
    }

    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class BlockArgument implements Ruby, Expression {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        Expression argument;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitBlockArgument(this, p);
        }

        @Override
        public @Nullable JavaType getType() {
            return argument.getType();
        }

        @SuppressWarnings("unchecked")
        @Override
        public BlockArgument withType(@Nullable JavaType type) {
            return withArgument(argument.withType(type));
        }

        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class AssignmentOperation implements Ruby, Statement, Expression, TypedTree {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @With
        @EqualsAndHashCode.Include
        @Getter
        UUID id;

        @With
        @Getter
        Space prefix;

        @With
        @Getter
        Markers markers;

        @With
        @Getter
        Expression variable;

        JLeftPadded<Ruby.AssignmentOperation.Type> operator;

        public Ruby.AssignmentOperation.Type getOperator() {
            return operator.getElement();
        }

        public Ruby.AssignmentOperation withOperator(Ruby.AssignmentOperation.Type operator) {
            return getPadding().withOperator(this.operator.withElement(operator));
        }

        @With
        @Getter
        Expression assignment;

        @With
        @Nullable
        @Getter
        JavaType type;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitAssignmentOperation(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        @Override
        @Transient
        public List<J> getSideEffects() {
            return singletonList(this);
        }

        public enum Type {
            And,
            Or
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @Override
        public String toString() {
            return withPrefix(Space.EMPTY).printTrimmed(new JavaPrinter<>());
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final Ruby.AssignmentOperation t;

            public JLeftPadded<Ruby.AssignmentOperation.Type> getOperator() {
                return t.operator;
            }

            public Ruby.AssignmentOperation withOperator(JLeftPadded<Ruby.AssignmentOperation.Type> operator) {
                return t.operator == operator ? t : new Ruby.AssignmentOperation(t.id, t.prefix, t.markers, t.variable, operator, t.assignment, t.type);
            }
        }
    }

    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class Begin implements Ruby, Statement {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        J.Block block;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitBegin(this, p);
        }

        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class Binary implements Ruby, Expression, TypedTree {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        @Getter
        @With
        Expression left;

        JLeftPadded<Ruby.Binary.Type> operator;

        public Ruby.Binary.Type getOperator() {
            return operator.getElement();
        }

        public Ruby.Binary withOperator(Ruby.Binary.Type operator) {
            return getPadding().withOperator(this.operator.withElement(operator));
        }

        @Getter
        @With
        Expression right;

        @Getter
        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitBinary(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public enum Type {
            Comparison,
            Exponentiation,
            FlipFlopExclusive,
            FlipFlopInclusive,
            Match,
            RangeExclusive,
            RangeInclusive,
            Within,
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final Ruby.Binary t;

            public JLeftPadded<Ruby.Binary.Type> getOperator() {
                return t.operator;
            }

            public Ruby.Binary withOperator(JLeftPadded<Ruby.Binary.Type> operator) {
                return t.operator == operator ? t : new Ruby.Binary(t.id, t.prefix, t.markers, t.left, operator, t.right, t.type);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class ClassMethod implements Ruby, Statement, TypedTree {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        @Getter
        @With
        Expression receiver;

        JLeftPadded<J.MethodDeclaration> method;

        public J.MethodDeclaration getMethod() {
            return method.getElement();
        }

        public ClassMethod withMethod(J.MethodDeclaration method) {
            return getPadding().withMethod(this.method.withElement(method));
        }

        @Override
        public @Nullable JavaType getType() {
            return method.getElement().getType();
        }

        @SuppressWarnings("unchecked")
        @Override
        public ClassMethod withType(@Nullable JavaType type) {
            return withMethod(method.getElement().withType(type));
        }

        public @Nullable JavaType.Method getMethodTye() {
            return method.getElement().getMethodType();
        }

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitClassMethod(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final ClassMethod t;

            public JLeftPadded<J.MethodDeclaration> getMethod() {
                return t.method;
            }

            public ClassMethod withMethod(JLeftPadded<J.MethodDeclaration> method) {
                return t.method == method ? t : new ClassMethod(t.id, t.prefix, t.markers, t.receiver, method);
            }
        }
    }

    @ToString
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Getter
    @AllArgsConstructor
    @With
    class DelimitedString implements Ruby, Statement, Expression {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        String delimiter;
        List<J> strings;
        List<RegexpOptions> regexpOptions;

        @Nullable
        JavaType type;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitDelimitedString(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        @lombok.Value
        @With
        @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
        public static class Value implements Ruby {
            @EqualsAndHashCode.Include
            UUID id;

            Markers markers;
            J tree;
            Space after;

            @Override
            public <J2 extends J> J2 withPrefix(Space space) {
                //noinspection unchecked
                return (J2) this;
            }

            @Override
            public <P> J acceptRuby(RubyVisitor<P> v, P p) {
                return v.visitDelimitedStringValue(this, p);
            }
        }

        public enum RegexpOptions {
            IgnoreCase,
            Multiline,
            Extended,
            Java,
            Once,
            None,
            EUCJPEncoding,
            SJISEncoding,
            UTF8Encoding,
        }
    }

    /**
     * Unlike Java, Ruby allows expressions to appear anywhere Statements do.
     * Rather than re-define versions of the many J types that implement Expression to also implement Statement,
     * just wrap such expressions.
     * <p>
     * Has no state or behavior of its own aside from the Expression it wraps.
     */
    @SuppressWarnings("unchecked")
    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class ExpressionStatement implements Ruby, Expression, Statement {
        @EqualsAndHashCode.Include
        UUID id;

        Expression expression;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            J j = v.visit(getExpression(), p);
            if (j instanceof ExpressionStatement) {
                return j;
            } else if (j instanceof Expression) {
                return withExpression((Expression) j);
            }
            return j;
        }

        @Override
        public <J2 extends J> J2 withPrefix(Space space) {
            return (J2) withExpression(expression.withPrefix(space));
        }

        @Override
        public Space getPrefix() {
            return expression.getPrefix();
        }

        @Override
        public <J2 extends Tree> J2 withMarkers(Markers markers) {
            return (J2) withExpression(expression.withMarkers(markers));
        }

        @Override
        public Markers getMarkers() {
            return expression.getMarkers();
        }

        @Override
        public @Nullable JavaType getType() {
            return expression.getType();
        }

        @Override
        public <T extends J> T withType(@Nullable JavaType type) {
            return (T) withExpression(expression.withType(type));
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class Hash implements Ruby, Expression, TypedTree {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        JContainer<KeyValue> elements;

        public List<KeyValue> getElements() {
            return elements.getElements();
        }

        public Hash withElements(List<KeyValue> elements) {
            return getPadding().withElements(JContainer.withElements(this.elements, elements));
        }

        @Getter
        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitHash(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final Hash t;

            public JContainer<KeyValue> getElements() {
                return t.elements;
            }

            public Hash withElements(JContainer<KeyValue> elements) {
                return t.elements == elements ? t : new Hash(t.id, t.prefix, t.markers, elements, t.type);
            }
        }
    }

    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class End implements Ruby, Statement {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        J.Block block;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitEnd(this, p);
        }

        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }
    }

    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class Expansion implements Ruby, Expression {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        TypedTree tree;

        @Override
        @Nullable
        public JavaType getType() {
            return tree.getType();
        }

        @Override
        public <T extends J> T withType(@Nullable JavaType type) {
            return tree.withType(type);
        }

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitExpansion(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class KeyValue implements Ruby, Expression, TypedTree {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        @Getter
        @With
        Expression key;

        JLeftPadded<Expression> value;

        public Expression getValue() {
            return value.getElement();
        }

        public KeyValue withValue(Expression value) {
            return getPadding().withValue(JLeftPadded.withElement(this.value, value));
        }

        @Getter
        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitKeyValue(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final KeyValue t;

            public JLeftPadded<Expression> getValue() {
                return t.value;
            }

            public KeyValue withValue(JLeftPadded<Expression> value) {
                return t.value == value ? t : new KeyValue(t.id, t.prefix, t.markers, t.key, value, t.type);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class MultipleAssignment implements Ruby, Statement, TypedTree {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        JContainer<Expression> assignments;

        public List<Expression> getAssignments() {
            return assignments.getElements();
        }

        public MultipleAssignment withExpressions(List<Expression> assignments) {
            return getPadding().withAssignments(JContainer.withElements(this.assignments, assignments));
        }

        JContainer<Expression> initializers;

        public List<Expression> getInitializers() {
            return initializers.getElements();
        }

        public MultipleAssignment withInitializers(List<Expression> initializers) {
            return getPadding().withInitializers(JContainer.withElements(this.initializers, initializers));
        }

        @Getter
        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitMultipleAssignment(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final MultipleAssignment t;

            public JContainer<Expression> getAssignments() {
                return t.assignments;
            }

            public Ruby.MultipleAssignment withAssignments(JContainer<Expression> assignments) {
                return t.assignments == assignments ? t : new Ruby.MultipleAssignment(t.id, t.prefix, t.markers, assignments, t.initializers, t.type);
            }

            public JContainer<Expression> getInitializers() {
                return t.initializers;
            }

            public Ruby.MultipleAssignment withInitializers(JContainer<Expression> initializers) {
                return t.initializers == initializers ? t : new Ruby.MultipleAssignment(t.id, t.prefix, t.markers, t.assignments, initializers, t.type);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class OpenEigenclass implements Ruby, Statement, TypedTree {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        JLeftPadded<Expression> eigenclass;

        public Expression getEigenclass() {
            return eigenclass.getElement();
        }

        public OpenEigenclass withEigenclass(Expression eigenclass) {
            return getPadding().withEigenclass(this.eigenclass.withElement(eigenclass));
        }

        @Getter
        @With
        J.Block body;

        @Override
        public @Nullable JavaType getType() {
            return eigenclass.getElement().getType();
        }

        @SuppressWarnings("unchecked")
        @Override
        public OpenEigenclass withType(@Nullable JavaType type) {
            return withEigenclass(eigenclass.getElement().withType(type));
        }

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitOpenEigenclass(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final OpenEigenclass t;

            public JLeftPadded<Expression> getEigenclass() {
                return t.eigenclass;
            }

            public OpenEigenclass withEigenclass(JLeftPadded<Expression> eigenclass) {
                return t.eigenclass == eigenclass ? t : new OpenEigenclass(t.id, t.prefix, t.markers, eigenclass, t.body);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class Rational implements Ruby, Expression, TypedTree {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        JRightPadded<Expression> numerator;

        public Expression getNumerator() {
            return numerator.getElement();
        }

        public Rational withNumerator(Expression numerator) {
            return getPadding().withNumerator(JRightPadded.withElement(this.numerator, numerator));
        }

        @Getter
        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitRational(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final Rational t;

            public JRightPadded<Expression> getNumerator() {
                return t.numerator;
            }

            public Rational withNumerator(JRightPadded<Expression> numerator) {
                return t.numerator == numerator ? t : new Rational(t.id, t.prefix, t.markers, numerator, t.type);
            }
        }
    }

    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class Redo implements Ruby, Statement {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitRedo(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        @Override
        public String toString() {
            return withPrefix(Space.EMPTY).printTrimmed(new JavaPrinter<>());
        }
    }

    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    final class Rescue implements Ruby, Statement {
        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        J.Try tryBlock;

        /**
         * A Java try block contains most of the functionality of a Ruby rescue block,
         * including the guarded statements after begin, rescue clauses (equivalent to
         * catch and multi-catch), and the ensure clause (equivalent to finally). The else
         * block in Ruby is the only thing that has no corollary in Java, and is represented
         * separately here.
         *
         * @return The "try" part of the rescue statement.
         */
        public Try getTry() {
            return tryBlock;
        }

        public Rescue withTry(Try tryBlock) {
            return this.tryBlock == tryBlock ? this : new Rescue(id, prefix, markers, tryBlock, elseClause);
        }

        @Nullable
        J.Block elseClause;

        @Nullable
        public J.Block getElse() {
            return elseClause;
        }

        public Rescue withElse(@Nullable J.Block elseClause) {
            return this.elseClause == elseClause ? this : new Rescue(id, prefix, markers, tryBlock, elseClause);
        }

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitRescue(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        @Override
        public String toString() {
            return withPrefix(Space.EMPTY).printTrimmed(new JavaPrinter<>());
        }
    }

    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class Retry implements Ruby, Statement {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitRetry(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }
    }

    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class Splat implements Ruby, Expression {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        Expression value;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitSplat(this, p);
        }

        @Override
        public @Nullable JavaType getType() {
            return value.getType();
        }

        @Override
        public <T extends J> T withType(@Nullable JavaType type) {
            return value.withType(type);
        }

        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    class SubArrayIndex implements Ruby, Expression {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        @Getter
        @With
        Expression startIndex;

        JLeftPadded<Expression> length;

        public Expression getLength() {
            return length.getElement();
        }

        public SubArrayIndex withLength(Expression length) {
            return getPadding().withLength(JLeftPadded.withElement(this.length, length));
        }


        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitSubArrayIndex(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        @Override
        public @Nullable JavaType getType() {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public SubArrayIndex withType(@Nullable JavaType type) {
            return this;
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final SubArrayIndex t;

            public JLeftPadded<Expression> getLength() {
                return t.length;
            }

            public Ruby.SubArrayIndex withLength(JLeftPadded<Expression> length) {
                return t.length == length ? t : new Ruby.SubArrayIndex(t.id, t.prefix, t.markers, t.startIndex, length);
            }
        }
    }

    /**
     * A Ruby class may extend from a `Struct.new(..)` expression.
     */
    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class ExpressionTypeTree implements Ruby, TypeTree {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;

        /**
         * Most commonly will be a call of some sort, frequently a {@link J.NewClass}, for example
         * in the case of <code>class Point < Struct.new(:x, :y)</code>.
         */
        Expression newType;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitExpressionTypeTree(this, p);
        }

        @Override
        public @Nullable JavaType getType() {
            return newType.getType();
        }

        @SuppressWarnings("unchecked")
        @Override
        public ExpressionTypeTree withType(@Nullable JavaType type) {
            return withNewType(newType.withType(type));
        }
    }

    @Value
    @With
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    class Unary implements Ruby, Expression, TypedTree {
        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        Ruby.Unary.Type operator;
        Expression expression;

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitUnary(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public enum Type {
            Defined,
        }

        @Override
        public JavaType getType() {
            return JavaType.Primitive.Boolean;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Ruby.Unary withType(@Nullable JavaType ignored) {
            return this;
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class Yield implements Ruby, Statement {
        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        JContainer<Statement> data;

        public List<Statement> getData() {
            return data.getElements();
        }

        public Ruby.Yield withData(List<Statement> data) {
            return getPadding().withData(JContainer.withElements(this.data, data));
        }

        @Override
        public <P> J acceptRuby(RubyVisitor<P> v, P p) {
            return v.visitYield(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        @Override
        public String toString() {
            return withPrefix(Space.EMPTY).printTrimmed(new JavaPrinter<>());
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final Ruby.Yield t;

            public JContainer<Statement> getData() {
                return t.data;
            }

            public Ruby.Yield withData(JContainer<Statement> data) {
                return t.data == data ? t : new Ruby.Yield(t.id, t.prefix, t.markers, data);
            }
        }
    }
}
