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
package org.openrewrite.ruby;

import org.jruby.RubySymbol;
import org.jruby.ast.*;
import org.jruby.ast.types.INameNode;
import org.jruby.ast.visitor.AbstractNodeVisitor;
import org.jruby.ast.visitor.OperatorCallNode;
import org.jruby.util.KeyValuePair;
import org.jruby.util.RegexpOptions;
import org.openrewrite.Cursor;
import org.openrewrite.FileAttributes;
import org.openrewrite.internal.EncodingDetectingInputStream;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.marker.ImplicitReturn;
import org.openrewrite.java.marker.OmitParentheses;
import org.openrewrite.java.marker.TrailingComma;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.ruby.internal.DelimiterMatcher;
import org.openrewrite.ruby.marker.*;
import org.openrewrite.ruby.tree.Ruby;
import org.openrewrite.ruby.tree.RubySpace;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.Space.EMPTY;

/**
 * For detailed descriptions of what every node type is, see
 * <a href="https://www.rubydoc.info/gems/ruby-internal/Node">Ruby internals on Node</a>.
 */
public class RubyParserVisitor extends AbstractNodeVisitor<J> {
    private final Path sourcePath;

    @Nullable
    private final FileAttributes fileAttributes;

    private final String source;
    private final Charset charset;
    private final boolean charsetBomMarked;

    private int cursor = 0;
    private Cursor nodes = new Cursor(null, Cursor.ROOT_VALUE);

    public RubyParserVisitor(Path sourcePath, @Nullable FileAttributes fileAttributes, EncodingDetectingInputStream source) {
        this.sourcePath = sourcePath;
        this.fileAttributes = fileAttributes;
        this.source = source.readFully();
        this.charset = source.getCharset();
        this.charsetBomMarked = source.isCharsetBomMarked();
    }

    @Override
    protected J defaultVisit(Node node) {
        throw new UnsupportedOperationException(String.format("Node type %s not yet implemented",
                node.getClass().getSimpleName()));
    }

    @Override
    public J visitAliasNode(AliasNode node) {
        return new Ruby.Alias(
                randomId(),
                sourceBefore("alias"),
                Markers.EMPTY,
                convert(node.getNewName()),
                convert(node.getOldName())
        );
    }

    @Override
    public J visitArgsCatNode(ArgsCatNode node) {
        Space prefix = whitespace();
        List<JRightPadded<Expression>> elements;
        if (node.getFirstNode() instanceof ListNode) {
            elements = convertAll(Arrays.asList(((ListNode) node.getFirstNode()).children()), n -> sourceBefore(","),
                    n -> sourceBefore(","));
        } else {
            elements = singletonList(padRight(convert(node.getFirstNode()), sourceBefore(",")));
        }
        elements = ListUtils.concat(elements, padRight(new Ruby.Splat(
                randomId(),
                sourceBefore("*"),
                Markers.EMPTY,
                convert(node.getSecondNode())
        ), EMPTY));

        return new Ruby.Array(
                randomId(),
                prefix,
                Markers.EMPTY,
                JContainer.build(
                        EMPTY,
                        elements,
                        Markers.EMPTY.add(new OmitParentheses(randomId()))
                ),
                null
        );
    }

    @Override
    public J visitAndNode(AndNode node) {
        Space prefix = whitespace();
        Expression left = convert(node.getFirstNode());
        Space opPrefix = whitespace();
        String op = source.startsWith("&&", cursor) ? "&&" : "and";
        skip(op);
        return new J.Binary(
                randomId(),
                prefix,
                op.equals("&&") ? Markers.EMPTY : Markers.EMPTY.add(new EnglishOperator(randomId())),
                left,
                padLeft(opPrefix, J.Binary.Type.And),
                convert(node.getSecondNode()),
                null
        );
    }

    @Override
    public J visitArgsNode(ArgsNode node) {
        throw new UnsupportedOperationException("Handled by convertArgs and skipped.");
    }

    @Override
    public Expression visitArgsPushNode(ArgsPushNode node) {
        return convert(node.getFirstNode());
    }

    @Override
    public J visitArgumentNode(ArgumentNode node) {
        return convertIdentifier(node.getName());
    }

    @Override
    public J visitArrayNode(ArrayNode node) {
        return visitListNode(node);
    }

    @Override
    public J visitArrayPatternNode(ArrayPatternNode node) {
        Space prefix = whitespace();
        if (node.getConstant() != null) {
            J.Identifier constant = convert(node.getConstant());
            String delimiter = source.substring(cursor, cursor + 1);
            return new Ruby.StructPattern(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    constant,
                    delimiter,
                    JContainer.build(
                            sourceBefore(delimiter),
                            singletonList(padRight(convert(node.getPreArgs()), sourceBefore(DelimiterMatcher.end(delimiter)))),
                            Markers.EMPTY
                    )
            );
        } else {
            return convert(node.getPreArgs());
        }
    }

    @Override
    public J visitAttrAssignNode(AttrAssignNode node) {
        Space prefix = whitespace();
        String attrName = node.getName().asJavaString();
        if (attrName.equals("[]=")) {
            return convertArrayAssignment(node, prefix);
        }
        return new J.Assignment(
                randomId(),
                prefix,
                Markers.EMPTY,
                new J.FieldAccess(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        convert(node.getReceiverNode()),
                        padLeft(sourceBefore("."), convertIdentifier(attrName.substring(0, attrName.indexOf('=')))),
                        null
                ),
                padLeft(sourceBefore("="), convert(node.getArgsNode())),
                null
        );
    }

    private J.Assignment convertArrayAssignment(AttrAssignNode node, Space prefix) {
        Expression arrayAccessReceiver = convert(node.getReceiverNode());
        Space arrayDimensionPrefix = sourceBefore("[");
        Expression index;
        Node assignment;
        if (node.getArgsNode() instanceof ArgsPushNode) {
            ArgsPushNode argsPushNode = (ArgsPushNode) node.getArgsNode();
            index = visitArgsPushNode(argsPushNode);
            assignment = argsPushNode.getSecondNode();
        } else {
            ListNode args = (ListNode) node.getArgsNode();
            if (args.size() == 2) {
                index = convert(args.get(0));
            } else if (args.size() == 3) {
                index = new Ruby.SubArrayIndex(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        convert(args.get(0)),
                        padLeft(sourceBefore(","), convert(args.get(1)))
                );
            } else {
                throw new IllegalStateException("Unexpected array index with 3 nodes");
            }
            assignment = args.get(args.size() - 1);
        }

        return new J.Assignment(
                randomId(),
                prefix,
                Markers.EMPTY,
                new J.ArrayAccess(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        arrayAccessReceiver,
                        new J.ArrayDimension(
                                randomId(),
                                arrayDimensionPrefix,
                                Markers.EMPTY,
                                padRight(index, sourceBefore("]"))
                        ),
                        null
                ),
                padLeft(sourceBefore("="), convert(assignment)),
                null
        );
    }

    @Override
    public J visitBackRefNode(BackRefNode node) {
        return convertIdentifier("$" + node.getType());
    }

    @Override
    public J visitBeginNode(BeginNode node) {
        throw new UnsupportedOperationException("Calls to visitBeginNode have not been observed " +
                                                "with a variety of rescue statements. Implement if one " +
                                                "is found.");
    }

    @Override
    public J visitBignumNode(BignumNode node) {
        return new J.Literal(
                randomId(),
                sourceBefore(node.getValue().toString()),
                Markers.EMPTY,
                node.getValue(),
                node.getValue().toString(),
                null,
                JavaType.Primitive.Long
        );
    }

    @Override
    public J visitBlockArgNode(BlockArgNode node) {
        return new Ruby.BlockArgument(
                randomId(),
                sourceBefore("&"),
                Markers.EMPTY,
                convertIdentifier(node.getName())
        );
    }

    @Override
    public J visitBlockPassNode(BlockPassNode node) {
        return new Ruby.BlockArgument(
                randomId(),
                sourceBefore("&"),
                Markers.EMPTY,
                convert(node.getBodyNode())
        );
    }

    @Override
    public J visitBlockNode(BlockNode node) {
        return visitBlock(node);
    }

    /**
     * @param node A single node or a list of nodes, which in any case needs to be wrapped ina
     *             a {@link J.Block}.
     * @return A {@link J.Block}.
     */
    private J.Block visitBlock(Node node) {
        return new J.Block(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                JRightPadded.build(false),
                convertBlockStatements(
                        node,
                        n -> {
                            Space eob = whitespace();
                            if (source.startsWith("end", cursor)) {
                                skip("end");
                            }
                            return eob;
                        }
                ),
                EMPTY
        );
    }

    @Override
    public J visitBreakNode(BreakNode node) {
        return new J.Break(
                randomId(),
                sourceBefore("break"),
                Markers.EMPTY,
                null
        );
    }

    @Override
    public J visitCallNode(CallNode node) {
        if (node.getName().asJavaString().equals("[]")) {
            return convertArrayAccess(node);
        }

        Space prefix = whitespace();
        J receiver = convert(node.getReceiverNode());
        Space beforeDot = sourceBefore(".");
        J.Identifier name = convertIdentifier(node.getName());
        if (!(receiver instanceof TypeTree)) {
            receiver = new Ruby.TypeReference(
                    randomId(),
                    receiver.getPrefix(),
                    Markers.EMPTY,
                    receiver.withPrefix(EMPTY)
            );
        }

        if (name.getSimpleName().equals("new")) {
            return new J.NewClass(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    padRight(new J.Empty(randomId(), EMPTY, Markers.EMPTY), beforeDot),
                    name.getPrefix(),
                    (TypeTree) receiver,
                    convertCallArgs(node),
                    null,
                    null
            );
        } else {
            return new J.MethodInvocation(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    padRight((Expression) receiver, beforeDot),
                    null,
                    name,
                    convertCallArgs(node),
                    null
            );
        }
    }

    private J.ArrayAccess convertArrayAccess(CallNode callNode) {
        return convertArrayAccess(callNode.getReceiverNode(), callNode.getArgsNode(), callNode.getIterNode());
    }

    private J.ArrayAccess convertArrayAccess(Node receiverNode, Node argsNode, @Nullable Node iterNode) {
        Space prefix = whitespace();
        Expression receiver = convert(receiverNode);
        JContainer<J> args = convertArgs("[", argsNode, iterNode, "]");
        List<J> argElems = args.getElements();
        if (argElems.size() == 2) {
            argElems = singletonList(new Ruby.SubArrayIndex(
                    randomId(),
                    argElems.get(0).getPrefix(),
                    Markers.EMPTY,
                    argElems.get(0).withPrefix(EMPTY),
                    padLeft(args.getPadding().getElements().get(0).getAfter(), (Expression) args.getElements().get(1))
            ));
        }

        return new J.ArrayAccess(
                randomId(),
                prefix,
                Markers.EMPTY,
                receiver,
                new J.ArrayDimension(
                        randomId(),
                        args.getBefore(),
                        Markers.EMPTY,
                        padRight((Expression) argElems.get(0), args.getLastSpace())
                ),
                null
        );
    }

