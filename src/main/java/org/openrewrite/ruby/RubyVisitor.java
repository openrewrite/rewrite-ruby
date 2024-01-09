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

import org.openrewrite.SourceFile;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.ruby.tree.Ruby;
import org.openrewrite.ruby.tree.RubyContainer;
import org.openrewrite.ruby.tree.RubyRightPadded;
import org.openrewrite.ruby.tree.RubySpace;

@SuppressWarnings("unused")
public class RubyVisitor<P> extends JavaVisitor<P> {

    @Override
    public boolean isAcceptable(SourceFile sourceFile, P p) {
        return sourceFile instanceof Ruby.CompilationUnit;
    }

    @Override
    public String getLanguage() {
        return "ruby";
    }

    public Space visitSpace(Space space, RubySpace.Location loc, P p) {
        return visitSpace(space, Space.Location.LANGUAGE_EXTENSION, p);
    }

    public <J2 extends J> JContainer<J2> visitContainer(@Nullable JContainer<J2> container,
                                                        RubyContainer.Location loc, P p) {
        return super.visitContainer(container, JContainer.Location.LANGUAGE_EXTENSION, p);
    }

    public <T> JRightPadded<T> visitRightPadded(@Nullable JRightPadded<T> right, RubyRightPadded.Location loc, P p) {
        return super.visitRightPadded(right, JRightPadded.Location.LANGUAGE_EXTENSION, p);
    }

    public Ruby visitCompilationUnit(Ruby.CompilationUnit compilationUnit, P p) {
        Ruby.CompilationUnit c = compilationUnit;
        c = c.withPrefix(visitSpace(c.getPrefix(), Space.Location.COMPILATION_UNIT_PREFIX, p));
        c = c.withMarkers(visitMarkers(c.getMarkers(), p));
        c = c.getPadding().withStatements(ListUtils.map(c.getPadding().getStatements(), statement ->
                visitRightPadded(statement, RubyRightPadded.Location.COMPILATION_UNIT_STATEMENT_SUFFIX, p)));
        return c;
    }

    public J visitAlias(Ruby.Alias alias, P p) {
        Ruby.Alias a = alias;
        a = a.withPrefix(visitSpace(a.getPrefix(), RubySpace.Location.ALIAS_PREFIX, p));
        a = a.withMarkers(visitMarkers(a.getMarkers(), p));
        Statement temp = (Statement) visitStatement(a, p);
        if (!(temp instanceof Ruby.Alias)) {
            return temp;
        } else {
            a = (Ruby.Alias) temp;
        }
        a = a.withNewName((J.Identifier) visit(a.getNewName(), p));
        a = a.withExistingName((J.Identifier) visit(a.getExistingName(), p));
        return a;
    }

    public J visitArray(Ruby.Array array, P p) {
        Ruby.Array l = array;
        l = l.withPrefix(visitSpace(l.getPrefix(), RubySpace.Location.ARRAY_PREFIX, p));
        l = l.withMarkers(visitMarkers(l.getMarkers(), p));
        Expression temp = (Expression) visitExpression(l, p);
        if (!(temp instanceof Ruby.Array)) {
            return temp;
        } else {
            l = (Ruby.Array) temp;
        }
        l = l.getPadding().withElements(visitContainer(l.getPadding().getElements(), RubyContainer.Location.ARRAY_ELEMENTS, p));
        l = l.withType(visitType(l.getType(), p));
        return l;
    }

    public J visitAssignmentOperation(Ruby.AssignmentOperation assignOp, P p) {
        Ruby.AssignmentOperation a = assignOp;
        a = a.withPrefix(visitSpace(a.getPrefix(), Space.Location.ASSIGNMENT_OPERATION_PREFIX, p));
        a = a.withMarkers(visitMarkers(a.getMarkers(), p));
        Statement temp = (Statement) visitStatement(a, p);
        if (!(temp instanceof Ruby.AssignmentOperation)) {
            return temp;
        } else {
            a = (Ruby.AssignmentOperation) temp;
        }
        Expression temp2 = (Expression) visitExpression(a, p);
        if (!(temp2 instanceof Ruby.AssignmentOperation)) {
            return temp2;
        } else {
            a = (Ruby.AssignmentOperation) temp2;
        }
        a = a.withVariable(visitAndCast(a.getVariable(), p));
        a = a.getPadding().withOperator(visitLeftPadded(a.getPadding().getOperator(), JLeftPadded.Location.ASSIGNMENT_OPERATION_OPERATOR, p));
        a = a.withAssignment(visitAndCast(a.getAssignment(), p));
        a = a.withType(visitType(a.getType(), p));
        return a;
    }

