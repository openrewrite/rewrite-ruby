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
package org.openrewrite.ruby.internal;

import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.marker.ImplicitReturn;
import org.openrewrite.java.marker.OmitParentheses;
import org.openrewrite.java.marker.TrailingComma;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.ruby.RubyVisitor;
import org.openrewrite.ruby.marker.*;
import org.openrewrite.ruby.tree.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class RubyPrinter<P> extends RubyVisitor<PrintOutputCapture<P>> {
    private static final UnaryOperator<String> MARKER_WRAPPER =
            out -> "~~" + out + (out.isEmpty() ? "" : "~~") + ">";

    private final RubyJavaPrinter delegate = new RubyJavaPrinter();

    @Override
    public J visit(@Nullable Tree tree, PrintOutputCapture<P> p) {
        if (!(tree instanceof Ruby)) {
            // re-route printing to the java printer
            return delegate.visit(tree, p);
        } else {
            return super.visit(tree, p);
        }
    }

    public Ruby visitCompilationUnit(Ruby.CompilationUnit compilationUnit, PrintOutputCapture<P> p) {
        visitSpace(compilationUnit.getPrefix(), Space.Location.COMPILATION_UNIT_PREFIX, p);
        visitMarkers(compilationUnit.getMarkers(), p);
        for (JRightPadded<Statement> statement : compilationUnit.getPadding().getStatements()) {
            visit(statement.getElement(), p);
            visitSpace(statement.getAfter(), Space.Location.LANGUAGE_EXTENSION, p);
        }
        return compilationUnit;
    }

    @Override
    public J visitAlias(Ruby.Alias alias, PrintOutputCapture<P> p) {
        beforeSyntax(alias, RubySpace.Location.ALIAS_PREFIX, p);
        p.append("alias");
        visit(alias.getNewName(), p);
        visit(alias.getExistingName(), p);
        afterSyntax(alias, p);
        return alias;
    }

    @Override
    public J visitArray(Ruby.Array array, PrintOutputCapture<P> p) {
        beforeSyntax(array, RubySpace.Location.LIST_LITERAL, p);
        if (array.getPadding().getElements().getMarkers().findFirst(OmitParentheses.class).isPresent()) {
            visitContainer("", array.getPadding().getElements(), RubyContainer.Location.LIST_LITERAL_ELEMENTS,
                    ",", "", p);
        } else {
            visitContainer("[", array.getPadding().getElements(), RubyContainer.Location.LIST_LITERAL_ELEMENTS,
                    ",", "]", p);
        }
        afterSyntax(array, p);
        return array;
    }

    @Override
    public J visitAssignmentOperation(Ruby.AssignmentOperation assignOp, PrintOutputCapture<P> p) {
        String keyword = "";
        switch (assignOp.getOperator()) {
            case And:
                keyword = "&&=";
                break;
            case Or:
                keyword = "||=";
                break;
        }
        beforeSyntax(assignOp, Space.Location.ASSIGNMENT_OPERATION_PREFIX, p);
        visit(assignOp.getVariable(), p);
        visitSpace(assignOp.getPadding().getOperator().getBefore(), Space.Location.ASSIGNMENT_OPERATION_OPERATOR, p);
        p.append(keyword);
        visit(assignOp.getAssignment(), p);
        afterSyntax(assignOp, p);
        return assignOp;
    }

    @Override
    public J visitBegin(Ruby.Begin begin, PrintOutputCapture<P> p) {
        beforeSyntax(begin, RubySpace.Location.BEGIN_PREFIX, p);
        p.append("BEGIN");
        visitSpace(begin.getBlock().getPrefix(), Space.Location.BLOCK_PREFIX, p);
        visitMarkers(begin.getBlock().getMarkers(), p);
        p.append("{");
        visit(begin.getBlock().getStatements(), p);
        visitSpace(begin.getBlock().getEnd(), Space.Location.BLOCK_END, p);
        p.append("}");
        afterSyntax(begin, p);
        return begin;
    }

    @Override
    public J visitBinary(Ruby.Binary binary, PrintOutputCapture<P> p) {
        String keyword = "";
        switch (binary.getOperator()) {
            case Comparison:
                keyword = "<=>";
                break;
            case Exponentiation:
                keyword = "**";
                break;
            case FlipFlopInclusive:
            case RangeInclusive:
                keyword = "..";
                break;
            case FlipFlopExclusive:
            case RangeExclusive:
                keyword = "...";
                break;
            case Match:
                keyword = "=~";
                break;
            case Within:
                keyword = "===";
                break;
        }
        beforeSyntax(binary, Space.Location.BINARY_PREFIX, p);
        visit(binary.getLeft(), p);
        visitSpace(binary.getPadding().getOperator().getBefore(), Space.Location.BINARY_OPERATOR, p);
        p.append(keyword);
        visit(binary.getRight(), p);
        afterSyntax(binary, p);
        return binary;
    }

    @Override
    public J visitBlock(Ruby.Block block, PrintOutputCapture<P> p) {
        beforeSyntax(block, Space.Location.BLOCK_PREFIX, p);
        p.append(block.isInline() ? "{" : "do");
        visitContainer("|", block.getPadding().getParameters(), RubyContainer.Location.BLOCK_PARAMETERS, ",", "|", p);
        visit(block.getBody(), p);
        p.append(block.isInline() ? "}" : "end");
        afterSyntax(block, p);
        return block;
    }

    @Override
    public J visitBlockArgument(Ruby.BlockArgument blockArgument, PrintOutputCapture<P> p) {
        beforeSyntax(blockArgument, RubySpace.Location.BLOCK_ARGUMENT_PREFIX, p);
        p.append("&");
        visit(blockArgument.getArgument(), p);
        afterSyntax(blockArgument, p);
        return blockArgument;
    }

    @Override
    public J visitClassMethod(Ruby.ClassMethod method, PrintOutputCapture<P> p) {
        beforeSyntax(method, RubySpace.Location.CLASS_METHOD_PREFIX, p);
        p.append("def");
        visit(method.getReceiver(), p);
        visitSpace(method.getPadding().getMethod().getBefore(), RubySpace.Location.CLASS_METHOD_NAME_PREFIX, p);
        visitMarkers(method.getPadding().getMethod().getMarkers(), p);
        p.append(".");
        visit(method.getMethod().getName(), p);
        boolean omitParentheses = method.getMethod().getPadding().getParameters()
                .getMarkers().findFirst(OmitParentheses.class).isPresent();
        visitContainer(omitParentheses ? "" : "(", method.getMethod().getPadding().getParameters(),
                RubyContainer.Location.CLASS_METHOD_DECLARATION_PARAMETERS, ",",
                omitParentheses ? "" : ")", p);
        visit(method.getMethod().getBody(), p);
        p.append("end");
        afterSyntax(method, p);
        return method;
    }

    @Override
    public J visitDelimitedString(Ruby.DelimitedString dString, PrintOutputCapture<P> p) {
        beforeSyntax(dString, RubySpace.Location.DELIMITED_STRING_VALUE_PREFIX, p);
        p.append(dString.getDelimiter());
        visit(dString.getStrings(), p);
        p.append(dString.getDelimiter().charAt(dString.getDelimiter().length() - 1));
        for (Ruby.DelimitedString.RegexpOptions regexpOption : dString.getRegexpOptions()) {
            switch (regexpOption) {
                case IgnoreCase:
                    p.append('i');
                    break;
                case Java:
                    p.append('j');
                    break;
                case Multiline:
                    p.append('m');
                    break;
                case Extended:
                    p.append('x');
                    break;
                case Once:
                    p.append('o');
                    break;
                case None:
                    p.append('n');
                    break;
                case EUCJPEncoding:
                    p.append('e');
                    break;
                case SJISEncoding:
                    p.append('s');
                    break;
                case UTF8Encoding:
                    p.append('u');
                    break;
                default:
                    throw new IllegalStateException("Unexpected regexp option " + regexpOption);
            }
        }
        afterSyntax(dString, p);
        return dString;
    }

    @Override
    public J visitDelimitedStringValue(Ruby.DelimitedString.Value value, PrintOutputCapture<P> p) {
        beforeSyntax(value, RubySpace.Location.DELIMITED_STRING_VALUE_PREFIX, p);
        p.append("#{");
        visit(value.getTree(), p);
        visitSpace(value.getAfter(), RubySpace.Location.DELIMITED_STRING_VALUE_SUFFIX, p);
        p.append('}');
        afterSyntax(value, p);
        return value;
    }

    @Override
    public J visitEnd(Ruby.End end, PrintOutputCapture<P> p) {
        beforeSyntax(end, RubySpace.Location.END_PREFIX, p);
        p.append("END");
        visitSpace(end.getBlock().getPrefix(), Space.Location.BLOCK_PREFIX, p);
        visitMarkers(end.getBlock().getMarkers(), p);
        p.append("{");
        visit(end.getBlock().getStatements(), p);
        visitSpace(end.getBlock().getEnd(), Space.Location.BLOCK_END, p);
        p.append("}");
        afterSyntax(end, p);
        return end;
    }

    @Override
    public J visitExpansion(Ruby.Expansion expansion, PrintOutputCapture<P> p) {
        beforeSyntax(expansion, RubySpace.Location.EXPANSION_PREFIX, p);
        p.append("*");
        visit(expansion.getTree(), p);
        afterSyntax(expansion, p);
        return expansion;
    }

    @Override
    public J visitExpressionTypeTree(Ruby.ExpressionTypeTree expressionTypeTree, PrintOutputCapture<P> p) {
        beforeSyntax(expressionTypeTree, RubySpace.Location.EXPRESSION_TYPE_TREE_PREFIX, p);
        visit(expressionTypeTree.getNewType(), p);
        afterSyntax(expressionTypeTree, p);
        return expressionTypeTree;
    }

    @Override
    public J visitHash(Ruby.Hash hash, PrintOutputCapture<P> p) {
        beforeSyntax(hash, RubySpace.Location.HASH_PREFIX, p);
        visitContainer("{", hash.getPadding().getElements(), RubyContainer.Location.HASH_ELEMENTS, ",", "}", p);
        afterSyntax(hash, p);
        return hash;
    }

    @Override
    public J visitKeyValue(Ruby.KeyValue keyValue, PrintOutputCapture<P> p) {
        beforeSyntax(keyValue, RubySpace.Location.KEY_VALUE_PREFIX, p);
        visit(keyValue.getKey(), p);
        visitLeftPadded("=>", keyValue.getPadding().getValue(), RubyLeftPadded.Location.KEY_VALUE_VALUE_PREFIX, p);
        afterSyntax(keyValue, p);
        return keyValue;
    }

    @Override
    public J visitMultipleAssignment(Ruby.MultipleAssignment multipleAssignment, PrintOutputCapture<P> p) {
        beforeSyntax(multipleAssignment, RubySpace.Location.LIST_LITERAL, p);
        visitContainer("", multipleAssignment.getPadding().getAssignments(), RubyContainer.Location.MULTIPLE_ASSIGNMENT_ASSIGNMENTS,
                ",", "", p);
        visitContainer("=", multipleAssignment.getPadding().getInitializers(), RubyContainer.Location.MULTIPLE_ASSIGNMENT_INITIALIZERS,
                ",", "", p);
        afterSyntax(multipleAssignment, p);
        return multipleAssignment;
    }

    @Override
    public J visitOpenEigenclass(Ruby.OpenEigenclass openEigenclass, PrintOutputCapture<P> p) {
        beforeSyntax(openEigenclass, RubySpace.Location.OPEN_EIGENCLASS_PREFIX, p);
        p.append("class");
        visitLeftPadded("<<", openEigenclass.getPadding().getEigenclass(), RubyLeftPadded.Location.OPEN_EIGENCLASS_IDENTIFIER, p);
        visit(openEigenclass.getBody(), p);
        p.append("end");
        afterSyntax(openEigenclass, p);
        return openEigenclass;
    }

    @Override
    public J visitRational(Ruby.Rational rational, PrintOutputCapture<P> p) {
        beforeSyntax(rational, RubySpace.Location.RATIONAL_PREFIX, p);
        visitRightPadded(rational.getPadding().getNumerator(),
                RubyRightPadded.Location.RATIONAL_NUMERATOR_SUFFIX, "r", p);
        afterSyntax(rational, p);
        return rational;
    }

    @Override
    public J visitRedo(Ruby.Redo redo, PrintOutputCapture<P> p) {
        beforeSyntax(redo, RubySpace.Location.REDO_PREFIX, p);
        p.append("redo");
        afterSyntax(redo, p);
        return redo;
    }

    @Override
    public J visitRescue(Ruby.Rescue rescue, PrintOutputCapture<P> p) {
        beforeSyntax(rescue, RubySpace.Location.RESCUE_PREFIX, p);
        p.append("begin");
        J.Try aTry = rescue.getTry();
        visitSpace(aTry.getPrefix(), Space.Location.TRY_PREFIX, p);
        visitMarkers(aTry.getMarkers(), p);
        visit(aTry.getBody(), p);

        for (J.Try.Catch aCatch : aTry.getCatches()) {
            visitSpace(aCatch.getPrefix(), Space.Location.CATCH_PREFIX, p);
            visitMarkers(aCatch.getMarkers(), p);
            p.append("rescue");

            J.VariableDeclarations param = aCatch.getParameter().getTree();
            visitSpace(param.getPrefix(), Space.Location.CONTROL_PARENTHESES_PREFIX, p);
            visitMarkers(param.getMarkers(), p);

            TypeTree types = param.getTypeExpression();
            if (types instanceof J.MultiCatch) {
                J.MultiCatch multiCatch = (J.MultiCatch) types;
                visitRightPadded(multiCatch.getPadding().getAlternatives(), RubyRightPadded.Location.RESCUE_TYPE_SUFFIX, ",", p);
            } else {
                visit(types, p);
            }

            if (!param.getVariables().isEmpty()) {
                List<J.VariableDeclarations.NamedVariable> variables = param.getVariables();
                for (int i = 0; i < variables.size(); i++) {
                    if (i > 1) {
                        throw new IllegalArgumentException("Expected at most one variable name in rescue");
                    }
                    J.VariableDeclarations.NamedVariable exceptionName = variables.get(i);
                    visitSpace(exceptionName.getPrefix(), Space.Location.LANGUAGE_EXTENSION, p);
                    visitMarkers(exceptionName.getMarkers(), p);
                    p.append("=>");
                    visit(exceptionName.getName(), p);
                }
            }

            visit(aCatch.getBody(), p);
        }

        if (rescue.getElse() != null) {
            visitSpace(rescue.getElse().getPrefix(), Space.Location.BLOCK_PREFIX, p);
            p.append("else");
            visit(rescue.getElse().withPrefix(Space.EMPTY), p);
        }

        if (aTry.getFinally() != null) {
            visitSpace(aTry.getFinally().getPrefix(), Space.Location.BLOCK_PREFIX, p);
            p.append("ensure");
            visit(aTry.getFinally().withPrefix(Space.EMPTY), p);
        }

        p.append("end");
        afterSyntax(rescue, p);
        return rescue;
    }

    @Override
    public J visitRetry(Ruby.Retry retry, PrintOutputCapture<P> p) {
        beforeSyntax(retry, RubySpace.Location.RETRY_PREFIX, p);
        p.append("retry");
        afterSyntax(retry, p);
        return retry;
    }

    @Override
    public J visitSplat(Ruby.Splat splat, PrintOutputCapture<P> p) {
        beforeSyntax(splat, RubySpace.Location.SPLAT_PREFIX, p);
        p.append("*");
        visit(splat.getValue(), p);
        afterSyntax(splat, p);
        return splat;
    }

    @Override
    public J visitSubArrayIndex(Ruby.SubArrayIndex subArrayIndex, PrintOutputCapture<P> p) {
        beforeSyntax(subArrayIndex, RubySpace.Location.SUB_ARRAY_INDEX_PREFIX, p);
        visit(subArrayIndex.getStartIndex(), p);
        visitLeftPadded(",", subArrayIndex.getPadding().getLength(), RubyLeftPadded.Location.SUB_ARRAY_LENGTH_PREFIX, p);
        afterSyntax(subArrayIndex, p);
        return subArrayIndex;
    }

    @Override
    public J visitUnary(Ruby.Unary unary, PrintOutputCapture<P> p) {
        beforeSyntax(unary, Space.Location.UNARY_PREFIX, p);
        p.append("defined?");
        visit(unary.getExpression(), p);
        afterSyntax(unary, p);
        return unary;
    }

    @Override
    public J visitYield(Ruby.Yield yield, PrintOutputCapture<P> p) {
        beforeSyntax(yield, RubySpace.Location.YIELD, p);
        p.append("yield");
        JContainer<Statement> args = yield.getPadding().getData();
        if (args.getMarkers().findFirst(OmitParentheses.class).isPresent()) {
            visitContainer("", args, RubyContainer.Location.YIELD_DATA, ",", "", p);
        } else {
            visitContainer("(", args, RubyContainer.Location.YIELD_DATA, ",", ")", p);
        }
        afterSyntax(yield, p);
        return yield;
    }

    protected void beforeSyntax(J j, RubySpace.Location loc, PrintOutputCapture<P> p) {
        beforeSyntax(j.getPrefix(), j.getMarkers(), loc, p);
    }

    protected void beforeSyntax(J j, Space.Location loc, PrintOutputCapture<P> p) {
        beforeSyntax(j.getPrefix(), j.getMarkers(), loc, p);
    }

    protected void beforeSyntax(Space prefix, Markers markers, RubySpace.Location location, PrintOutputCapture<P> p) {
        for (Marker marker : markers.getMarkers()) {
            p.append(p.getMarkerPrinter().beforePrefix(marker, new Cursor(getCursor(), marker), MARKER_WRAPPER));
        }
        visitSpace(prefix, location, p);
        visitMarkers(markers, p);
        for (Marker marker : markers.getMarkers()) {
            p.append(p.getMarkerPrinter().beforeSyntax(marker, new Cursor(getCursor(), marker), MARKER_WRAPPER));
        }
    }

    protected void beforeSyntax(Space prefix, Markers markers, Space.Location location, PrintOutputCapture<P> p) {
        for (Marker marker : markers.getMarkers()) {
            p.append(p.getMarkerPrinter().beforePrefix(marker, new Cursor(getCursor(), marker), MARKER_WRAPPER));
        }
        visitSpace(prefix, location, p);
        visitMarkers(markers, p);
        for (Marker marker : markers.getMarkers()) {
            p.append(p.getMarkerPrinter().beforeSyntax(marker, new Cursor(getCursor(), marker), MARKER_WRAPPER));
        }
    }

    protected void afterSyntax(J t, PrintOutputCapture<P> p) {
        afterSyntax(t.getMarkers(), p);
    }

    protected void afterSyntax(Markers markers, PrintOutputCapture<P> p) {
        for (Marker marker : markers.getMarkers()) {
            p.append(p.getMarkerPrinter().afterSyntax(marker, new Cursor(getCursor(), marker), MARKER_WRAPPER));
        }
    }

    public Space visitSpace(Space space, RubySpace.Location loc, PrintOutputCapture<P> p) {
        return delegate.visitSpace(space, Space.Location.LANGUAGE_EXTENSION, p);
    }

    @Override
    public Space visitSpace(Space space, Space.Location loc, PrintOutputCapture<P> p) {
        return delegate.visitSpace(space, loc, p);
    }

    @SuppressWarnings("SameParameterValue")
    protected void visitContainer(String before, @Nullable JContainer<? extends J> container, RubyContainer.Location location,
                                  String suffixBetween, @Nullable String after, PrintOutputCapture<P> p) {
        if (container == null) {
            return;
        }
        visitSpace(container.getBefore(), location.getBeforeLocation(), p);
        p.append(before);
        visitRightPadded(container.getPadding().getElements(), location.getElementLocation(), suffixBetween, p);
        p.append(after == null ? "" : after);
    }

    public void visitLeftPadded(@Nullable String prefix, @Nullable JLeftPadded<? extends J> leftPadded,
                                RubyLeftPadded.Location location, PrintOutputCapture<P> p) {
        if (leftPadded != null) {
            beforeSyntax(leftPadded.getBefore(), leftPadded.getMarkers(), location.getBeforeLocation(), p);
            if (prefix != null) {
                p.append(prefix);
            }
            visit(leftPadded.getElement(), p);
            afterSyntax(leftPadded.getMarkers(), p);
        }
    }

    protected void visitRightPadded(@Nullable JRightPadded<? extends J> rightPadded, RubyRightPadded.Location location,
                                    @Nullable String suffix, PrintOutputCapture<P> p) {
        if (rightPadded != null) {
            beforeSyntax(Space.EMPTY, rightPadded.getMarkers(), (RubySpace.Location) null, p);
            visit(rightPadded.getElement(), p);
            afterSyntax(rightPadded.getMarkers(), p);
            visitSpace(rightPadded.getAfter(), location.getAfterLocation(), p);
            if (suffix != null) {
                p.append(suffix);
            }
        }
    }

    protected void visitRightPadded(List<? extends JRightPadded<? extends J>> nodes, RubyRightPadded.Location location, String suffixBetween, PrintOutputCapture<P> p) {
        for (int i = 0; i < nodes.size(); i++) {
            JRightPadded<? extends J> node = nodes.get(i);
            visit(node.getElement(), p);
            visitSpace(node.getAfter(), location.getAfterLocation(), p);
            if (i < nodes.size() - 1) {
                p.append(suffixBetween);
            } else {
                for (Marker m : node.getMarkers().getMarkers()) {
                    if (m instanceof TrailingComma) {
                        p.append(suffixBetween);
                        visitSpace(((TrailingComma) m).getSuffix(), Space.Location.LANGUAGE_EXTENSION, p);
                        break;
                    }
                }
            }
        }
    }

    private class RubyJavaPrinter extends JavaPrinter<P> {
        @Override
        public J visit(@Nullable Tree tree, PrintOutputCapture<P> p) {
            if (tree instanceof Ruby) {
                // re-route printing back up to groovy
                return RubyPrinter.this.visit(tree, p);
            } else {
                return super.visit(tree, p);
            }
        }

        @Override
        protected void visitStatement(@Nullable JRightPadded<Statement> paddedStat, JRightPadded.Location location, PrintOutputCapture<P> p) {
            if (paddedStat != null) {
                visit(paddedStat.getElement(), p);
                visitSpace(paddedStat.getAfter(), location.getAfterLocation(), p);
                visitMarkers(paddedStat.getMarkers(), p);
            }
        }

        @Override
        public <M extends Marker> M visitMarker(Marker marker, PrintOutputCapture<P> p) {
            if (marker instanceof org.openrewrite.java.marker.Semicolon) {
                p.append(';');
            }
            return super.visitMarker(marker, p);
        }

        @Override
        public J visitAssignmentOperation(J.AssignmentOperation assignOp, PrintOutputCapture<P> p) {
            String keyword = "";
            switch (assignOp.getOperator()) {
                case Addition:
                    keyword = "+=";
                    break;
                case Subtraction:
                    keyword = "-=";
                    break;
                case Multiplication:
                    keyword = "*=";
                    break;
                case Division:
                    keyword = "/=";
                    break;
                case Modulo:
                    keyword = "%=";
                    break;
                case Exponentiation:
                    keyword = "**=";
                    break;
            }
            beforeSyntax(assignOp, Space.Location.ASSIGNMENT_OPERATION_PREFIX, p);
            visit(assignOp.getVariable(), p);
            visitSpace(assignOp.getPadding().getOperator().getBefore(), Space.Location.ASSIGNMENT_OPERATION_OPERATOR, p);
            p.append(keyword);
            visit(assignOp.getAssignment(), p);
            afterSyntax(assignOp, p);
            return assignOp;
        }

        @Override
        public J visitBinary(J.Binary binary, PrintOutputCapture<P> p) {
            String keyword = "";
            switch (binary.getOperator()) {
                case Addition:
                    keyword = "+";
                    break;
                case Subtraction:
                    keyword = "-";
                    break;
                case Multiplication:
                    keyword = "*";
                    break;
                case Division:
                    keyword = "/";
                    break;
                case Modulo:
                    keyword = "%";
                    break;
                case LessThan:
                    keyword = "<";
                    break;
                case GreaterThan:
                    keyword = ">";
                    break;
                case LessThanOrEqual:
                    keyword = "<=";
                    break;
                case GreaterThanOrEqual:
                    keyword = ">=";
                    break;
                case Equal:
                    keyword = "==";
                    break;
                case NotEqual:
                    keyword = "!=";
                    break;
                case BitAnd:
                    keyword = "&";
                    break;
                case BitOr:
                    keyword = "|";
                    break;
                case BitXor:
                    keyword = "^";
                    break;
                case LeftShift:
                    keyword = "<<";
                    break;
                case RightShift:
                    keyword = ">>";
                    break;
                case UnsignedRightShift:
                    keyword = ">>>";
                    break;
                case Or:
                    keyword = "||";
                    if (binary.getMarkers().findFirst(EnglishOperator.class).isPresent()) {
                        keyword = "or";
                    }
                    break;
                case And:
                    keyword = "&&";
                    if (binary.getMarkers().findFirst(EnglishOperator.class).isPresent()) {
                        keyword = "and";
                    }
                    break;
            }
            beforeSyntax(binary, Space.Location.BINARY_PREFIX, p);
            visit(binary.getLeft(), p);
            visitSpace(binary.getPadding().getOperator().getBefore(), Space.Location.BINARY_OPERATOR, p);
            p.append(keyword);
            visit(binary.getRight(), p);
            afterSyntax(binary, p);
            return binary;
        }

        @Override
        public J visitBlock(J.Block block, PrintOutputCapture<P> p) {
            beforeSyntax(block, Space.Location.BLOCK_PREFIX, p);
            visitStatements(block.getPadding().getStatements(), JRightPadded.Location.BLOCK_STATEMENT, p);
            visitSpace(block.getEnd(), Space.Location.BLOCK_END, p);
            afterSyntax(block, p);
            return block;
        }

        @Override
        public J visitCase(J.Case aCase, PrintOutputCapture<P> p) {
            beforeSyntax(aCase, Space.Location.CASE_PREFIX, p);
            if (aCase.getExpressions().isEmpty()) {
                p.append("else");
            } else {
                p.append("when");
                visitContainer("", aCase.getPadding().getExpressions(), JContainer.Location.CASE_EXPRESSION,
                        ",", "", p);
            }
            visitContainer("", aCase.getPadding().getStatements(), JContainer.Location.CASE,
                    "", "", p);
            afterSyntax(aCase, p);
            return aCase;
        }

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, PrintOutputCapture<P> p) {
            beforeSyntax(classDecl, Space.Location.CLASS_DECLARATION_PREFIX, p);
            p.append("class");
            visit(classDecl.getName(), p);
            visitLeftPadded("<", classDecl.getPadding().getExtends(), JLeftPadded.Location.EXTENDS, p);
            visit(classDecl.getBody(), p);
            p.append("end");
            afterSyntax(classDecl, p);
            return classDecl;
        }

        @Override
        public J visitContinue(J.Continue continueStatement, PrintOutputCapture<P> p) {
            beforeSyntax(continueStatement, Space.Location.CONTINUE_PREFIX, p);
            p.append("next");
            visit(continueStatement.getLabel(), p);
            afterSyntax(continueStatement, p);
            return continueStatement;
        }

        @Override
        public <T extends J> J visitControlParentheses(J.ControlParentheses<T> controlParens, PrintOutputCapture<P> p) {
            visitSpace(controlParens.getPrefix(), Space.Location.CONTROL_PARENTHESES_PREFIX, p);
            visitRightPadded(controlParens.getPadding().getTree(), JRightPadded.Location.PARENTHESES, p);
            if (controlParens.getMarkers().findFirst(ExplicitThen.class).isPresent()) {
                p.append("then");
            } else if (controlParens.getMarkers().findFirst(ExplicitDo.class).isPresent()) {
                p.append("do");
            }
            afterSyntax(controlParens, p);
            return controlParens;
        }

        @Override
        public J visitElse(J.If.Else anElse, PrintOutputCapture<P> p) {
            beforeSyntax(anElse, Space.Location.ELSE_PREFIX, p);
            if (anElse.getBody() instanceof J.If) {
                p.append("els"); // the nested `J.If` will print the remaining `if`
            } else {
                p.append("else");
            }
            visitStatement(anElse.getPadding().getBody(), JRightPadded.Location.IF_ELSE, p);
            if (!(anElse.getBody() instanceof J.If)) {
                p.append("end");
            }
            afterSyntax(anElse, p);
            return anElse;
        }

        @Override
        public J visitForEachLoop(J.ForEachLoop forEachLoop, PrintOutputCapture<P> p) {
            beforeSyntax(forEachLoop, Space.Location.FOR_EACH_LOOP_PREFIX, p);
            p.append("for");
            J.ForEachLoop.Control ctrl = forEachLoop.getControl();
            visitSpace(ctrl.getPrefix(), Space.Location.FOR_EACH_CONTROL_PREFIX, p);
            visitRightPadded(ctrl.getPadding().getVariable(), JRightPadded.Location.FOREACH_VARIABLE, "in", p);
            visitRightPadded(ctrl.getPadding().getIterable(), JRightPadded.Location.FOREACH_ITERABLE, "", p);
            visitStatement(forEachLoop.getPadding().getBody(), JRightPadded.Location.FOR_BODY, p);
            p.append("end");
            afterSyntax(forEachLoop, p);
            return forEachLoop;
        }

        @Override
        public J visitIf(J.If iff, PrintOutputCapture<P> p) {
            beforeSyntax(iff, Space.Location.IF_PREFIX, p);

            if (iff.getMarkers().findFirst(IfModifier.class).isPresent()) {
                visitStatement(iff.getPadding().getThenPart(), JRightPadded.Location.IF_THEN, p);
                p.append("if");
                visit(iff.getIfCondition(), p);
                return iff;
            } else {
                p.append("if");
                visit(iff.getIfCondition(), p);
                visitStatement(iff.getPadding().getThenPart(), JRightPadded.Location.IF_THEN, p);
                if (iff.getElsePart() == null) {
                    p.append("end");
                } else {
                    visit(iff.getElsePart(), p);
                }
            }

            afterSyntax(iff, p);
            return iff;
        }

        @Override
        public J visitLambda(J.Lambda lambda, PrintOutputCapture<P> p) {
            beforeSyntax(lambda, Space.Location.LAMBDA_PREFIX, p);
            p.append("->");
            if (!lambda.getParameters().getParameters().isEmpty()) {
                beforeSyntax(lambda.getParameters(), Space.Location.LAMBDA_PARAMETERS_PREFIX, p);
                p.append("(");
                visitRightPadded(lambda.getParameters().getPadding().getParams(),
                        JRightPadded.Location.LAMBDA_PARAM, ",", p);
                p.append(")");
                afterSyntax(lambda.getParameters(), p);
            }
            J.Block body = (J.Block) lambda.getBody();
            visitSpace(body.getPrefix(), Space.Location.BLOCK_PREFIX, p);
            p.append("{");
            visitRightPadded(body.getPadding().getStatements(), JRightPadded.Location.BLOCK_STATEMENT, "", p);
            visitSpace(body.getEnd(), Space.Location.BLOCK_END, p);
            p.append("}");
            afterSyntax(lambda, p);
            return lambda;
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, PrintOutputCapture<P> p) {
            beforeSyntax(method, Space.Location.METHOD_DECLARATION_PREFIX, p);
            p.append("def");
            visit(method.getAnnotations().getName().getAnnotations(), p);
            visit(method.getName(), p);
            boolean omitParentheses = method.getPadding().getParameters()
                    .getMarkers().findFirst(OmitParentheses.class).isPresent();
            visitContainer(omitParentheses ? "" : "(", method.getPadding().getParameters(),
                    JContainer.Location.METHOD_DECLARATION_PARAMETERS, ",",
                    omitParentheses ? "" : ")", p);
            visit(method.getBody(), p);
            p.append("end");
            afterSyntax(method, p);
            return method;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, PrintOutputCapture<P> p) {
            beforeSyntax(method, Space.Location.METHOD_INVOCATION_PREFIX, p);
            visitRightPadded(method.getPadding().getSelect(), JRightPadded.Location.METHOD_SELECT, ".", p);
            visit(method.getName(), p);

            JContainer<Expression> args = method.getPadding().getArguments();
            AtomicReference<Ruby.Block> blockArg = new AtomicReference<>();
            args = JContainer.withElements(args, ListUtils.mapLast(args.getElements(), arg -> {
                if (arg instanceof Ruby.Block) {
                    blockArg.set((Ruby.Block) arg);
                    return null;
                }
                return arg;
            }));
            if (args.getMarkers().findFirst(OmitParentheses.class).isPresent()) {
                visitContainer("", args, JContainer.Location.METHOD_INVOCATION_ARGUMENTS, ",", "", p);
            } else {
                visitContainer("(", args, JContainer.Location.METHOD_INVOCATION_ARGUMENTS, ",", ")", p);
            }
            if (blockArg.get() != null) {
                visit(blockArg.get(), p);
            }

            afterSyntax(method, p);
            return method;
        }

        @Override
        public J visitSwitch(J.Switch aSwitch, PrintOutputCapture<P> p) {
            beforeSyntax(aSwitch, Space.Location.SWITCH_PREFIX, p);
            p.append("case");
            visit(aSwitch.getSelector(), p);
            visitRightPadded(aSwitch.getCases().getPadding().getStatements(),
                    JRightPadded.Location.BLOCK_STATEMENT, "", p);
            visitSpace(aSwitch.getCases().getEnd(), Space.Location.BLOCK_END, p);
            p.append("end");
            afterSyntax(aSwitch, p);
            return aSwitch;
        }

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations multiVariable, PrintOutputCapture<P> p) {
            beforeSyntax(multiVariable, Space.Location.VARIABLE_DECLARATIONS_PREFIX, p);
            for (J.Modifier m : multiVariable.getModifiers()) {
                visitModifier(m, p);
            }
            visit(multiVariable.getTypeExpression(), p);
            if (multiVariable.getVarargs() != null) {
                visitSpace(multiVariable.getVarargs(), Space.Location.VARARGS, p);
                p.append("*");
            }
            visitRightPadded(multiVariable.getPadding().getVariables(), JRightPadded.Location.NAMED_VARIABLE, ",", p);
            afterSyntax(multiVariable, p);
            return multiVariable;
        }

        @Override
        public J visitNewClass(J.NewClass newClass, PrintOutputCapture<P> p) {
            beforeSyntax(newClass, Space.Location.NEW_CLASS_PREFIX, p);
            visit(newClass.getClazz(), p);
            visitSpace(requireNonNull(newClass.getPadding().getEnclosing()).getAfter(), Space.Location.NEW_CLASS_ENCLOSING_SUFFIX, p);
            p.append('.');
            visitSpace(newClass.getNew(), Space.Location.NEW_PREFIX, p);
            p.append("new");
            if (!newClass.getPadding().getArguments().getMarkers().findFirst(OmitParentheses.class).isPresent()) {
                visitContainer("(", newClass.getPadding().getArguments(), JContainer.Location.NEW_CLASS_ARGUMENTS, ",", ")", p);
            }
            afterSyntax(newClass, p);
            return newClass;
        }

        @Override
        public J visitReturn(J.Return aReturn, PrintOutputCapture<P> p) {
            beforeSyntax(aReturn, Space.Location.RETURN_PREFIX, p);
            if (!aReturn.getMarkers().findFirst(ImplicitReturn.class).isPresent()) {
                p.append("return");
            }
            visit(aReturn.getExpression(), p);
            afterSyntax(aReturn, p);
            return aReturn;
        }

        @Override
        public J visitTry(J.Try tryable, PrintOutputCapture<P> p) {
            throw new UnsupportedOperationException("A try statement is not supported in Ruby without being " +
                                                    "wrapped in a Rescue object.");
        }

        @Override
        public J visitUnary(J.Unary unary, PrintOutputCapture<P> p) {
            beforeSyntax(unary, Space.Location.UNARY_PREFIX, p);
            switch (unary.getOperator()) {
                case PreIncrement:
                    p.append("++");
                    visit(unary.getExpression(), p);
                    break;
                case PreDecrement:
                    p.append("--");
                    visit(unary.getExpression(), p);
                    break;
                case PostIncrement:
                    visit(unary.getExpression(), p);
                    visitSpace(unary.getPadding().getOperator().getBefore(), Space.Location.UNARY_OPERATOR, p);
                    p.append("++");
                    break;
                case PostDecrement:
                    visit(unary.getExpression(), p);
                    visitSpace(unary.getPadding().getOperator().getBefore(), Space.Location.UNARY_OPERATOR, p);
                    p.append("--");
                    break;
                case Positive:
                    p.append('+');
                    visit(unary.getExpression(), p);
                    break;
                case Negative:
                    p.append('-');
                    visit(unary.getExpression(), p);
                    break;
                case Complement:
                    p.append('~');
                    visit(unary.getExpression(), p);
                    break;
                case Not:
                default:
                    if (unary.getMarkers().findFirst(EnglishOperator.class).isPresent()) {
                        p.append("not");
                    } else {
                        p.append('!');
                    }
                    visit(unary.getExpression(), p);
            }
            afterSyntax(unary, p);
            return unary;
        }

        @Override
        public J visitWhileLoop(J.WhileLoop whileLoop, PrintOutputCapture<P> p) {
            beforeSyntax(whileLoop, Space.Location.WHILE_PREFIX, p);

            boolean until = whileLoop.getMarkers().findFirst(Until.class).isPresent();
            if (whileLoop.getMarkers().findFirst(WhileModifier.class).isPresent()) {
                visitStatement(whileLoop.getPadding().getBody(), JRightPadded.Location.WHILE_BODY, p);
                p.append(until ? "until" : "while");
                visit(whileLoop.getCondition(), p);
            } else {
                p.append(until ? "until" : "while");
                visit(whileLoop.getCondition(), p);
                visitStatement(whileLoop.getPadding().getBody(), JRightPadded.Location.WHILE_BODY, p);
                p.append("end");
            }

            afterSyntax(whileLoop, p);
            return whileLoop;
        }
    }
}