    private List<JRightPadded<Statement>> convertBlockStatements(@Nullable Node node, Function<Node, Space> suffix) {
        if (node == null) {
            return emptyList();
        }
        List<? extends Node> trees;
        if (node instanceof ListNode && !(node instanceof DNode) && !(node instanceof ZArrayNode)) {
            trees = isMultipleStatements((ListNode) node) ?
                    Arrays.asList(((ListNode) node).children()) :
                    singletonList(node);
        } else {
            trees = singletonList(node);
        }

        if (trees.isEmpty()) {
            return emptyList();
        }
        List<JRightPadded<Statement>> converted = new ArrayList<>(trees.size());
        for (int i = 0; i < trees.size(); i++) {
            JRightPadded<J> stat = convert(trees.get(i), i == trees.size() - 1 ? suffix : n -> EMPTY);
            if (!(stat.getElement() instanceof Statement)) {
                stat = stat.withElement(new Ruby.ExpressionStatement(randomId(), (Expression) stat.getElement()));
            }
            if (stat.getElement() instanceof J.Empty) {
                continue;
            }
            //noinspection ReassignedVariable,unchecked
            converted.add((JRightPadded<Statement>) (JRightPadded<?>) stat);
        }
        return converted;
    }

    /**
     * We need a heuristic to tell whether a {@link ListNode} that is given as a block body represents
     * a single statement (e.g. a {@link Ruby.Array}) or multiple statements. All we have is an imperfect
     * heuristic right now, and seeking a better one.
     *
     * @param node A {@link ListNode} that serves as a block body.
     * @return {@code true} if the {@link ListNode} contains more than one statement.
     */
    private boolean isMultipleStatements(ListNode node) {
        if (node.size() <= 1) {
            return true;
        }
        int line = node.children()[0].getLine();
        Node[] children = node.children();
        for (int i = 1; i < children.length; i++) {
            if (children[i].getLine() != line) {
                return true;
            }
        }
        return false;
    }

    @Override
    public J visitCaseNode(CaseNode node) {
        return convertCase(node.getCaseNode(), node.getCases(), node.getElseNode(), false);
    }

    private J convertCase(Node caseNode, ListNode cases, @Nullable Node elseNode,
                          boolean patternCase) {
        Space prefix = whitespace();
        if (source.startsWith("case", cursor)) {
            return convertRubyCaseStatement(caseNode, cases, elseNode, patternCase)
                    .withPrefix(prefix);
        } else {
            List<Node> groupedCases = new ArrayList<>(cases.size());
            cases.forEach(groupedCases::add);
            Expression left = convert(caseNode);

            Space casePrefix = whitespace();

            boolean booleanCheck = source.startsWith("in", cursor);
            skip(booleanCheck ? "in" : "=>");
            J.Case pattern = ((J.Case) convertCases(groupedCases, true).getElement())
                    .withStatements(emptyList())
                    .withPrefix(casePrefix);

            if (booleanCheck) {
                return new Ruby.BooleanCheck(
                        randomId(),
                        prefix,
                        Markers.EMPTY,
                        left,
                        pattern,
                        null
                );
            } else {
                return new Ruby.RightwardAssignment(
                        randomId(),
                        prefix,
                        Markers.EMPTY,
                        left,
                        pattern,
                        null
                );
            }
        }
    }

    private J.Switch convertRubyCaseStatement(Node caseNode, ListNode cases, @Nullable Node elseNode,
                                              boolean patternCase) {
        skip("case");
        J.ControlParentheses<Expression> selector = new J.ControlParentheses<>(
                randomId(),
                EMPTY,
                Markers.EMPTY,
                padRight(convert(caseNode), EMPTY)
        );

        Map<Integer, List<Node>> groupedWhen = new LinkedHashMap<>();
        for (Node aCase : cases) {
            if (aCase != null) {
                groupedWhen.computeIfAbsent(aCase.getLine(), k -> new ArrayList<>()).add(aCase);
            }
        }

        List<JRightPadded<Statement>> mappedCases = new ArrayList<>(groupedWhen.size());
        for (List<Node> c : groupedWhen.values()) {
            Space prefix = sourceBefore(patternCase ? "in" : "when");
            JRightPadded<Statement> mapped = convertCases(c, patternCase);
            mappedCases.add(mapped.withElement(mapped.getElement().withPrefix(prefix)));
        }

        J.Block caseBlock = new J.Block(
                randomId(),
                EMPTY,
                Markers.EMPTY,
                JRightPadded.build(false),
                mappedCases,
                elseNode == null ? sourceBefore("end") : EMPTY
        );

        if (elseNode != null) {
            Space elsePrefix = sourceBefore("else");
            JContainer<Statement> body = JContainer.build(
                    whitespace(),
                    convertBlockStatements(elseNode, n -> EMPTY),
                    Markers.EMPTY
            );
            caseBlock = caseBlock.getPadding().withStatements(ListUtils.concat(caseBlock.getPadding().getStatements(),
                    padRight(new J.Case(
                            randomId(),
                            elsePrefix,
                            Markers.EMPTY,
                            J.Case.Type.Statement,
                            JContainer.empty(),
                            body,
                            null
                    ), EMPTY)));
            caseBlock = caseBlock.withEnd(sourceBefore("end"));
        }

        return new J.Switch(
                randomId(),
                EMPTY,
                Markers.EMPTY,
                selector,
                caseBlock
        );
    }

    private JRightPadded<Statement> convertCases(List<Node> cases, boolean patternCase) {
        List<Node> expressionNodes = new ArrayList<>();
        for (Node node : cases) {
            if (patternCase) {
                InNode in = (InNode) node;
                if (in.getExpression() instanceof ArrayNode) {
                    Collections.addAll(expressionNodes, ((ArrayNode) in.getExpression()).children());
                } else {
                    expressionNodes.add(in.getExpression());
                }
            } else {
                WhenNode when = (WhenNode) node;
                if (when.getExpressionNodes() instanceof ArrayNode) {
                    Collections.addAll(expressionNodes, ((ArrayNode) when.getExpressionNodes()).children());
                } else {
                    expressionNodes.add(when.getExpressionNodes());
                }
            }
        }

        JContainer<Expression> expressions = JContainer.build(
                whitespace(),
                convertAll(expressionNodes, n -> sourceBefore(","), n -> EMPTY),
                Markers.EMPTY
        );

        int cursorBeforeBodyWhitespace = cursor;
        Space beforeBody = whitespace();
        Node body = cases.get(0) instanceof WhenNode ?
                ((WhenNode) cases.get(0)).getBodyNode() :
                ((InNode) cases.get(0)).getBody();

        Markers markers = Markers.EMPTY;
        if (patternCase) {
            markers = markers.add(new PatternCase(randomId()));
        }

        if (source.startsWith("then", cursor)) {
            expressions = expressions.withMarkers(expressions.getMarkers().add(new ExplicitThen(randomId())));
            expressions = expressions.getPadding().withElements(ListUtils.mapLast(
                    expressions.getPadding().getElements(), last -> last.withAfter(beforeBody)));
            skip("then");
        } else {
            cursor = cursorBeforeBodyWhitespace;
        }

        return padRight(new J.Case(
                randomId(),
                EMPTY,
                markers,
                J.Case.Type.Statement,
                expressions,
                JContainer.build(
                        whitespace(),
                        convertBlockStatements(body, n -> EMPTY),
                        Markers.EMPTY
                ),
                null
        ), EMPTY);
    }

    @Override
    public J visitClassNode(ClassNode node) {
        Space prefix = whitespace();
        skip("class");
        J.Identifier name = convertIdentifier(node.getCPath().getName());

        JLeftPadded<TypeTree> extendings = null;
        Node superNode = node.getSuperNode();
        if (superNode != null) {
            Space extendingsPrefix = sourceBefore("<");
            Expression superClass = convert(superNode);
            extendings = padLeft(
                    extendingsPrefix,
                    superClass instanceof TypeTree ?
                            (TypeTree) superClass :
                            new Ruby.ExpressionTypeTree(
                                    randomId(),
                                    superClass.getPrefix(),
                                    Markers.EMPTY,
                                    superClass.withPrefix(EMPTY)
                            )
            );
        }

        return new J.ClassDeclaration(
                randomId(),
                prefix,
                Markers.EMPTY,
                emptyList(),
                emptyList(),
                new J.ClassDeclaration.Kind(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        emptyList(),
                        J.ClassDeclaration.Kind.Type.Class
                ),
                name,
                null,
                null,
                extendings,
                null,
                null,
                visitBlock(node.getBodyNode()),
                null
        );
    }

    @Override
    public J visitClassVarAsgnNode(ClassVarAsgnNode node) {
        return visitAsgnNode(node, node.getName());
    }

    @Override
    public J visitClassVarNode(ClassVarNode node) {
        return convertIdentifier(node.getName());
    }

    @Override
    public J visitColon2Node(Colon2Node node) {
        return new J.MemberReference(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                padRight(convert(node.getLeftNode()), sourceBefore("::")),
                null,
                padLeft(EMPTY, convertIdentifier(node.getName())),
                null,
                null,
                null
        );
    }

    @Override
    public J visitColon3Node(Colon3Node node) {
        return new J.MemberReference(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                padRight(new J.Empty(randomId(), EMPTY, Markers.EMPTY),
                        source.startsWith("::", cursor) ? sourceBefore("::") : EMPTY),
                null,
                padLeft(EMPTY, convertIdentifier(node.getName())),
                null,
                null,
                null
        );
    }

    @Override
    public J visitComplexNode(ComplexNode node) {
        return new Ruby.NumericDomain(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                padRight(convert(node.getNumber()), sourceBefore("i")),
                Ruby.NumericDomain.Domain.Complex,
                null
        );
    }

    @Override
    public J visitConstDeclNode(ConstDeclNode node) {
        return visitAsgnNode(node, node.getName());
    }

    @Override
    public J visitConstNode(ConstNode node) {
        return convertIdentifier(node.getName());
    }

    @Override
    public J visitDefinedNode(DefinedNode node) {
        return new Ruby.Unary(
                randomId(),
                sourceBefore("defined?"),
                Markers.EMPTY,
                Ruby.Unary.Type.Defined,
                convert(node.getExpressionNode())
        );
    }

    @Override
    public J visitDefnNode(DefnNode node) {
        return convertDefNode(node);
    }