    public J visitPreExecution(Ruby.PreExecution begin, P p) {
        Ruby.PreExecution b = begin;
        b = b.withPrefix(visitSpace(b.getPrefix(), RubySpace.Location.PRE_EXECUTION_PREFIX, p));
        b = b.withMarkers(visitMarkers(b.getMarkers(), p));
        b = b.withBlock((J.Block) visit(b.getBlock(), p));
        return b;
    }

    public J visitBegin(Ruby.Begin begin, P p) {
        Ruby.Begin b = begin;
        b = b.withPrefix(visitSpace(b.getPrefix(), RubySpace.Location.BEGIN_PREFIX, p));
        b = b.withMarkers(visitMarkers(b.getMarkers(), p));
        Expression temp = (Expression) visitExpression(b, p);
        if (!(temp instanceof Ruby.Begin)) {
            return temp;
        } else {
            b = (Ruby.Begin) temp;
        }
        b = b.withBody((J.Block) visit(b.getBody(), p));
        b = b.withType(visitType(b.getType(), p));
        return b;
    }

    public J visitBinary(Ruby.Binary binary, P p) {
        Ruby.Binary b = binary;
        b = b.withPrefix(visitSpace(b.getPrefix(), Space.Location.BINARY_PREFIX, p));
        b = b.withMarkers(visitMarkers(b.getMarkers(), p));
        Expression temp = (Expression) visitExpression(b, p);
        if (!(temp instanceof Ruby.Binary)) {
            return temp;
        } else {
            b = (Ruby.Binary) temp;
        }
        b = b.withLeft(visitAndCast(b.getLeft(), p));
        b = b.getPadding().withOperator(visitLeftPadded(b.getPadding().getOperator(), JLeftPadded.Location.LANGUAGE_EXTENSION, p));
        b = b.withRight(visitAndCast(b.getRight(), p));
        b = b.withType(visitType(b.getType(), p));
        return b;
    }

    public J visitBlock(Ruby.Block block, P p) {
        Ruby.Block b = block;
        b = b.withPrefix(visitSpace(b.getPrefix(), Space.Location.BLOCK_PREFIX, p));
        b = b.withMarkers(visitMarkers(b.getMarkers(), p));
        Expression temp = (Expression) visitExpression(b, p);
        if (!(temp instanceof Ruby.Block)) {
            return temp;
        } else {
            b = (Ruby.Block) temp;
        }
        b = b.getPadding().withParameters(visitContainer(b.getPadding().getParameters(),
                RubyContainer.Location.BLOCK_PARAMETERS, p));
        b = b.withBody((J.Block) visit(b.getBody(), p));
        return b;
    }

    public J visitBlockArgument(Ruby.BlockArgument blockArgument, P p) {
        Ruby.BlockArgument b = blockArgument;
        b = b.withPrefix(visitSpace(b.getPrefix(), RubySpace.Location.BLOCK_ARGUMENT_PREFIX, p));
        b = b.withMarkers(visitMarkers(b.getMarkers(), p));
        Expression temp = (Expression) visitExpression(b, p);
        if (!(temp instanceof Ruby.BlockArgument)) {
            return temp;
        } else {
            b = (Ruby.BlockArgument) temp;
        }
        b = b.withArgument((Expression) visit(b.getArgument(), p));
        b = b.withType(visitType(b.getType(), p));
        return b;
    }

    public J visitBooleanCheck(Ruby.BooleanCheck booleanCheck, P p) {
        Ruby.BooleanCheck b = booleanCheck;
        b = b.withPrefix(visitSpace(b.getPrefix(), RubySpace.Location.BOOLEAN_CHECK_PREFIX, p));
        b = b.withMarkers(visitMarkers(b.getMarkers(), p));
        Expression temp = (Expression) visitExpression(b, p);
        if (!(temp instanceof Ruby.BooleanCheck)) {
            return temp;
        } else {
            b = (Ruby.BooleanCheck) temp;
        }
        b = b.withLeft((Expression) visit(b.getLeft(), p));
        b = b.withPattern((J.Case) visit(b.getPattern(), p));
        b = b.withType(visitType(b.getType(), p));
        return b;
    }

