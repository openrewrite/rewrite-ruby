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
        l = l.withPrefix(visitSpace(l.getPrefix(), RubySpace.Location.LIST_LITERAL, p));
        l = l.withMarkers(visitMarkers(l.getMarkers(), p));
        Expression temp = (Expression) visitExpression(l, p);
        if (!(temp instanceof Ruby.Array)) {
            return temp;
        } else {
            l = (Ruby.Array) temp;
        }
        l = l.getPadding().withElements(visitContainer(l.getPadding().getElements(), RubyContainer.Location.LIST_LITERAL_ELEMENTS, p));
        l = l.withType(visitType(l.getType(), p));
        return l;
    }

    public J visitBegin(Ruby.Begin begin, P p) {
        Ruby.Begin b = begin;
        b = b.withPrefix(visitSpace(b.getPrefix(), RubySpace.Location.BEGIN_PREFIX, p));
        b = b.withMarkers(visitMarkers(b.getMarkers(), p));
        b = b.withBlock((J.Block) visit(b.getBlock(), p));
        return b;
    }

    public J visitBinary(Ruby.Binary binary, P p) {
        Ruby.Binary b = binary;
        b = b.withPrefix(visitSpace(b.getPrefix(), RubySpace.Location.BINARY_PREFIX, p));
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
        b = b.withPrefix(visitSpace(b.getPrefix(), RubySpace.Location.BLOCK_PREFIX, p));
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

    public J visitEnd(Ruby.End end, P p) {
        Ruby.End e = end;
        e = e.withPrefix(visitSpace(e.getPrefix(), RubySpace.Location.END_PREFIX, p));
        e = e.withMarkers(visitMarkers(e.getMarkers(), p));
        e = e.withBlock((J.Block) visit(e.getBlock(), p));
        return e;
    }

    public J visitExpansion(Ruby.Expansion expansion, P p) {
        Ruby.Expansion e = expansion;
        e = e.withPrefix(visitSpace(e.getPrefix(), RubySpace.Location.EXPANSION_PREFIX, p));
        e = e.withMarkers(visitMarkers(e.getMarkers(), p));
        Expression temp = (Expression) visitExpression(e, p);
        if (!(temp instanceof Ruby.Expansion)) {
            return temp;
        } else {
            e = (Ruby.Expansion) temp;
        }
        e = e.withTree((TypedTree) visit(e.getTree(), p));
        return e;
    }

    public J visitExpressionTypeTree(Ruby.ExpressionTypeTree expressionTypeTree, P p) {
        Ruby.ExpressionTypeTree s = expressionTypeTree;
        s = s.withPrefix(visitSpace(s.getPrefix(), RubySpace.Location.EXPRESSION_TYPE_TREE_PREFIX, p));
        s = s.withMarkers(visitMarkers(s.getMarkers(), p));
        s = s.withNewType((J.NewClass) visit(s.getNewType(), p));
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
        h = h.getPadding().withElements(visitContainer(h.getPadding().getElements(),
                RubyContainer.Location.HASH_ELEMENTS, p));
        h = h.withType(visitType(h.getType(), p));
        return h;
    }

    public J visitKeyValue(Ruby.KeyValue keyValue, P p) {
        Ruby.KeyValue k = keyValue;
        k = k.withPrefix(visitSpace(k.getPrefix(), RubySpace.Location.KEY_VALUE_PREFIX, p));
        k = k.withMarkers(visitMarkers(k.getMarkers(), p));
        Expression temp = (Expression) visitExpression(k, p);
        if (!(temp instanceof Ruby.KeyValue)) {
            return temp;
        } else {
            k = (Ruby.KeyValue) temp;
        }
        k = k.withKey((Expression) visit(k.getKey(), p));
        k = k.getPadding().withValue(visitLeftPadded(k.getPadding().getValue(), JLeftPadded.Location.LANGUAGE_EXTENSION, p));
        k = k.withType(visitType(k.getType(), p));
        return k;
    }

    public J visitMultipleAssignment(Ruby.MultipleAssignment multipleAssignment, P p) {
        Ruby.MultipleAssignment m = multipleAssignment;
        m = m.withPrefix(visitSpace(m.getPrefix(), RubySpace.Location.MULTIPLE_ASSIGNMENT, p));
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
        return s;
    }

    public J visitSubArrayIndex(Ruby.SubArrayIndex subArrayIndex, P p) {
        Ruby.SubArrayIndex s = subArrayIndex;
        s = s.withPrefix(visitSpace(s.getPrefix(), RubySpace.Location.SUB_ARRAY_INDEX_PREFIX, p));
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

    public J visitUnary(Ruby.Unary binary, P p) {
        Ruby.Unary b = binary;
        b = b.withPrefix(visitSpace(b.getPrefix(), RubySpace.Location.UNARY_PREFIX, p));
        b = b.withMarkers(visitMarkers(b.getMarkers(), p));
        Expression temp = (Expression) visitExpression(b, p);
        if (!(temp instanceof Ruby.Unary)) {
            return temp;
        } else {
            b = (Ruby.Unary) temp;
        }
        b = b.withExpression((Expression) visit(b.getExpression(), p));
        b = b.withType(visitType(b.getType(), p));
        return b;
    }

    public J visitYield(Ruby.Yield yield, P p) {
        Ruby.Yield y = yield;
        y = y.withPrefix(visitSpace(y.getPrefix(), RubySpace.Location.YIELD, p));
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