    @Override
    public J visitDefsNode(DefsNode node) {
        return convertDefNode(node);
    }

    private Statement convertDefNode(MethodDefNode node) {
        Space prefix = sourceBefore("def");

        Expression classMethodReceiver = null;
        Space classMethodReceiverDot = null;
        if (node instanceof DefsNode) {
            classMethodReceiver = convert(((DefsNode) node).getReceiverNode());
            classMethodReceiverDot = sourceBefore(".");
        }

        J.MethodDeclaration.IdentifierWithAnnotations name = new J.MethodDeclaration.IdentifierWithAnnotations(
                convertIdentifier(node.getName()),
                emptyList()
        );

        JContainer<J> args = convertArgs("(", node.getArgsNode(), null, ")");
        args = JContainer.withElements(args, ListUtils.map(args.getElements(), arg -> {
            if (arg instanceof J.Identifier) {
                return new J.VariableDeclarations(
                        randomId(),
                        arg.getPrefix(),
                        Markers.EMPTY,
                        emptyList(),
                        emptyList(),
                        null,
                        null,
                        emptyList(),
                        singletonList(padRight(new J.VariableDeclarations.NamedVariable(
                                randomId(),
                                EMPTY,
                                Markers.EMPTY,
                                arg.withPrefix(EMPTY),
                                emptyList(),
                                null,
                                null
                        ), EMPTY))
                );
            }
            return arg;
        }));

        J body = convert(node.getBodyNode());
        if (!(body instanceof J.Block)) {
            Statement bodyStatement = body instanceof Statement ?
                    (Statement) body :
                    new Ruby.ExpressionStatement(randomId(), (Expression) body);
            body = new J.Block(randomId(), EMPTY, Markers.EMPTY,
                    JRightPadded.build(false), singletonList(padRight(bodyStatement, EMPTY)),
                    sourceBefore("end"));
        }

        J.Block bodyBlock = (J.Block) body;
        bodyBlock = bodyBlock.getPadding().withStatements(ListUtils.mapLast(bodyBlock.getPadding().getStatements(), statement ->
                statement.getElement() instanceof J.Return || !(statement.getElement() instanceof Expression) ?
                        statement :
                        statement.withElement(new J.Return(
                                randomId(),
                                statement.getElement().getPrefix(),
                                Markers.EMPTY.add(new ImplicitReturn(randomId())),
                                statement.getElement().withPrefix(EMPTY)
                        ))
        ));

        //noinspection unchecked
        J.MethodDeclaration method = new J.MethodDeclaration(
                randomId(),
                prefix,
                Markers.EMPTY,
                emptyList(),
                emptyList(),
                null,
                null,
                name,
                (JContainer<Statement>) (JContainer<?>) args,
                null,
                bodyBlock,
                null,
                null
        );

        if (node instanceof DefsNode) {
            return new Ruby.ClassMethod(
                    randomId(),
                    method.getPrefix(),
                    Markers.EMPTY,
                    classMethodReceiver,
                    padLeft(classMethodReceiverDot, method.withPrefix(EMPTY))
            );
        }

        return method;
    }

    @Override
    public J visitDotNode(DotNode node) {
        return maybeParenthesized(prefix -> {
            Expression left = convert(node.getBeginNode());
            Space opPrefix = whitespace();
            String op = source.substring(cursor).startsWith("...") ? "..." : "..";
            skip(op);
            return new Ruby.Binary(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    left,
                    padLeft(opPrefix, op.equals("...") ? Ruby.Binary.Type.RangeExclusive : Ruby.Binary.Type.RangeInclusive),
                    (Expression) node.getEndNode().accept(this),
                    null
            );
        });
    }

    private J maybeParenthesized(Function<Space, J> insideParentheses) {
        Space prefix = whitespace();
        if (source.startsWith("(", cursor)) {
            skip("(");
            J inside = maybeParenthesized(insideParentheses);
            return new J.Parentheses<>(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    padRight(inside, sourceBefore(")"))
            );
        }
        return insideParentheses.apply(prefix);
    }

    @Override
    public J visitDAsgnNode(DAsgnNode node) {
        return visitAsgnNode(node, node.getName());
    }

    @Override
    public J visitDRegxNode(DRegexpNode node) {
        return ((Ruby.DelimitedString) convertStrings(node))
                .withRegexpOptions(convertRegexOptions(node.getOptions()));
    }

    private List<Ruby.DelimitedString.RegexpOptions> convertRegexOptions(RegexpOptions options) {
        return regexOptionsString(options).chars().mapToObj(opt -> {
            switch (opt) {
                case 'x':
                    return Ruby.DelimitedString.RegexpOptions.Extended;
                case 'i':
                    return Ruby.DelimitedString.RegexpOptions.IgnoreCase;
                case 'm':
                    return Ruby.DelimitedString.RegexpOptions.Multiline;
                case 'j':
                    return Ruby.DelimitedString.RegexpOptions.Java;
                case 'o':
                    return Ruby.DelimitedString.RegexpOptions.Once;
                case 'n':
                    return Ruby.DelimitedString.RegexpOptions.None;
                case 'e':
                    return Ruby.DelimitedString.RegexpOptions.EUCJPEncoding;
                case 's':
                    return Ruby.DelimitedString.RegexpOptions.SJISEncoding;
                case 'u':
                    return Ruby.DelimitedString.RegexpOptions.UTF8Encoding;
                default:
                    throw new UnsupportedOperationException(String.format("Unknown regexp option %s", opt));
            }
        }).collect(toList());
    }

    private String regexOptionsString(RegexpOptions options) {
        int optionCount = 0;
        if (options.isExtended()) {
            optionCount++;
        }
        if (!options.isKcodeDefault()) {
            optionCount++;
        }
        if (options.isIgnorecase()) {
            optionCount++;
        }
        if (options.isJava()) {
            optionCount++;
        }
        if (options.isLiteral()) {
            optionCount++;
        }
        if (options.isMultiline()) {
            optionCount++;
        }
        if (options.isOnce()) {
            optionCount++;
        }
        String optionsString = source.substring(cursor, cursor + optionCount);
        skip(optionsString);
        return optionsString;
    }

    @Override
    public J visitDStrNode(DStrNode node) {
        return convertStrings(node);
    }

    @Override
    public J visitDSymbolNode(DSymbolNode node) {
        return convertSymbols(node);
    }

    @Override
    public J visitDVarNode(DVarNode node) {
        return convertIdentifier(node.getName());
    }

    @Override
    public J visitDXStrNode(DXStrNode node) {
        return convertStrings(node);
    }

    @Override
    public J visitEncodingNode(EncodingNode node) {
        return convertIdentifier("__ENCODING__");
    }

    @Override
    public J visitEnsureNode(EnsureNode node) {
        Ruby.Rescue rescue = convert(node.getBodyNode());
        Space prefix;
        if (rescue.getElse() == null) {
            List<J.Try.Catch> catches = rescue.getTry().getCatches();
            prefix = catches.get(catches.size() - 1).getBody().getEnd();
            rescue = rescue.withTry(rescue.getTry().withCatches(ListUtils.mapLast(catches, c ->
                    c.withBody(c.getBody().withEnd(EMPTY)))));
        } else {
            prefix = rescue.getElse().getEnd();
            rescue = rescue.withElse(rescue.getElse().withEnd(EMPTY));
        }

        skip("ensure");
        List<JRightPadded<Statement>> ensureBody;
        if (node.getEnsureNode() instanceof BlockNode) {
            ensureBody = convertAll(Arrays.asList(((BlockNode) node.getEnsureNode()).children()),
                    n -> EMPTY, n -> EMPTY);
        } else {
            ensureBody = singletonList(padRight(convert(node.getEnsureNode()), EMPTY));
        }

        return rescue.withTry(rescue.getTry().withFinally(new J.Block(
                randomId(),
                prefix,
                Markers.EMPTY,
                JRightPadded.build(false),
                ensureBody,
                sourceBefore("end")
        )));
    }

    @Override
    public J visitEvStrNode(EvStrNode node) {
        skip("#{");
        return new Ruby.DelimitedString.Value(
                randomId(),
                Markers.EMPTY,
                convert(node.getBody()),
                sourceBefore("}")
        );
    }

    @Override
    public J visitFalseNode(FalseNode node) {
        return new J.Literal(randomId(), sourceBefore("false"), Markers.EMPTY, true, "false",
                null, JavaType.Primitive.Boolean);
    }

    @Override
    public J visitFCallNode(FCallNode node) {
        Space prefix = whitespace();
        J.Identifier name = convertIdentifier(node.getName());
        return new J.MethodInvocation(
                randomId(),
                prefix,
                Markers.EMPTY,
                null,
                null,
                name,
                convertCallArgs(node),
                null
        );
    }

    private <T extends BlockAcceptingNode & IArgumentNode> JContainer<Expression> convertCallArgs(T node) {
        return convertArgs("(", node.getArgsNode(), node.getIterNode(), ")");
    }

    @Override
    public J visitFindPatternNode(FindPatternNode node) {
        Space prefix = whitespace();
        ArrayNode allArgs = new ArrayNode(0);
        if (node.getPreRestArg() != null) {
            allArgs.add(node.getPreRestArg());
        }
        allArgs.addAll(node.getArgs());
        if (node.getPostRestArg() != null) {
            allArgs.add(node.getPostRestArg());
        }
        JContainer<Expression> elements = convertArgs("[", allArgs, null, "]");
        return new Ruby.Array(
                randomId(),
                prefix,
                Markers.EMPTY,
                elements,
                null
        );
    }

    @Override
    public J visitFixnumNode(FixnumNode node) {
        return new J.Literal(
                randomId(),
                sourceBefore(Long.toString(node.getValue())),
                Markers.EMPTY,
                node.getValue(),
                Long.toString(node.getValue()),
                null,
                JavaType.Primitive.Long
        );
    }

    @Override
    public J visitFlipNode(FlipNode node) {
        Space prefix = whitespace();
        Expression left = convert(node.getBeginNode());
        Space opPrefix = sourceBefore("..");
        Ruby.Binary.Type op = Ruby.Binary.Type.FlipFlopInclusive;
        if (source.charAt(cursor) == '.') {
            skip(".");
            op = Ruby.Binary.Type.FlipFlopExclusive;
        }
        return new Ruby.Binary(
                randomId(),
                prefix,
                Markers.EMPTY,
                left,
                padLeft(opPrefix, op),
                convert(node.getEndNode()),
                JavaType.Primitive.Boolean
        );
    }

    @Override
    public J visitFloatNode(FloatNode node) {
        return new J.Literal(
                randomId(),
                sourceBefore(Double.toString(node.getValue())),
                Markers.EMPTY,
                node.getValue(),
                Double.toString(node.getValue()),
                null,
                JavaType.Primitive.Float
        );
    }