    public J visitClassMethod(Ruby.ClassMethod classMethod, P p) {
        Ruby.ClassMethod c = classMethod;
        c = c.withPrefix(visitSpace(c.getPrefix(), RubySpace.Location.CLASS_METHOD_PREFIX, p));
        c = c.withMarkers(visitMarkers(c.getMarkers(), p));
        Statement temp = (Statement) visitStatement(c, p);
        if (!(temp instanceof Ruby.ClassMethod)) {
            return temp;
        } else {
            c = (Ruby.ClassMethod) temp;
        }
        c = c.withReceiver((Expression) visit(c.getReceiver(), p));
        c = c.getPadding().withMethod(visitLeftPadded(c.getPadding().getMethod(),
                JLeftPadded.Location.LANGUAGE_EXTENSION, p));
        return c;
    }

    public J visitDelimitedArray(Ruby.DelimitedArray delimitedArray, P p) {
        Ruby.DelimitedArray da = delimitedArray;
        da = da.withPrefix(visitSpace(da.getPrefix(), RubySpace.Location.DELIMITED_ARRAY_PREFIX, p));
        da = da.withMarkers(visitMarkers(da.getMarkers(), p));
        Expression temp = (Expression) visitExpression(da, p);
        if (!(temp instanceof Ruby.DelimitedArray)) {
            return temp;
        } else {
            da = (Ruby.DelimitedArray) temp;
        }
        da = da.getPadding().withElements(visitContainer(da.getPadding().getElements(),
                RubyContainer.Location.DELIMITED_ARRAY_ELEMENTS, p));
        da = da.withType(visitType(da.getType(), p));
        return da;
    }

    public J visitDelimitedString(Ruby.DelimitedString delimitedString, P p) {
        Ruby.DelimitedString ds = delimitedString;
        ds = ds.withPrefix(visitSpace(ds.getPrefix(), RubySpace.Location.DELIMITED_STRING_PREFIX, p));
        ds = ds.withMarkers(visitMarkers(ds.getMarkers(), p));
        Expression temp = (Expression) visitExpression(ds, p);
        if (!(temp instanceof Ruby.DelimitedString)) {
            return temp;
        } else {
            ds = (Ruby.DelimitedString) temp;
        }
        ds = ds.withStrings(ListUtils.map(ds.getStrings(), s -> visit(s, p)));
        ds = ds.withType(visitType(ds.getType(), p));
        return ds;
    }

    public J visitDelimitedStringValue(Ruby.DelimitedString.Value value, P p) {
        Ruby.DelimitedString.Value v = value;
        v = v.withMarkers(visitMarkers(v.getMarkers(), p));
        v = v.withTree(visit(v.getTree(), p));
        v = v.withAfter(visitSpace(v.getAfter(), RubySpace.Location.DELIMITED_STRING_VALUE_SUFFIX, p));
        return v;
    }

    public J visitPostExecution(Ruby.PostExecution end, P p) {
        Ruby.PostExecution e = end;
        e = e.withPrefix(visitSpace(e.getPrefix(), RubySpace.Location.POST_EXECUTION_PREFIX, p));
        e = e.withMarkers(visitMarkers(e.getMarkers(), p));
        e = e.withBlock((J.Block) visit(e.getBlock(), p));
        return e;
    }

    public J visitExpressionTypeTree(Ruby.ExpressionTypeTree expressionTypeTree, P p) {
        Ruby.ExpressionTypeTree s = expressionTypeTree;
        s = s.withPrefix(visitSpace(s.getPrefix(), RubySpace.Location.EXPRESSION_TYPE_TREE_PREFIX, p));
        s = s.withMarkers(visitMarkers(s.getMarkers(), p));
        s = s.withReference(visit(s.getReference(), p));
        return s;
    }