    @Override
    public J visitForNode(ForNode node) {
        Markers markers = Markers.EMPTY;
        Space prefix = sourceBefore("for");

        J.Identifier variableIdentifier = convert(node.getVarNode());
        JRightPadded<J.VariableDeclarations> variable = padRight(new J.VariableDeclarations(
                randomId(),
                variableIdentifier.getPrefix(),
                Markers.EMPTY,
                emptyList(),
                emptyList(),
                null,
                null,
                emptyList(),
                singletonList(padRight(new J.VariableDeclarations.NamedVariable(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        variableIdentifier.withPrefix(EMPTY),
                        emptyList(),
                        null,
                        null
                ), EMPTY))
        ), sourceBefore("in"));

        JRightPadded<Expression> iterable = padRight(convert(node.getIterNode()), whitespace());

        return new J.ForEachLoop(
                randomId(),
                prefix,
                markers,
                new J.ForEachLoop.Control(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        variable,
                        iterable
                ),
                padRight(convert(node.getBodyNode()), sourceBefore("end"))
        );
    }

    @Override
    public J visitGlobalAsgnNode(GlobalAsgnNode node) {
        return visitAsgnNode(node, node.getName());
    }

    @Override
    public J visitGlobalVarNode(GlobalVarNode node) {
        return convertIdentifier(node.getName());
    }

    @Override
    public J visitHashNode(HashNode node) {
        return convertHash(node, null);
    }

    private Ruby.Hash convertHash(HashNode node, @Nullable Node restArg) {
        Space prefix = whitespace();
        boolean omitBrackets = source.charAt(cursor) != '{';
        if (!omitBrackets) {
            skip("{");
        }

        List<JRightPadded<Expression>> pairs = new ArrayList<>(node.getPairs().size());
        List<KeyValuePair<Node, Node>> nodePairs = node.getPairs();
        for (int i = 0; i < nodePairs.size(); i++) {
            KeyValuePair<Node, Node> kv = nodePairs.get(i);
            Space kvPrefix = whitespace();
            Expression key = convert(kv.getKey());
            Space separatorPrefix = whitespace();
            Ruby.KeyValue.Separator separator;
            if (source.startsWith("=>", cursor)) {
                separator = Ruby.KeyValue.Separator.Rocket;
                skip("=>");
            } else {
                separator = Ruby.KeyValue.Separator.Colon;
                skip(":");
            }

            int cursorBeforeWhitespace = cursor;
            Space valuePrefix = whitespace();
            Expression value;
            if (kv.getValue() instanceof LocalAsgnNode &&
                !source.startsWith(((LocalAsgnNode) kv.getValue()).getName().asJavaString(), cursor)) {
                // in hash pattern matching you can match on {sym:} with no value
                // to the right of the symbol. not valid in hash literals
                value = new J.Empty(randomId(), EMPTY, Markers.EMPTY);
                cursor = cursorBeforeWhitespace;
            } else {
                value = convert(kv.getValue()).withPrefix(valuePrefix);
            }

            pairs.add(padRight(new Ruby.KeyValue(
                    randomId(),
                    kvPrefix,
                    Markers.EMPTY,
                    key,
                    padLeft(separatorPrefix, separator),
                    value,
                    null
            ), i == nodePairs.size() - 1 && restArg == null ? EMPTY : sourceBefore(",")));
        }
        if (restArg != null) {
            pairs.add(padRight(convert(restArg), EMPTY));
        }
        if (nodePairs.isEmpty() && restArg == null) {
            pairs.add(padRight(new J.Empty(randomId(), EMPTY, Markers.EMPTY), EMPTY));
        }

        if (!omitBrackets) {
            pairs = ListUtils.mapLast(pairs, last -> last.withAfter(sourceBefore("}")));
        }

        return new Ruby.Hash(
                randomId(),
                prefix,
                omitBrackets ?
                        Markers.EMPTY.add(new OmitParentheses(randomId())) :
                        Markers.EMPTY,
                JContainer.build(EMPTY, pairs, Markers.EMPTY),
                null
        );
    }

    @Override
    public J visitHashPatternNode(HashPatternNode node) {
        Space prefix = whitespace();
        if (node.getConstant() != null) {
            J.Identifier constant = convert(node.getConstant());
            String delimiter = source.substring(cursor, cursor + 1);
            return new Ruby.StructPattern(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    constant,
                    delimiter,
                    JContainer.build(
                            sourceBefore(delimiter),
                            singletonList(padRight(convertHash(node.getKeywordArgs(), node.getRestArg()),
                                    sourceBefore(DelimiterMatcher.end(delimiter)))),
                            Markers.EMPTY
                    )
            );
        } else {
            return convertHash(node.getKeywordArgs(), node.getRestArg());
        }
    }

    @Override
    public J visitIfNode(IfNode node) {
        Space prefix = whitespace();
        return (source.startsWith("if", cursor) ?
                ifStatement(node) :
                ifModifier(node)).withPrefix(prefix);
    }

    private J.If ifModifier(IfNode node) {
        Statement thenElem = convert(node.getThenBody());
        JRightPadded<Statement> then = padRight(thenElem, sourceBefore("if"));
        Space ifConditionPrefix = whitespace();
        Expression ifConditionExpr = convert(node.getCondition());
        J.ControlParentheses<Expression> ifCondition = new J.ControlParentheses<>(
                randomId(),
                ifConditionPrefix,
                Markers.EMPTY,
                padRight(ifConditionExpr, EMPTY)
        );
        return new J.If(
                randomId(),
                EMPTY,
                Markers.EMPTY.add(new IfModifier(randomId())),
                ifCondition,
                then,
                null
        );
    }

    private J.If ifStatement(IfNode node) {
        skip("if");
        Space ifConditionPrefix = whitespace();
        Expression ifConditionExpr = convert(node.getCondition());
        boolean explicitThen = Pattern.compile("\\s+then").matcher(source).find(cursor);
        J.ControlParentheses<Expression> ifCondition = new J.ControlParentheses<>(
                randomId(),
                ifConditionPrefix,
                explicitThen ?
                        Markers.EMPTY.add(new ExplicitThen(randomId())) :
                        Markers.EMPTY,
                padRight(ifConditionExpr, explicitThen ? sourceBefore("then") : EMPTY)
        );

        Statement thenElem = convert(node.getThenBody());
        JRightPadded<Statement> then = node.getElseBody() == null ?
                padRight(thenElem, sourceBefore("end")) :
                padRight(thenElem, EMPTY);

        J.If.Else anElse = null;
        if (node.getElseBody() != null) {
            Space elsePrefix = whitespace();
            skip(source.startsWith("else", cursor) ? "else" : "els");
            anElse = new J.If.Else(
                    randomId(),
                    elsePrefix,
                    Markers.EMPTY,
                    padRight(convert(node.getElseBody()),
                            node.getElseBody() instanceof IfNode ? EMPTY : sourceBefore("end"))
            );
        }

        return new J.If(
                randomId(),
                EMPTY,
                Markers.EMPTY,
                ifCondition,
                then,
                anElse
        );
    }

    @Override
    public J visitInNode(InNode node) {
        return super.visitInNode(node);
    }

    @Override
    public J visitInstAsgnNode(InstAsgnNode node) {
        return visitAsgnNode(node, node.getName());
    }

    @Override
    public J visitInstVarNode(InstVarNode node) {
        return convertIdentifier(node.getName());
    }

    @Override
    public J visitIterNode(IterNode node) {
        Space prefix = whitespace();
        J.Block body;
        JContainer<J> parameters = null;
        boolean inline = source.charAt(cursor) == '{';
        if (inline) {
            skip("{");
            if (!node.getArgsNode().isEmpty() || node.getArgsNode().getBlock() != null) {
                parameters = convertArgs("|", node.getArgsNode(), null, "|");
            }
            body = visitBlock(node.getBodyNode());
            skip("}");
        } else {
            skip("do");
            if (!node.getArgsNode().isEmpty()) {
                parameters = convertArgs("|", node.getArgsNode(), null, "|");
            }
            body = visitBlock(node.getBodyNode());
            // visitBlock handles the skip("end")
        }
        return new Ruby.Block(
                randomId(),
                prefix,
                Markers.EMPTY,
                inline,
                parameters,
                body
        );
    }

    @Override
    public J visitLambdaNode(LambdaNode node) {
        Space prefix = sourceBefore("->");
        Space parametersPrefix = whitespace();
        JContainer<J> args = convertArgs("(", node.getArgsNode(), null, ")");
        return new J.Lambda(
                randomId(),
                prefix,
                Markers.EMPTY,
                new J.Lambda.Parameters(
                        randomId(),
                        parametersPrefix,
                        Markers.EMPTY,
                        true,
                        args.getPadding().getElements()
                ),
                EMPTY,
                new J.Block(
                        randomId(),
                        sourceBefore("{"),
                        Markers.EMPTY,
                        JRightPadded.build(false),
                        convertBlockStatements(node.getBodyNode(), n -> EMPTY),
                        sourceBefore("}")
                ),
                null
        );
    }

    @Override
    public J visitListNode(ListNode node) {
        Space prefix = whitespace();

        if (!node.isEmpty() && source.startsWith("%", cursor)) {
            Node first = node.get(0);
            if (first instanceof SymbolNode || first instanceof DSymbolNode) {
                // this is a symbol array literal like %i[foo bar baz]
                return convertSymbols(node.children()).withPrefix(prefix);
            } else if (first instanceof StrNode || first instanceof DStrNode) {
                // this is a string array literal like %w[foo bar baz]
                return convertStrings(node.children()).withPrefix(prefix);
            }
        }

        JContainer<Expression> elements = convertArgs("[", node, null, "]");
        return new Ruby.Array(
                randomId(),
                prefix,
                Markers.EMPTY,
                elements,
                null
        );
    }

    @Override
    public J visitLiteralNode(LiteralNode node) {
        return convertIdentifier(node.getSymbolName());
    }

    @Override
    public J visitLocalAsgnNode(LocalAsgnNode node) {
        Space prefix = whitespace();
        boolean namedSingleSplat = source.startsWith("*" + node.getName().asJavaString(), cursor);
        if (namedSingleSplat) {
            skip("*");
        }
        Expression assignment = visitAsgnNode(node, node.getName());
        if (namedSingleSplat) {
            J.Identifier named = (J.Identifier) assignment;
            assignment = named.withSimpleName("*" + named.getSimpleName());
        }
        return assignment.withPrefix(prefix);
    }