    public J visitHash(Ruby.Hash hash, P p) {
        Ruby.Hash h = hash;
        h = h.withPrefix(visitSpace(h.getPrefix(), RubySpace.Location.HASH_PREFIX, p));
        h = h.withMarkers(visitMarkers(h.getMarkers(), p));
        Expression temp = (Expression) visitExpression(h, p);
        if (!(temp instanceof Ruby.Hash)) {
            return temp;
        } else {
            h = (Ruby.Hash) temp;
        }
        h = h.getPadding().withPairs(visitContainer(h.getPadding().getPairs(),
                RubyContainer.Location.HASH_ELEMENTS, p));
        h = h.withType(visitType(h.getType(), p));
        return h;
    }

    public J visitHeredoc(Ruby.Heredoc heredoc, P p) {
        Ruby.Heredoc h = heredoc;
        h = h.withPrefix(visitSpace(h.getPrefix(), RubySpace.Location.HEREDOC_PREFIX, p));
        h = h.withMarkers(visitMarkers(h.getMarkers(), p));
        Expression temp = (Expression) visitExpression(h, p);
        if (!(temp instanceof Ruby.Heredoc)) {
            return temp;
        } else {
            h = (Ruby.Heredoc) temp;
        }
        h = h.withValue((J.Literal) visit(h.getValue(), p));
        h = h.withType(visitType(h.getType(), p));
        return h;
    }

    public J visitKeyValue(Ruby.Hash.KeyValue keyValue, P p) {
        Ruby.Hash.KeyValue k = keyValue;
        k = k.withPrefix(visitSpace(k.getPrefix(), RubySpace.Location.KEY_VALUE_PREFIX, p));
        k = k.withMarkers(visitMarkers(k.getMarkers(), p));
        Expression temp = (Expression) visitExpression(k, p);
        if (!(temp instanceof Ruby.Hash.KeyValue)) {
            return temp;
        } else {
            k = (Ruby.Hash.KeyValue) temp;
        }
        k = k.withKey((Expression) visit(k.getKey(), p));
        k = k.getPadding().withSeparator(visitLeftPadded(k.getPadding().getSeparator(),
                JLeftPadded.Location.LANGUAGE_EXTENSION, p));
        k = k.withValue((Expression) visit(k.getValue(), p));
        k = k.withType(visitType(k.getType(), p));
        return k;
    }

    public J visitModule(Ruby.Module module, P p) {
        Ruby.Module m = module;
        m = m.withPrefix(visitSpace(m.getPrefix(), RubySpace.Location.MODULE_PREFIX, p));
        m = m.withMarkers(visitMarkers(m.getMarkers(), p));
        Statement temp = (Statement) visitStatement(m, p);
        if (!(temp instanceof Ruby.Module)) {
            return temp;
        } else {
            m = (Ruby.Module) temp;
        }
        m = m.withName((J.Identifier) visit(m.getName(), p));
        m = m.withBlock((J.Block) visit(m.getBlock(), p));
        return m;
    }

    public J visitMultipleAssignment(Ruby.MultipleAssignment multipleAssignment, P p) {
        Ruby.MultipleAssignment m = multipleAssignment;
        m = m.withPrefix(visitSpace(m.getPrefix(), RubySpace.Location.MULTIPLE_ASSIGNMENT_PREFIX, p));
        m = m.withMarkers(visitMarkers(m.getMarkers(), p));
        Statement temp = (Statement) visitStatement(m, p);
        if (!(temp instanceof Ruby.MultipleAssignment)) {
            return temp;
        } else {
            m = (Ruby.MultipleAssignment) temp;
        }
        m = m.getPadding().withAssignments(visitContainer(m.getPadding().getAssignments(),
                RubyContainer.Location.MULTIPLE_ASSIGNMENT_ASSIGNMENTS, p));
        m = m.getPadding().withInitializers(visitContainer(m.getPadding().getAssignments(),
                RubyContainer.Location.MULTIPLE_ASSIGNMENT_INITIALIZERS, p));
        return m;
    }

    public J visitNext(Ruby.Next next, P p) {
        Ruby.Next n = next;
        n = n.withPrefix(visitSpace(n.getPrefix(), RubySpace.Location.NEXT_PREFIX, p));
        n = n.withMarkers(visitMarkers(n.getMarkers(), p));
        Statement temp = (Statement) visitStatement(n, p);
        if (!(temp instanceof Ruby.Next)) {
            return temp;
        } else {
            n = (Ruby.Next) temp;
        }
        n = n.withNext((J.Continue) visit(n.getNext(), p));
        n = n.withValue((Expression) visit(n.getValue(), p));
        return n;
    }

    public J visitNumericDomain(Ruby.NumericDomain numericDomain, P p) {
        Ruby.NumericDomain n = numericDomain;
        n = n.withPrefix(visitSpace(n.getPrefix(), RubySpace.Location.NUMERIC_DOMAIN_PREFIX, p));
        n = n.withMarkers(visitMarkers(n.getMarkers(), p));
        Expression temp = (Expression) visitExpression(n, p);
        if (!(temp instanceof Ruby.NumericDomain)) {
            return temp;
        } else {
            n = (Ruby.NumericDomain) temp;
        }
        n = n.withValue((Expression) visitNonNull(n.getValue(), p));
        n = n.withType(visitType(n.getType(), p));
        return n;
    }

    public J visitOpenEigenclass(Ruby.OpenEigenclass openEigenclass, P p) {
        Ruby.OpenEigenclass o = openEigenclass;
        o = o.withPrefix(visitSpace(o.getPrefix(), RubySpace.Location.OPEN_EIGENCLASS_PREFIX, p));
        o = o.withMarkers(visitMarkers(o.getMarkers(), p));
        Statement temp = (Statement) visitStatement(o, p);
        if (!(temp instanceof Ruby.OpenEigenclass)) {
            return temp;
        } else {
            o = (Ruby.OpenEigenclass) temp;
        }
        o = o.getPadding().withEigenclass(visitLeftPadded(o.getPadding().getEigenclass(),
                JLeftPadded.Location.LANGUAGE_EXTENSION, p));
        o = o.withBody((J.Block) visit(o.getBody(), p));
        return o;
    }

    public J visitRedo(Ruby.Redo redo, P p) {
        Ruby.Redo r = redo;
        r = r.withPrefix(visitSpace(r.getPrefix(), RubySpace.Location.REDO_PREFIX, p));
        r = r.withMarkers(visitMarkers(r.getMarkers(), p));
        Statement temp = (Statement) visitStatement(r, p);
        if (!(temp instanceof Ruby.Redo)) {
            return temp;
        } else {
            r = (Ruby.Redo) temp;
        }
        return r;
    }

    public J visitRescue(Ruby.Rescue rescue, P p) {
        Ruby.Rescue r = rescue;
        r = r.withPrefix(visitSpace(r.getPrefix(), RubySpace.Location.RESCUE_PREFIX, p));
        r = r.withMarkers(visitMarkers(r.getMarkers(), p));
        Statement temp = (Statement) visitStatement(r, p);
        if (!(temp instanceof Ruby.Rescue)) {
            return temp;
        } else {
            r = (Ruby.Rescue) temp;
        }
        r = r.withTry((J.Try) visitNonNull(r.getTry(), p));
        r = r.withElse((J.Block) visit(r.getElse(), p));
        return r;
    }

    public J visitRetry(Ruby.Retry retry, P p) {
        Ruby.Retry r = retry;
        r = r.withPrefix(visitSpace(r.getPrefix(), RubySpace.Location.RETRY_PREFIX, p));
        r = r.withMarkers(visitMarkers(r.getMarkers(), p));
        Statement temp = (Statement) visitStatement(r, p);
        if (!(temp instanceof Ruby.Retry)) {
            return temp;
        } else {
            r = (Ruby.Retry) temp;
        }
        return r;
    }

    public J visitRightwardAssignment(Ruby.RightwardAssignment rightwardAssignment, P p) {
        Ruby.RightwardAssignment r = rightwardAssignment;
        r = r.withPrefix(visitSpace(r.getPrefix(), RubySpace.Location.RIGHTWARD_ASSIGNMENT_PREFIX, p));
        r = r.withMarkers(visitMarkers(r.getMarkers(), p));
        Expression temp = (Expression) visitExpression(r, p);
        if (!(temp instanceof Ruby.RightwardAssignment)) {
            return temp;
        } else {
            r = (Ruby.RightwardAssignment) temp;
        }
        r = r.withLeft((Expression) visit(r.getLeft(), p));
        r = r.withPattern((J.Case) visit(r.getPattern(), p));
        r = r.withType(visitType(r.getType(), p));
        return r;
    }