    private Expression visitAsgnNode(AssignableNode node, RubySymbol name) {
        if (node.getValueNode() instanceof OperatorCallNode) {
            Space variablePrefix = whitespace();
            J.Identifier variable = convertIdentifier(name);
            OperatorCallNode assignOp = (OperatorCallNode) node.getValueNode();
            Space opPrefix = whitespace();
            if (source.charAt(cursor) == '=') {
                skip("=");
                return new J.Assignment(
                        randomId(),
                        variablePrefix,
                        Markers.EMPTY,
                        variable,
                        padLeft(opPrefix, visitOperatorCallNode(assignOp)),
                        null
                );
            } else {
                Expression mapped = convertOpAsgn(
                        () -> variable,
                        assignOp.getName().asJavaString(), ((ListNode) assignOp.getArgsNode()).get(0)
                ).withPrefix(variablePrefix);
                if (mapped instanceof J.AssignmentOperation) {
                    J.AssignmentOperation j = (J.AssignmentOperation) mapped;
                    return j.getPadding().withOperator(j.getPadding().getOperator().withBefore(opPrefix));
                }
                Ruby.AssignmentOperation r = (Ruby.AssignmentOperation) mapped;
                return r.getPadding().withOperator(r.getPadding().getOperator().withBefore(opPrefix));
            }
        } else {
            Space prefix = whitespace();
            J.Identifier variable = convertIdentifier(name);
            if (node.getValueNode() instanceof NilImplicitNode) {
                return variable.withPrefix(prefix);
            }
            return new J.Assignment(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    variable,
                    padLeft(sourceBefore("="), convert(node.getValueNode())),
                    null
            );
        }
    }

    private Object convertAssignmentOpType(String op) {
        switch (op) {
            case "+":
                return J.AssignmentOperation.Type.Addition;
            case "-":
                return J.AssignmentOperation.Type.Subtraction;
            case "*":
                return J.AssignmentOperation.Type.Multiplication;
            case "/":
                return J.AssignmentOperation.Type.Division;
            case "%":
                return J.AssignmentOperation.Type.Modulo;
            case "**":
                return J.AssignmentOperation.Type.Exponentiation;
            case "&&":
                return Ruby.AssignmentOperation.Type.And;
            case "||":
                return Ruby.AssignmentOperation.Type.Or;
            default:
                throw new UnsupportedOperationException("Unsupported assignment operator " + op);
        }
    }

    @Override
    public J visitLocalVarNode(LocalVarNode node) {
        return convertIdentifier(node.getName());
    }

    @Override
    public J visitMatchNode(MatchNode node) {
        return convert(node.getRegexpNode());
    }

    @Override
    public J visitMatch2Node(Match2Node node) {
        return convert(node.getReceiverNode());
    }

    @Override
    public J visitMatch3Node(Match3Node node) {
        return new Ruby.Binary(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                convert(node.getReceiverNode()),
                padLeft(sourceBefore("=~"), Ruby.Binary.Type.Match),
                convert(node.getValueNode()),
                null
        );
    }

    @Override
    public J visitModuleNode(ModuleNode node) {
        return new Ruby.Module(
                randomId(),
                sourceBefore("module"),
                Markers.EMPTY,
                convertIdentifier(node.getCPath().getName()),
                new J.Block(
                        randomId(),
                        whitespace(),
                        Markers.EMPTY,
                        JRightPadded.build(false),
                        convertBlockStatements(node.getBodyNode(), n -> EMPTY),
                        sourceBefore("end")
                )
        );
    }

    @Override
    public J visitMultipleAsgnNode(MultipleAsgnNode node) {
        Space prefix = whitespace();
        JContainer<Expression> assignments = convertArgs("(", node.getPre(), null, ")");
        if (node.getRest() != null) {
            Space lastComma = assignments.getMarkers().findFirst(TrailingComma.class)
                    .map(TrailingComma::getSuffix).orElse(EMPTY);
            assignments = assignments.withMarkers(assignments.getMarkers().removeByType(TrailingComma.class));
            assignments = assignments.getPadding().withElements(ListUtils.concat(
                    ListUtils.mapLast(assignments.getPadding().getElements(), assign -> assign.withAfter(lastComma)),
                    padRight(convert(node.getRest()), EMPTY)
            ));
        }
        Space initializerPrefix = sourceBefore("=");
        Space firstArgPrefix = whitespace();
        JContainer<Expression> initializers =
                source.startsWith("[", cursor) ?
                        JContainer.build(initializerPrefix, singletonList(padRight(visitArrayNode(
                                (ArrayNode) node.getValueNode()).withPrefix(firstArgPrefix), EMPTY)), Markers.EMPTY) :
                        JContainer.build(
                                prefix,
                                ListUtils.mapFirst(
                                        this.<Expression>convertArgs("[", node.getValueNode(), null, "]").getPadding().getElements(),
                                        arg -> arg.withElement(arg.getElement().withPrefix(firstArgPrefix))
                                ),
                                Markers.EMPTY
                        ).withBefore(initializerPrefix);
        return new Ruby.MultipleAssignment(
                randomId(),
                prefix,
                Markers.EMPTY,
                assignments,
                initializers,
                null
        );
    }

    @Override
    public J visitNewlineNode(NewlineNode node) {
        throw new UnsupportedOperationException("Newlines are handled elsewhere.");
    }

    @Override
    public J visitNextNode(NextNode node) {
        return new J.Continue(
                randomId(),
                sourceBefore("next"),
                Markers.EMPTY,
                null
        );
    }

    @Override
    public J visitNilNode(NilNode node) {
        Space prefix = whitespace();
        return source.startsWith("nil", cursor) ?
                convertIdentifier("nil").withPrefix(prefix) :
                new J.Empty(randomId(), prefix, Markers.EMPTY);
    }

    @Override
    public J visitNilRestArgNode(NilRestArgNode node) {
        return convertIdentifier("**nil");
    }

    @Override
    public J visitNthRefNode(NthRefNode node) {
        return convertIdentifier("$" + node.getMatchNumber());
    }

    @Override
    public J visitOpAsgnNode(OpAsgnNode node) {
        Supplier<Expression> receiver = () -> {
            Expression r = convert(node.getReceiverNode());
            if (node.getVariableName() != null) {
                r = new J.FieldAccess(
                        randomId(),
                        r.getPrefix(),
                        Markers.EMPTY,
                        r.withPrefix(EMPTY),
                        padLeft(sourceBefore("."), convertIdentifier(node.getVariableName())),
                        null
                );
            }
            return r;
        };
        return convertOpAsgn(receiver, node.getOperatorName(), node.getValueNode());
    }

    @Override
    public J visitOpAsgnAndNode(OpAsgnAndNode node) {
        return convertOpAsgn(() -> convert(node.getFirstNode()), "&&", node.getSecondNode());
    }

    @Override
    public J visitOpAsgnOrNode(OpAsgnOrNode node) {
        return convertOpAsgn(() -> convert(node.getFirstNode()), "||", node.getSecondNode());
    }

    @Override
    public J visitOpElementAsgnNode(OpElementAsgnNode node) {
        return convertOpAsgn(
                () -> convertArrayAccess(node.getReceiverNode(), node.getArgsNode(), null),
                node.getOperatorName(),
                node.getValueNode()
        );
    }

    private Expression convertOpAsgn(Supplier<Expression> first, String op, Node second) {
        Expression firstExpr = first.get();
        Space prefix = firstExpr.getPrefix();
        firstExpr = firstExpr.withPrefix(EMPTY);

        Object opType = convertAssignmentOpType(op);

        if (second instanceof LocalAsgnNode) {
            second = ((LocalAsgnNode) second).getValueNode();
        } else if (second instanceof InstAsgnNode) {
            second = ((InstAsgnNode) second).getValueNode();
        }

        if (opType instanceof Ruby.AssignmentOperation.Type) {
            return new Ruby.AssignmentOperation(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    firstExpr,
                    padLeft(sourceBefore(op + "="), (Ruby.AssignmentOperation.Type) opType),
                    convert(second),
                    null
            );
        } else {
            return new J.AssignmentOperation(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    firstExpr,
                    padLeft(sourceBefore(op + "="), (J.AssignmentOperation.Type) opType),
                    convert(second),
                    null
            );
        }
    }

    @Override
    public Expression visitOperatorCallNode(OperatorCallNode node) {
        String op = node.getName().asJavaString();
        if (op.endsWith("@")) {
            op = op.substring(0, op.length() - 1);
        }
        Markers markers = Markers.EMPTY;
        J.Binary.Type type = null;
        J.Unary.Type unaryType = null;
        Ruby.Binary.Type rubyType = null;
        switch (op) {
            case "+":
                if (node.getArgsNode() == null) {
                    unaryType = J.Unary.Type.Positive;
                } else {
                    type = J.Binary.Type.Addition;
                }
                break;
            case "-":
                if (node.getArgsNode() == null) {
                    unaryType = J.Unary.Type.Negative;
                } else {
                    type = J.Binary.Type.Subtraction;
                }
                break;
            case "*":
                type = J.Binary.Type.Multiplication;
                break;
            case "/":
                type = J.Binary.Type.Division;
                break;
            case "%":
                type = J.Binary.Type.Modulo;
                break;
            case "**":
                rubyType = Ruby.Binary.Type.Exponentiation;
                break;
            case ">>":
                type = J.Binary.Type.RightShift;
                break;
            case "<<":
                type = J.Binary.Type.LeftShift;
                break;
            case "&":
                type = J.Binary.Type.BitAnd;
                break;
            case "|":
                type = J.Binary.Type.BitOr;
                break;
            case "^":
                type = J.Binary.Type.BitXor;
                break;
            case "~":
                unaryType = J.Unary.Type.Complement;
                break;
            case "==":
                type = J.Binary.Type.Equal;
                break;
            case "===":
                rubyType = Ruby.Binary.Type.Within;
                break;
            case "!=":
                type = J.Binary.Type.NotEqual;
                break;
            case "<=>":
                rubyType = Ruby.Binary.Type.Comparison;
                break;
            case "<":
                type = J.Binary.Type.LessThan;
                break;
            case "<=":
                type = J.Binary.Type.LessThanOrEqual;
                break;
            case ">":
                type = J.Binary.Type.GreaterThan;
                break;
            case ">=":
                type = J.Binary.Type.GreaterThanOrEqual;
                break;
            case "!":
                unaryType = J.Unary.Type.Not;
                if (source.startsWith("not", cursor)) {
                    op = "not";
                    markers = Markers.EMPTY.add(new EnglishOperator(randomId()));
                }
                break;
            default:
                throw new UnsupportedOperationException("Operator " + op + " not yet implemented");
        }

        if (type != null) {
            return new J.Binary(
                    randomId(),
                    whitespace(),
                    markers,
                    convert(node.getReceiverNode()),
                    padLeft(sourceBefore(op), type),
                    convert(node.getArgsNode().childNodes().get(0)),
                    null
            );
        } else if (unaryType != null) {
            return new J.Unary(
                    randomId(),
                    whitespace(),
                    markers,
                    padLeft(sourceBefore(op), unaryType),
                    convert(node.getReceiverNode()),
                    null
            );
        } else {
            return new Ruby.Binary(
                    randomId(),
                    whitespace(),
                    markers,
                    convert(node.getReceiverNode()),
                    padLeft(sourceBefore(op), rubyType),
                    convert(node.getArgsNode().childNodes().get(0)),
                    null
            );
        }
    }