    public J visitSplat(Ruby.Splat splat, P p) {
        Ruby.Splat s = splat;
        s = s.withPrefix(visitSpace(s.getPrefix(), RubySpace.Location.SPLAT_PREFIX, p));
        s = s.withMarkers(visitMarkers(s.getMarkers(), p));
        Expression temp = (Expression) visitExpression(s, p);
        if (!(temp instanceof Ruby.Splat)) {
            return temp;
        } else {
            s = (Ruby.Splat) temp;
        }
        s = s.withValue((Expression) visitNonNull(s.getValue(), p));
        s = s.withType(visitType(s.getType(), p));
        return s;
    }

    public J visitStructPattern(Ruby.StructPattern structPattern, P p) {
        Ruby.StructPattern s = structPattern;
        s = s.withPrefix(visitSpace(s.getPrefix(), RubySpace.Location.STRUCT_PATTERN_PREFIX, p));
        s = s.withMarkers(visitMarkers(s.getMarkers(), p));
        Expression temp = (Expression) visitExpression(s, p);
        if (!(temp instanceof Ruby.StructPattern)) {
            return temp;
        } else {
            s = (Ruby.StructPattern) temp;
        }
        s = s.withConstant((J.Identifier) visit(s.getConstant(), p));
        s = s.withType((JavaType) visit(s.getPattern(), p));
        s = s.withType(visitType(s.getType(), p));
        return s;
    }

    public J visitSubArrayIndex(Ruby.SubArrayIndex subArrayIndex, P p) {
        Ruby.SubArrayIndex s = subArrayIndex;
        s = s.withPrefix(visitSpace(s.getPrefix(), RubySpace.Location.SUB_ARRAY_PREFIX, p));
        s = s.withMarkers(visitMarkers(s.getMarkers(), p));
        Expression temp = (Expression) visitExpression(s, p);
        if (!(temp instanceof Ruby.SubArrayIndex)) {
            return temp;
        } else {
            s = (Ruby.SubArrayIndex) temp;
        }
        s = s.withStartIndex((Expression) visitNonNull(s.getStartIndex(), p));
        s = s.withLength((Expression) visitNonNull(s.getLength(), p));
        return s;
    }

    public J visitSymbol(Ruby.Symbol symbol, P p) {
        Ruby.Symbol s = symbol;
        s = s.withPrefix(visitSpace(s.getPrefix(), RubySpace.Location.SYMBOL_PREFIX, p));
        s = s.withMarkers(visitMarkers(s.getMarkers(), p));
        Expression temp = (Expression) visitExpression(s, p);
        if (!(temp instanceof Ruby.Symbol)) {
            return temp;
        } else {
            s = (Ruby.Symbol) temp;
        }
        s = s.withName((Expression) visit(s.getName(), p));
        s = s.withType(visitType(s.getType(), p));
        return s;
    }

    public J visitUnary(Ruby.Unary binary, P p) {
        Ruby.Unary u = binary;
        u = u.withPrefix(visitSpace(u.getPrefix(), Space.Location.UNARY_PREFIX, p));
        u = u.withMarkers(visitMarkers(u.getMarkers(), p));
        Expression temp = (Expression) visitExpression(u, p);
        if (!(temp instanceof Ruby.Unary)) {
            return temp;
        } else {
            u = (Ruby.Unary) temp;
        }
        u = u.withExpression((Expression) visit(u.getExpression(), p));
        u = u.withType(visitType(u.getType(), p));
        return u;
    }

    public J visitYield(Ruby.Yield yield, P p) {
        Ruby.Yield y = yield;
        y = y.withPrefix(visitSpace(y.getPrefix(), RubySpace.Location.YIELD_DATA, p));
        y = y.withMarkers(visitMarkers(y.getMarkers(), p));
        Statement temp = (Statement) visitStatement(y, p);
        if (!(temp instanceof Ruby.Yield)) {
            return temp;
        } else {
            y = (Ruby.Yield) temp;
        }
        y = y.getPadding().withData(visitContainer(y.getPadding().getData(),
                RubyContainer.Location.YIELD_DATA, p));
        return y;
    }
}