    @Override
    public J visitOptArgNode(OptArgNode node) {
        return new J.VariableDeclarations(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                emptyList(),
                emptyList(),
                null,
                null,
                emptyList(),
                singletonList(padRight(new J.VariableDeclarations.NamedVariable(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        convertIdentifier(node.getName()),
                        emptyList(),
                        node.getValue() == null ?
                                null :
                                padLeft(sourceBefore("="), convert(((LocalAsgnNode) node.getValue()).getValueNode())),
                        null
                ), EMPTY))
        );
    }

    @Override
    public J visitOrNode(OrNode node) {
        Space prefix = whitespace();
        Expression left = convert(node.getFirstNode());
        Space opPrefix = whitespace();
        String op = source.startsWith("||", cursor) ? "||" : "or";
        skip(op);
        return new J.Binary(
                randomId(),
                prefix,
                op.equals("||") ? Markers.EMPTY : Markers.EMPTY.add(new EnglishOperator(randomId())),
                left,
                padLeft(opPrefix, J.Binary.Type.Or),
                convert(node.getSecondNode()),
                null
        );
    }

    @Override
    public J visitPatternCaseNode(PatternCaseNode node) {
        return convertCase(node.getCaseNode(), node.getCases(), node.getElseNode(), true);
    }

    @Override
    public J visitPostExeNode(PostExeNode node) {
        Space prefix = sourceBefore("END");
        return new Ruby.End(
                randomId(),
                prefix,
                Markers.EMPTY,
                convertBeginEndBlock(node)
        );
    }

    @Override
    public J visitPreExeNode(PreExeNode node) {
        Space prefix = sourceBefore("BEGIN");
        return new Ruby.Begin(
                randomId(),
                prefix,
                Markers.EMPTY,
                convertBeginEndBlock(node)
        );
    }

    private J.Block convertBeginEndBlock(IterNode node) {
        return new J.Block(
                randomId(),
                sourceBefore("{"),
                Markers.EMPTY,
                JRightPadded.build(false),
                convertBlockStatements(node.getBodyNode(), n -> EMPTY),
                sourceBefore("}")
        );
    }

    @Override
    public J visitRationalNode(RationalNode node) {
        return new Ruby.NumericDomain(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                padRight(convert(node.getNumerator()), sourceBefore("r")),
                Ruby.NumericDomain.Domain.Rational,
                null
        );
    }

    @Override
    public J visitRedoNode(RedoNode node) {
        return new Ruby.Redo(
                randomId(),
                sourceBefore("redo"),
                Markers.EMPTY
        );
    }

    @Override
    public J visitRegexpNode(RegexpNode node) {
        return ((Ruby.DelimitedString) convertStrings(new StrNode(node.getLine(), node.getValue())))
                .withRegexpOptions(convertRegexOptions(node.getOptions()));
    }

    @Override
    public J visitRequiredKeywordArgumentValueNode(RequiredKeywordArgumentValueNode node) {
        throw new UnsupportedOperationException("This is only used in the event of syntax errors.");
    }

    @Override
    public J visitRescueBodyNode(RescueBodyNode node) {
        throw new UnsupportedOperationException("RescueBodyNode is a recursive data structure that is " +
                                                "handled by visitRescueNode and so should never be called.");
    }

    @Override
    public J visitRescueNode(RescueNode node) {
        Space prefix = whitespace();
        if (source.startsWith("begin", cursor)) {
            skip("begin");
        }
        Space bodyPrefix = whitespace();
        J.Block body = visitBlock(node.getBodyNode());
        List<J.Try.Catch> catches = convertCatches(node.getRescueNode(), emptyList());

        J.Block elseBlock = null;
        if (node.getElseNode() != null) {
            elseBlock = new J.Block(
                    randomId(),
                    sourceBefore("else"),
                    Markers.EMPTY,
                    JRightPadded.build(false),
                    convertBlockStatements(node.getElseNode(), n -> EMPTY),
                    whitespace()
            );
        }

        if (elseBlock == null) {
            // whitespace rather than sourceBefore("end") because an ensure may follow
            catches = ListUtils.mapLast(catches, c -> c.withBody(c.getBody().withEnd(whitespace())));
        }
        if (source.startsWith("end", cursor)) {
            skip("end");
        }

        return new Ruby.Rescue(
                randomId(),
                prefix,
                Markers.EMPTY,
                new J.Try(
                        randomId(),
                        bodyPrefix,
                        Markers.EMPTY,
                        JContainer.empty(),
                        body,
                        catches,
                        null
                ),
                elseBlock
        );
    }

    private List<J.Try.Catch> convertCatches(@Nullable RescueBodyNode rescue, List<J.Try.Catch> mappedRescues) {
        if (rescue == null) {
            return mappedRescues;
        }
        // delay allocation until at least one rescue is found
        if (mappedRescues.isEmpty()) {
            mappedRescues = new ArrayList<>(3);
        }

        Space prefix = sourceBefore("rescue");
        Space exceptionVariablePrefix = whitespace();

        List<JRightPadded<J>> exceptionTypes = convertAll(
                Arrays.asList((((ArrayNode) rescue.getExceptionNodes()).children())),
                n -> sourceBefore(","), n -> EMPTY);

        List<JRightPadded<J.VariableDeclarations.NamedVariable>> names = new ArrayList<>(1);
        List<JRightPadded<Statement>> catchBody;
        Space beforeExceptionNamePrefix = whitespace();
        Space bodyPrefix = beforeExceptionNamePrefix;
        if (source.startsWith("=>", cursor)) {
            skip("=>");
            BlockNode body = (BlockNode) rescue.getBodyNode();

            // because the exceptionName is being assigned to $!
            J.Identifier exceptionName = convertIdentifier(((INameNode) body.get(0)).getName());
            names.add(padRight(new J.VariableDeclarations.NamedVariable(
                    randomId(),
                    beforeExceptionNamePrefix,
                    Markers.EMPTY,
                    exceptionName,
                    emptyList(),
                    null,
                    null
            ), EMPTY));

            bodyPrefix = whitespace();
            List<Node> bodyNodes = Arrays.asList(body.children());
            bodyNodes = bodyNodes.subList(1, bodyNodes.size());
            catchBody = convertAll(bodyNodes, n -> EMPTY, n -> EMPTY);
        } else if (rescue.getBodyNode() instanceof BlockNode) {
            catchBody = convertAll(Arrays.asList(((BlockNode) rescue.getBodyNode()).children()),
                    n -> EMPTY, n -> EMPTY);
        } else {
            catchBody = singletonList(padRight(convert(rescue.getBodyNode()), EMPTY));
        }

        TypeTree exceptionType;
        if (exceptionTypes.size() == 1) {
            exceptionType = (TypeTree) exceptionTypes.get(0).getElement();
        } else {
            //noinspection unchecked
            exceptionType = new J.MultiCatch(randomId(), EMPTY, Markers.EMPTY,
                    exceptionTypes.stream().map(t -> (JRightPadded<NameTree>)
                            (JRightPadded<?>) t).collect(toList()));
        }

        J.VariableDeclarations exceptionVariable = new J.VariableDeclarations(
                randomId(),
                exceptionVariablePrefix,
                Markers.EMPTY,
                emptyList(),
                emptyList(),
                exceptionType,
                null,
                emptyList(),
                names
        );

        mappedRescues.add(new J.Try.Catch(
                randomId(),
                prefix,
                Markers.EMPTY,
                new J.ControlParentheses<>(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        padRight(exceptionVariable, EMPTY)
                ),
                new J.Block(
                        randomId(),
                        bodyPrefix,
                        Markers.EMPTY,
                        JRightPadded.build(false),
                        catchBody,
                        EMPTY
                )
        ));

        return convertCatches(rescue.getOptRescueNode(), mappedRescues);
    }

    @Override
    public J visitRestArgNode(RestArgNode node) {
        return new J.VariableDeclarations(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                emptyList(),
                emptyList(),
                null,
                sourceBefore("*"),
                emptyList(),
                singletonList(padRight(new J.VariableDeclarations.NamedVariable(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        convertIdentifier(node.getName()),
                        emptyList(),
                        null,
                        null
                ), EMPTY))
        );
    }

    @Override
    public J visitRetryNode(RetryNode node) {
        return new Ruby.Retry(
                randomId(),
                sourceBefore("retry"),
                Markers.EMPTY
        );
    }

    @Override
    public J visitReturnNode(ReturnNode node) {
        Space prefix = sourceBefore("return");
        Expression returnValue = convert(node.getValueNode());
        return new J.Return(
                randomId(),
                prefix,
                Markers.EMPTY,
                returnValue
        );
    }

    @Override
    public Ruby.CompilationUnit visitRootNode(RootNode node) {
        return new Ruby.CompilationUnit(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                sourcePath,
                fileAttributes,
                charset,
                charsetBomMarked,
                null,
                convertBlockStatements(node.getBodyNode(), n -> whitespace())
        );
    }

    @Override
    public J visitSClassNode(SClassNode node) {
        return new Ruby.OpenEigenclass(
                randomId(),
                sourceBefore("class"),
                Markers.EMPTY,
                padLeft(sourceBefore("<<"), convert(node.getReceiverNode())),
                new J.Block(
                        randomId(),
                        whitespace(),
                        Markers.EMPTY,
                        JRightPadded.build(false),
                        convertBlockStatements(node.getBodyNode(), n -> EMPTY),
                        sourceBefore("end")
                )
        );
    }

    @Override
    public J visitSelfNode(SelfNode node) {
        return convertIdentifier("self");
    }

    @Override
    public J visitSplatNode(SplatNode node) {
        return new Ruby.Splat(
                randomId(),
                sourceBefore("*"),
                Markers.EMPTY,
                convert(node.getValue())
        );
    }

    @Override
    public J visitStarNode(StarNode node) {
        return convertIdentifier("*");
    }

    @Override
    public J visitStrNode(StrNode node) {
        return convertStrings(node);
    }

    /**
     * @param nodes An array of either {@link StrNode} or {@link DNode}.
     * @return A {@link Ruby.DelimitedString}, {@link J.Literal}, or {@link Ruby.DelimitedArray} node.
     */
    public J convertStrings(Node... nodes) {
        Object parentValue = this.nodes.getParentOrThrow().getValue();
        boolean inDString = parentValue instanceof DStrNode || parentValue instanceof DXStrNode ||
                            parentValue instanceof DRegexpNode;
        Space prefix = inDString ? EMPTY : whitespace();
        String delimiter = "";
        if (!inDString) {
            if (source.charAt(cursor) == '%') {
                switch (source.charAt(cursor + 1)) {
                    case 'q':
                    case 'Q':
                    case 'w':
                    case 'W':
                    case 'x':
                    case 'r':
                        delimiter = source.substring(cursor, cursor + 3);
                        break;
                    default:
                        // the solo % case
                        delimiter = source.substring(cursor, cursor + 2);
                }
            } else {
                delimiter = source.substring(cursor, cursor + 1);
            }
        }
        skip(delimiter);

        J stringly;
        if (delimiter.startsWith("%w") || delimiter.startsWith("%W")) {
            List<JRightPadded<Expression>> strings = new ArrayList<>(nodes.length);
            for (Node node : nodes) {
                if (node instanceof StrNode) {
                    strings.add(padRight(convertStringLiteral((StrNode) node, delimiter, true), whitespace()));
                } else {
                    strings.add(padRight(convertDelimitedString((DNode) nodes[0], ""), whitespace()));
                }
            }
            stringly = new Ruby.DelimitedArray(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    delimiter,
                    JContainer.build(EMPTY, strings, Markers.EMPTY),
                    null
            );
        } else if (nodes[0] instanceof StrNode) {
            boolean isRegex = delimiter.startsWith("%r") || delimiter.startsWith("/");
            stringly = convertStringLiteral((StrNode) nodes[0], isRegex || inDString ? "" : delimiter, false);
            if (isRegex) {
                stringly = new Ruby.DelimitedString(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        delimiter,
                        singletonList(stringly),
                        emptyList(),
                        null
                );
            }
        } else if (nodes[0] instanceof DNode) {
            stringly = convertDelimitedString((DNode) nodes[0], delimiter);
        } else {
            throw new UnsupportedOperationException("Unexpected string node type " + nodes[0].getClass().getSimpleName());
        }

        skip(DelimiterMatcher.end(delimiter));
        return stringly.withPrefix(prefix);
    }

    private Ruby.DelimitedString convertDelimitedString(DNode node, String delimiter) {
        List<J> strings = new ArrayList<>(node.size());
        for (Node n : node) {
            if (!(n instanceof StrNode) || !((StrNode) n).getValue().isEmpty()) {
                strings.add(convert(n));
            }
        }
        return new Ruby.DelimitedString(
                randomId(),
                EMPTY,
                Markers.EMPTY,
                delimiter,
                strings,
                emptyList(),
                null
        );
    }

    /**
     * @param node           The string node to convert.
     * @param delimiter      A delimiter for this string. The cursor has already advanced beyond this delimiter.
     * @param inArrayLiteral If we are in an array literal, there may be no delimiter use.
     * @return Either a {@link J.Literal} or a {@link Ruby.Binary} with an implicit concatenation type.
     */
    private Expression convertStringLiteral(StrNode node, String delimiter, boolean inArrayLiteral) {
        String value = node.getValue().toString();
        String endDelimiter = DelimiterMatcher.end(delimiter);
        if (delimiter.equals("?")) {
            return new J.Literal(
                    randomId(),
                    EMPTY,
                    Markers.EMPTY,
                    value,
                    "?" + value,
                    null,
                    JavaType.Primitive.String
            );
        } else if (inArrayLiteral || endDelimiter.isEmpty()) {
            skip(value);
            return new J.Literal(
                    randomId(),
                    EMPTY,
                    Markers.EMPTY,
                    value,
                    value,
                    null,
                    JavaType.Primitive.String
            );
        } else if (node.getValue().isEmpty()) {
            return new J.Literal(
                    randomId(),
                    EMPTY,
                    Markers.EMPTY,
                    value,
                    String.format("%s%s%s", delimiter, value, endDelimiter),
                    null,
                    JavaType.Primitive.String
            );
        }

        // deal with the possibility of implicit concatenations
        char endDelimiterChar = endDelimiter.charAt(0);
        Map<J.Literal, Space> strings = new LinkedHashMap<>(1);
        Space beforeOperator = null;
        StringBuilder valueSrc = new StringBuilder();
        char[] valueArr = value.toCharArray();
        for (int i = 0; i < valueArr.length; ) {
            char c = source.charAt(cursor);
            char last = source.charAt(cursor - 1);
            cursor++;
            if (c == endDelimiterChar && last != '\'') {
                strings.put(new J.Literal(
                        randomId(),
                        EMPTY,
                        Markers.EMPTY,
                        valueSrc,
                        String.format("%s%s%s", delimiter, valueSrc, endDelimiter),
                        null,
                        JavaType.Primitive.String
                ), beforeOperator);
                beforeOperator = sourceBefore(delimiter);
                valueSrc.setLength(0);
            } else {
                valueSrc.append(c);
                i++;
                if (i == valueArr.length) {
                    strings.put(new J.Literal(
                            randomId(),
                            EMPTY,
                            Markers.EMPTY,
                            valueSrc,
                            String.format("%s%s%s", delimiter, valueSrc, endDelimiter),
                            null,
                            JavaType.Primitive.String
                    ), beforeOperator);
                    break;
                }
            }
        }

        Expression combined = null;
        for (Map.Entry<J.Literal, Space> literal : strings.entrySet()) {
            if (combined == null) {
                combined = literal.getKey();
            } else {
                combined = new Ruby.Binary(
                        randomId(),
                        combined.getPrefix(),
                        Markers.EMPTY,
                        combined.withPrefix(EMPTY),
                        JLeftPadded.build(Ruby.Binary.Type.ImplicitStringConcatenation)
                                .withBefore(literal.getValue()),
                        literal.getKey(),
                        null
                );
            }
        }

        return requireNonNull(combined);
    }

    @Override
    public J visitSuperNode(SuperNode node) {
        return new J.MethodInvocation(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                null,
                null,
                convertIdentifier("super"),
                convertCallArgs(new SuperArgsNode(node)),
                null
        );
    }

    /**
     * Because {@link SuperNode} does not implement {@link IArgumentNode}.
     */
    private static class SuperArgsNode extends SuperNode implements IArgumentNode {
        public SuperArgsNode(SuperNode superNode) {
            super(superNode.getLine(), superNode.getArgsNode(), superNode.getIterNode());
        }

        @Override
        public Node setArgsNode(Node node) {
            throw new UnsupportedOperationException("Setter will never be called");
        }
    }

    @Override
    public J visitSValueNode(SValueNode node) {
        // https://www.rubydoc.info/gems/ruby-internal/Node/SVALUE
        throw new UnsupportedOperationException("Does not appear to be called even for SVALUEs as described " +
                                                "in the Ruby documentation.");
    }

    @Override
    public J visitSymbolNode(SymbolNode node) {
        return convertSymbols(node);
    }

    /**
     * @param nodes An array of either {@link SymbolNode} or {@link DSymbolNode}.
     * @return A {@link Ruby.Symbol} or a {@link Ruby.DelimitedArray} node.
     */
    public Expression convertSymbols(Node... nodes) {
        Space prefix = whitespace();

        String delimiter;
        boolean explicitColon = false;
        if (source.startsWith("%", cursor)) { // %s or %i
            delimiter = source.substring(cursor, cursor + 3);
        } else {
            if (source.startsWith(":", cursor)) {
                explicitColon = true;
                skip(":");
            }
            if (nodes[0] instanceof SymbolNode) {
                RubySymbol firstName = ((SymbolNode) nodes[0]).getName();
                delimiter = source.startsWith(firstName.asJavaString(), cursor) ?
                        "" : source.substring(cursor, cursor + 1);
            } else {
                delimiter = source.substring(cursor, cursor + 1);
            }
        }
        skip(delimiter);

        List<JRightPadded<Expression>> nameNodes = new ArrayList<>(nodes.length);
        if (delimiter.startsWith("%i") || delimiter.startsWith("%I")) {
            // whitespace is only trimmed around symbol array names
            for (Node node : nodes) {
                nameNodes.add(padRight(
                        node instanceof SymbolNode ?
                                convertIdentifier(((SymbolNode) node).getName()) :
                                convert(((DSymbolNode) node).children()[0]), whitespace()));
            }
        } else if (nodes[0] instanceof SymbolNode) {
            // whitespace on the end of non-array name symbols is actually part of the name...
            nameNodes.add(padRight(convertIdentifier(((SymbolNode) nodes[0]).getName()), EMPTY));
        } else { // instanceof DSymbolNode
            nameNodes.add(padRight(convert((((DSymbolNode) nodes[0]).children()[0])), whitespace()));
        }

        if (delimiter.startsWith("%")) {
            skip(DelimiterMatcher.end(delimiter));
        }

        if (delimiter.startsWith("%i") || delimiter.startsWith("%I")) {
            return new Ruby.DelimitedArray(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    delimiter,
                    JContainer.build(EMPTY, nameNodes, Markers.EMPTY),
                    null
            );
        } else {
            return new Ruby.Symbol(
                    randomId(),
                    prefix,
                    Markers.EMPTY,
                    (explicitColon ? ":" : "") + delimiter,
                    nameNodes.get(0).getElement(),
                    null
            );
        }
    }

    @Override
    public J visitTrueNode(TrueNode node) {
        return new J.Literal(randomId(), sourceBefore("true"), Markers.EMPTY, true, "true",
                null, JavaType.Primitive.Boolean);
    }

    @Override
    public J visitVAliasNode(VAliasNode node) {
        return new Ruby.Alias(
                randomId(),
                sourceBefore("alias"),
                Markers.EMPTY,
                convertIdentifier(node.getNewName()),
                convertIdentifier(node.getOldName())
        );
    }

    @Override
    public J visitVCallNode(VCallNode node) {
        return convertIdentifier(node.getName());
    }

    @Override
    public J visitWhenNode(WhenNode node) {
        throw new UnsupportedOperationException("Multiple WhenNodes can potentially map to one J.Case, " +
                                                "so the grouping of WhenNodes is handled in visitCaseNode " +
                                                "and this method should never be called.");
    }

    @Override
    public J visitWhileNode(WhileNode node) {
        return whileOrUntilNode(node.getConditionNode(), node.getBodyNode());
    }

    @Override
    public J visitUntilNode(UntilNode node) {
        return whileOrUntilNode(node.getConditionNode(), node.getBodyNode());
    }

    private J whileOrUntilNode(Node conditionNode, Node bodyNode) {
        Space prefix = whitespace();

        if (source.startsWith("while", cursor) || source.startsWith("until", cursor)) {
            Markers markers = whileOrUntil();
            Space conditionPrefix = whitespace();
            Expression conditionExpr = convert(conditionNode);
            boolean explicitDo = Pattern.compile("\\s+do").matcher(source).find(cursor);
            J.ControlParentheses<Expression> condition = new J.ControlParentheses<>(
                    randomId(),
                    conditionPrefix,
                    explicitDo ?
                            Markers.EMPTY.add(new ExplicitDo(randomId())) :
                            Markers.EMPTY,
                    padRight(conditionExpr, explicitDo ? sourceBefore("do") : EMPTY)
            );

            return new J.WhileLoop(
                    randomId(),
                    prefix,
                    markers,
                    condition,
                    padRight(convert(bodyNode), sourceBefore("end"))
            );
        } else {
            JRightPadded<Statement> body = padRight(convert(bodyNode), whitespace());
            Markers markers = whileOrUntil();

            Space conditionPrefix = whitespace();
            Expression conditionExpr = convert(conditionNode);
            J.ControlParentheses<Expression> condition = new J.ControlParentheses<>(
                    randomId(),
                    conditionPrefix,
                    Markers.EMPTY,
                    padRight(conditionExpr, EMPTY)
            );

            return new J.WhileLoop(
                    randomId(),
                    prefix,
                    markers.add(new WhileModifier(randomId())),
                    condition,
                    body
            );
        }
    }

    private Markers whileOrUntil() {
        Markers markers = Markers.EMPTY;
        if (source.startsWith("until", cursor)) {
            markers = markers.add(new Until(randomId()));
            skip("until");
        } else {
            skip("while");
        }
        return markers;
    }

    @Override
    public J visitXStrNode(XStrNode node) {
        Space prefix = whitespace();
        String value = node.getValue().toString();
        String delimiter = source.charAt(cursor) == '`' ? "`" : source.substring(cursor, cursor + 3);
        skip(delimiter);
        skip(value);
        skip(DelimiterMatcher.end(delimiter));
        return new Ruby.DelimitedString(
                randomId(),
                prefix,
                Markers.EMPTY,
                delimiter,
                singletonList(
                        new J.Literal(
                                randomId(),
                                EMPTY,
                                Markers.EMPTY,
                                value,
                                value,
                                null,
                                JavaType.Primitive.String
                        )
                ),
                emptyList(),
                null
        );
    }

    @Override
    public J visitYieldNode(YieldNode node) {
        return new Ruby.Yield(
                randomId(),
                sourceBefore("yield"),
                Markers.EMPTY,
                convertArgs("(", node.getArgsNode(), null, ")")
        );
    }

    @Override
    public J visitZArrayNode(ZArrayNode node) {
        return new Ruby.Array(
                randomId(),
                sourceBefore("["),
                Markers.EMPTY,
                JContainer.<Expression>empty().withBefore(sourceBefore("]")),
                null
        );
    }

    @Override
    public J visitZSuperNode(ZSuperNode node) {
        return new J.MethodInvocation(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                null,
                null,
                convertIdentifier("super"),
                JContainer.<Expression>empty().withMarkers(Markers.EMPTY.add(new OmitParentheses(randomId()))),
                null
        );
    }

    private J.Identifier convertIdentifier(RubySymbol name) {
        return convertIdentifier(name.asJavaString());
    }

    private J.Identifier convertIdentifier(String name) {
        return new J.Identifier(
                randomId(),
                sourceBefore(name),
                Markers.EMPTY,
                emptyList(),
                name,
                null,
                null
        );
    }

    private <J2 extends J> J2 convert(@Nullable Node t) {
        if (t == null) {
            //noinspection ConstantConditions
            return null;
        }
        nodes = new Cursor(nodes, t);
        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            //noinspection unchecked
            J2 j = (J2) t.accept(this);
            nodes = nodes.getParentOrThrow();
            return j;
        } catch (Throwable ex) {
            throw ex; // nice debug breakpoint
        }
    }

    private <J2 extends J> JRightPadded<J2> convert(Node t, Function<Node, Space> suffix) {
        J2 j = convert(t);
        //noinspection ConstantConditions
        return j == null ? null : new JRightPadded<>(j, suffix.apply(t), Markers.EMPTY);
    }

    private <J2 extends J> JContainer<J2> convertArgs(String before, @Nullable Node argsNode,
                                                      @Nullable Node iterNode,
                                                      String after) {
        AtomicReference<Markers> markers = new AtomicReference<>(Markers.EMPTY);
        Space prefix = whitespace();
        boolean omitParentheses;
        if (source.startsWith(before, cursor)) {
            skip(before);
            omitParentheses = false;
        } else {
            markers.set(markers.get().add(new OmitParentheses(randomId())));
            omitParentheses = true;
        }

        List<Node> args;
        if (argsNode == null) {
            args = new ArrayList<>(1);
        } else if (argsNode instanceof ListNode) {
            ListNode listNode = (ListNode) argsNode;
            args = new ArrayList<>(listNode.size());
            for (Node node : listNode.children()) {
                if (node != null) {
                    args.add(node);
                }
            }
        } else if (argsNode instanceof ArgsNode) {
            ArgsNode argsArgsNode = (ArgsNode) argsNode;
            args = new ArrayList<>(argsArgsNode.getArgs().length + 1);
            Collections.addAll(args, argsArgsNode.getArgs());
            if (argsArgsNode.getBlock() != null) {
                args.add(argsArgsNode.getBlock());
            }
            if (argsArgsNode.getRestArgNode() != null) {
                args.add(argsArgsNode.getRestArgNode());
            }
        } else {
            args = new ArrayList<>(2);
            args.add(argsNode);
        }

        List<JRightPadded<J2>> mappedArgs = convertAll(args, n -> sourceBefore(","), n -> {
            int cursorBeforeWhitespace = cursor;
            Space next = whitespace();
            if (cursor < source.length() && source.charAt(cursor) == ',') {
                markers.set(markers.get().add(new TrailingComma(randomId(), next)));
                cursor++;
            } else {
                cursor = cursorBeforeWhitespace;
            }
            return omitParentheses ? EMPTY : sourceBefore(after);
        });

        if (iterNode != null) {
            J2 blockPass = convert(iterNode);
            Space suffix = EMPTY;
            if (blockPass instanceof Ruby.BlockArgument) {
                suffix = omitParentheses ? EMPTY : sourceBefore(after);
            }
            mappedArgs = ListUtils.concat(
                    mappedArgs,
                    padRight(blockPass, suffix)
            );
        }

        return JContainer.build(
                prefix,
                mappedArgs,
                markers.get()
        );
    }

    private <J2 extends J> List<JRightPadded<J2>> convertAll(List<? extends Node> trees,
                                                             Function<Node, Space> innerSuffix,
                                                             Function<Node, Space> suffix) {
        if (trees.isEmpty()) {
            return emptyList();
        }
        List<JRightPadded<J2>> converted = new ArrayList<>(trees.size());
        for (int i = 0; i < trees.size(); i++) {
            converted.add(convert(trees.get(i), i == trees.size() - 1 ? suffix : innerSuffix));
        }
        return converted;
    }

    private Space sourceBefore(String untilDelim) {
        int delimIndex = positionOfNext(untilDelim);
        if (delimIndex < 0) {
            return EMPTY; // unable to find this delimiter
        }

        String prefix = source.substring(cursor, delimIndex);
        cursor += prefix.length() + untilDelim.length(); // advance past the delimiter
        return RubySpace.format(prefix);
    }

    private <T> JRightPadded<T> padRight(T tree, Space right) {
        return new JRightPadded<>(tree, right, Markers.EMPTY);
    }

    private <T> JLeftPadded<T> padLeft(Space left, T tree) {
        return new JLeftPadded<>(left, tree, Markers.EMPTY);
    }

    private int positionOfNext(String untilDelim) {
        boolean inMultiLineComment = false;
        boolean inSingleLineComment = false;

        int delimIndex = cursor;
        for (; delimIndex < source.length() - untilDelim.length() + 1; delimIndex++) {
            if (inSingleLineComment) {
                if (source.charAt(delimIndex) == '\n') {
                    inSingleLineComment = false;
                }
            } else {
                if (source.length() - untilDelim.length() > delimIndex + 1) {
                    if (source.charAt(delimIndex) == '#') {
                        inSingleLineComment = true;
                        delimIndex++;
                    } else {
                        if (source.startsWith("=begin\n", delimIndex) ||
                            source.startsWith("=begin\r\n", delimIndex)) {
                            inMultiLineComment = true;
                            delimIndex += "=begin".length();
                        } else if (source.startsWith("=end\n", delimIndex) ||
                                   source.startsWith("=end\r\n", delimIndex)) {
                            inMultiLineComment = false;
                            delimIndex += "=end".length();
                        }
                    }
                }

                if (!inMultiLineComment && !inSingleLineComment) {
                    if (source.startsWith(untilDelim, delimIndex)) {
                        break; // found it!
                    }
                }
            }
        }

        return delimIndex > source.length() - untilDelim.length() ? -1 : delimIndex;
    }

    private Space whitespace() {
        int next = indexOfNextNonWhitespace(cursor, source);
        String prefix = source.substring(cursor, next);
        cursor += prefix.length();
        return RubySpace.format(prefix);
    }

    private void skip(@Nullable String token) {
        if (token == null) {
            //noinspection ConstantConditions
            return;
        }
        if (source.startsWith(token, cursor)) {
            cursor += token.length();
        }
    }

    public static int indexOfNextNonWhitespace(int cursor, String source) {
        boolean inMultiLineComment = false;
        boolean inSingleLineComment = false;

        int length = source.length();
        for (; cursor < length; cursor++) {
            char current = source.charAt(cursor);
            if (inSingleLineComment) {
                inSingleLineComment = current != '\n';
                continue;
            } else if (length > cursor + 1) {
                if (current == '#') {
                    inSingleLineComment = true;
                    cursor++;
                    continue;
                } else if (cursor == 0 || cursor == source.length() - "=end".length() ||
                           source.charAt(cursor - 1) == '\n') {
                    if (source.startsWith("=begin", cursor)) {
                        inMultiLineComment = true;
                        cursor++;
                        continue;
                    } else if (source.startsWith("=end", cursor)) {
                        inMultiLineComment = false;
                        cursor += "=end".length() - 1; // the loop increment adds another 1
                        continue;
                    }
                }
            }
            if (!inMultiLineComment && !Character.isWhitespace(current)) {
                break; // found it!
            }
        }
        return cursor;
    }
}
