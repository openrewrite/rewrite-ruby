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
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.ruby.marker.*;
import org.openrewrite.ruby.tree.Ruby;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.internal.StringUtils.indexOfNextNonWhitespace;
import static org.openrewrite.java.tree.Space.EMPTY;
import static org.openrewrite.java.tree.Space.format;

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
    public J visitArgumentNode(ArgumentNode node) {
        return getIdentifier(node.getName());
    }

    @Override
    public J visitArrayNode(ArrayNode node) {
        Space prefix = whitespace();
        JContainer<Expression> elements = convertArgs("[", node, "]");
        return new Ruby.Array(
                randomId(),
                prefix,
                Markers.EMPTY,
                elements,
                null
        );
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
        J.Identifier name = getIdentifier(node.getName());
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

    private J.ArrayAccess convertArrayAccess(CallNode node) {
        Space prefix = whitespace();
        Expression receiver = convert(node.getReceiverNode());
        JContainer<J> args = convertArgs("[", node.getArgsNode(), "]");
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

    private List<JRightPadded<Statement>> convertBlockStatements(Node node, Function<Node, Space> suffix) {
        List<? extends Node> trees;
        if (node instanceof ListNode && !(node instanceof DNode)) {
            trees = Arrays.asList(((ListNode) node).children());
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
            //noinspection ReassignedVariable,unchecked
            converted.add((JRightPadded<Statement>) (JRightPadded<?>) stat);
        }
        return converted;
    }

    @Override
    public J visitCaseNode(CaseNode node) {
        Space prefix = sourceBefore("case");
        J.ControlParentheses<Expression> selector = new J.ControlParentheses<>(
                randomId(),
                EMPTY,
                Markers.EMPTY,
                padRight(convert(node.getCaseNode()), EMPTY)
        );

        Map<Integer, List<WhenNode>> groupedWhen = new LinkedHashMap<>();
        for (Node aCase : node.getCases()) {
            groupedWhen.computeIfAbsent(aCase.getLine(), k -> new ArrayList<>())
                    .add((WhenNode) aCase);
        }

        J.Block cases = new J.Block(
                randomId(),
                EMPTY,
                Markers.EMPTY,
                JRightPadded.build(false),
                groupedWhen.values().stream().map(this::convertCases).collect(toList()),
                node.getElseNode() == null ? sourceBefore("end") : EMPTY
        );

        if (node.getElseNode() != null) {
            Space elsePrefix = sourceBefore("else");
            JContainer<Statement> body = JContainer.build(
                    whitespace(),
                    convertBlockStatements(node.getElseNode(), n -> EMPTY),
                    Markers.EMPTY
            );
            cases = cases.getPadding().withStatements(ListUtils.concat(cases.getPadding().getStatements(),
                    padRight(new J.Case(
                            randomId(),
                            elsePrefix,
                            Markers.EMPTY,
                            J.Case.Type.Statement,
                            JContainer.empty(),
                            body,
                            null
                    ), EMPTY)));
            cases = cases.withEnd(sourceBefore("end"));
        }

        return new J.Switch(
                randomId(),
                prefix,
                Markers.EMPTY,
                selector,
                cases
        );
    }

    private JRightPadded<Statement> convertCases(List<WhenNode> whenNodes) {
        Space prefix = sourceBefore("when");

        List<Node> expressionNodes = new ArrayList<>();
        for (WhenNode node : whenNodes) {
            if (node.getExpressionNodes() instanceof ArrayNode) {
                Collections.addAll(expressionNodes, ((ArrayNode) node.getExpressionNodes()).children());
            } else {
                expressionNodes.add(node.getExpressionNodes());
            }
        }

        JContainer<Expression> expressions = JContainer.build(
                whitespace(),
                convertAll(expressionNodes, n -> sourceBefore(","), n -> EMPTY),
                Markers.EMPTY
        );

        return padRight(new J.Case(
                randomId(),
                prefix,
                Markers.EMPTY,
                J.Case.Type.Statement,
                expressions,
                JContainer.build(
                        whitespace(),
                        convertBlockStatements(whenNodes.get(0).getBodyNode(), n -> EMPTY),
                        Markers.EMPTY
                ),
                null
        ), EMPTY);
    }

    @Override
    public J visitClassNode(ClassNode node) {
        Space prefix = whitespace();
        skip("class");
        J.Identifier name = getIdentifier(node.getCPath().getName());

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
    public J visitColon2Node(Colon2Node node) {
        return new J.MemberReference(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                padRight(convert(node.getLeftNode()), sourceBefore("::")),
                null,
                padLeft(EMPTY, getIdentifier(node.getName())),
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
                padRight(new J.Empty(randomId(), EMPTY, Markers.EMPTY), sourceBefore("::")),
                null,
                padLeft(EMPTY, getIdentifier(node.getName())),
                null,
                null,
                null
        );
    }

    @Override
    public J visitConstNode(ConstNode node) {
        return getIdentifier(node.getName());
    }

    @Override
    public J visitDefnNode(DefnNode node) {
        return convertMethodDeclaration(node);
    }

    @Override
    public J visitDefsNode(DefsNode node) {
        return convertMethodDeclaration(node);
    }

    private Statement convertMethodDeclaration(MethodDefNode node) {
        Space prefix = sourceBefore("def");

        Expression classMethodReceiver = null;
        Space classMethodReceiverDot = null;
        if (node instanceof DefsNode) {
            classMethodReceiver = convert(((DefsNode) node).getReceiverNode());
            classMethodReceiverDot = sourceBefore(".");
        }

        J.MethodDeclaration.IdentifierWithAnnotations name = new J.MethodDeclaration.IdentifierWithAnnotations(
                getIdentifier(node.getName()),
                emptyList()
        );

        JContainer<J> args = convertArgs("(", node.getArgsNode(), ")");
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
        Space prefix = whitespace();
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
    }

    @Override
    public J visitDAsgnNode(DAsgnNode node) {
        return visitAsgnNode(node, node.getName());
    }

    @Override
    public J visitDRegxNode(DRegexpNode node) {
        return visitDNode(whitespace(), "/", node)
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
        return visitDNode(node);
    }

    @Override
    public J visitDVarNode(DVarNode node) {
        return getIdentifier(node.getName());
    }

    @Override
    public J visitDXStrNode(DXStrNode node) {
        return visitDNode(node);
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
    public J visitInstAsgnNode(InstAsgnNode node) {
        return visitAsgnNode(node, node.getName());
    }

    @Override
    public J visitInstVarNode(InstVarNode node) {
        return getIdentifier(node.getName());
    }

    @Override
    public J visitNilNode(NilNode node) {
        return new J.Empty(randomId(), EMPTY, Markers.EMPTY);
    }

    @Override
    public J visitRegexpNode(RegexpNode node) {
        DStrNode dstr = new DStrNode(0, node.getValue().getEncoding());
        dstr.add(new StrNode(node.getLine(), node.getValue()));
        return this.<Ruby.DelimitedString>convert(dstr).withRegexpOptions(
                convertRegexOptions(node.getOptions()));
    }

    private Ruby.DelimitedString visitDNode(DNode node) {
        Space prefix = whitespace();
        String delimiter = "\"";
        if (source.charAt(cursor) == '%') {
            switch (source.charAt(cursor + 1)) {
                case 'Q':
                case 'q':
                case 'x':
                case 'r':
                    // ex: %Q<is a string>
                    delimiter = source.substring(cursor, 3);
                    break;
                default:
                    // ex: %<is a string>
                    delimiter = source.substring(cursor, 2);
                    break;
            }
        } else if (source.charAt(cursor) == '/') {
            delimiter = "/";
        }
        return visitDNode(prefix, delimiter, node);
    }

    private Ruby.DelimitedString visitDNode(Space prefix, String delimiter, DNode node) {
        skip(delimiter);
        Ruby.DelimitedString dString = new Ruby.DelimitedString(
                randomId(),
                prefix,
                Markers.EMPTY,
                delimiter,
                StreamSupport.stream(node.spliterator(), false)
                        .filter(Objects::nonNull)
                        .filter(n -> !(n instanceof StrNode) || !((StrNode) n).getValue().isEmpty())
                        .map(n -> (J) convert(n))
                        .collect(toList()),
                emptyList(),
                null
        );
        skip(delimiter.substring(delimiter.length() - 1));
        return dString;
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
        J.Identifier name = getIdentifier(node.getName());
        J.MethodInvocation method = new J.MethodInvocation(
                randomId(),
                prefix,
                Markers.EMPTY,
                null,
                null,
                name,
                convertCallArgs(node),
                null
        );

        if (method.getSimpleName().equals("lambda")) {
            assert method.getArguments().size() == 2 : "lambda should have exactly one argument -- a block";
            Expression lastArg = method.getArguments().get(1);
            assert lastArg instanceof Ruby.Block : "lambda should have exactly one argument -- a block";

            Ruby.Block block = (Ruby.Block) lastArg;
            JContainer<J> parameters = block.getPadding().getParameters();
            return new J.Lambda(
                    method.getId(),
                    method.getPrefix(),
                    method.getMarkers(),
                    new J.Lambda.Parameters(
                            randomId(),
                            EMPTY,
                            Markers.EMPTY,
                            false,
                            parameters == null ? emptyList() : parameters.getPadding().getElements()
                    ),
                    method.getPadding().getArguments().getBefore(),
                    block.getBody(),
                    null
            );
        }

        return method;
    }

    private <T extends BlockAcceptingNode & IArgumentNode> JContainer<Expression> convertCallArgs(T node) {
        JContainer<Expression> args = convertArgs("(", node.getArgsNode(), ")");
        if (node.getIterNode() != null) {
            args = JContainer.withElements(args,
                    ListUtils.concat(args.getElements(), (Expression) convert(node.getIterNode())));
        }
        return args;
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
    public J visitGlobalVarNode(GlobalVarNode node) {
        return getIdentifier(node.getName());
    }

    @Override
    public J visitHashNode(HashNode node) {
        Space prefix = sourceBefore("{");

        List<JRightPadded<Ruby.KeyValue>> pairs = new ArrayList<>(node.getPairs().size());
        List<KeyValuePair<Node, Node>> nodePairs = node.getPairs();
        for (int i = 0; i < nodePairs.size(); i++) {
            KeyValuePair<Node, Node> kv = nodePairs.get(i);
            pairs.add(padRight(new Ruby.KeyValue(
                    randomId(),
                    whitespace(),
                    Markers.EMPTY,
                    convert(kv.getKey()),
                    padLeft(sourceBefore("=>"), convert(kv.getValue())),
                    null
            ), i == nodePairs.size() - 1 ? sourceBefore("}") : sourceBefore(",")));
        }

        return new Ruby.Hash(
                randomId(),
                prefix,
                Markers.EMPTY,
                JContainer.build(EMPTY, pairs, Markers.EMPTY),
                null
        );
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
    public J visitIterNode(IterNode node) {
        Space prefix = whitespace();
        J.Block body;
        JContainer<J> parameters = null;
        boolean inline = source.charAt(cursor) == '{';
        if (inline) {
            skip("{");
            if (!node.getArgsNode().isEmpty()) {
                parameters = convertArgs("|", node.getArgsNode(), "|");
            }
            body = visitBlock(node.getBodyNode());
            skip("}");
        } else {
            skip("do");
            if (!node.getArgsNode().isEmpty()) {
                parameters = convertArgs("|", node.getArgsNode(), "|");
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
    public J visitLocalAsgnNode(LocalAsgnNode node) {
        return visitAsgnNode(node, node.getName());
    }

    private Expression visitAsgnNode(AssignableNode node, RubySymbol name) {
        if (node.getValueNode() instanceof OperatorCallNode) {
            Space variablePrefix = whitespace();
            J.Identifier variable = getIdentifier(name);
            Space opPrefix = whitespace();
            OperatorCallNode assignOp = (OperatorCallNode) node.getValueNode();

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
                String op = assignOp.getName().asJavaString();
                J.AssignmentOperation.Type type;
                switch (op) {
                    case "+":
                        type = J.AssignmentOperation.Type.Addition;
                        break;
                    case "-":
                        type = J.AssignmentOperation.Type.Subtraction;
                        break;
                    case "*":
                        type = J.AssignmentOperation.Type.Multiplication;
                        break;
                    case "/":
                        type = J.AssignmentOperation.Type.Division;
                        break;
                    case "%":
                        type = J.AssignmentOperation.Type.Modulo;
                        break;
                    case "**":
                        type = J.AssignmentOperation.Type.Exponentiation;
                        break;
                    default:
                        throw new UnsupportedOperationException("Unsupported assignment operator " + op);
                }
                skip(op + "=");
                return new J.AssignmentOperation(
                        randomId(),
                        variablePrefix,
                        Markers.EMPTY,
                        variable,
                        padLeft(opPrefix, type),
                        convert(((ListNode) assignOp.getArgsNode()).get(0)),
                        null
                );
            }
        } else {
            Space prefix = whitespace();
            J.Identifier variable = getIdentifier(name);
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

    @Override
    public J visitLocalVarNode(LocalVarNode node) {
        return getIdentifier(node.getName());
    }

    @Override
    public J visitMultipleAsgnNode(MultipleAsgnNode node) {
        Space prefix = whitespace();
        JContainer<Expression> assignments = convertArgs("(", node.getPre(), ")");
        if (node.getRest() != null) {
            assignments = assignments.getPadding().withElements(ListUtils.concat(
                    ListUtils.mapLast(assignments.getPadding().getElements(), assign -> assign.withAfter(sourceBefore(","))),
                    padRight(
                            new Ruby.Expansion(
                                    randomId(),
                                    sourceBefore("*"),
                                    Markers.EMPTY,
                                    convert(node.getRest())
                            ),
                            EMPTY
                    )
            ));
        }
        Space initializerPrefix = sourceBefore("=");
        Space firstArgPrefix = whitespace();
        JContainer<Expression> initializers =
                source.startsWith("[", cursor) ?
                        JContainer.build(initializerPrefix, singletonList(padRight(visitArrayNode(
                                (ArrayNode) node.getValueNode()).withPrefix(firstArgPrefix), EMPTY)), Markers.EMPTY) :
                        JContainer.<Expression>build(
                                prefix,
                                ListUtils.mapFirst(
                                        convertAll(StreamSupport.stream(((ArrayNode) node.getValueNode()).spliterator(), false)
                                                        .filter(Objects::nonNull)
                                                        .collect(toList()), n -> sourceBefore(","),
                                                n -> EMPTY),
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
    public J visitNextNode(NextNode node) {
        return new J.Continue(
                randomId(),
                sourceBefore("next"),
                Markers.EMPTY,
                null
        );
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
                        getIdentifier(node.getName()),
                        emptyList(),
                        node.getValue() == null ?
                                null :
                                padLeft(sourceBefore("="), convert(((LocalAsgnNode) node.getValue()).getValueNode())),
                        null
                ), EMPTY))
        );
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
    public J visitRedoNode(RedoNode node) {
        return new Ruby.Redo(
                randomId(),
                sourceBefore("redo"),
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
    public J visitSuperNode(SuperNode node) {
        return new J.MethodInvocation(
                randomId(),
                whitespace(),
                Markers.EMPTY,
                null,
                null,
                new J.Identifier(
                        randomId(),
                        sourceBefore("super"),
                        Markers.EMPTY,
                        emptyList(),
                        "super",
                        null,
                        null
                ),
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
    public J visitSymbolNode(SymbolNode node) {
        return getIdentifier(node.getName());
    }

    @Override
    public J visitTrueNode(TrueNode node) {
        return new J.Literal(randomId(), sourceBefore("true"), Markers.EMPTY, true, "true",
                null, JavaType.Primitive.Boolean);
    }

    @Override
    public J visitVCallNode(VCallNode node) {
        return getIdentifier(node.getName());
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

    @Override
    public J visitYieldNode(YieldNode node) {
        return new Ruby.Yield(
                randomId(),
                sourceBefore("yield"),
                Markers.EMPTY,
                convertArgs("(", node.getArgsNode(), ")")
        );
    }

    private J.Identifier getIdentifier(RubySymbol name) {
        String nameStr = name.asJavaString();
        return new J.Identifier(randomId(), sourceBefore(nameStr), Markers.EMPTY, emptyList(),
                nameStr, null, null);
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
    public J visitRescueBodyNode(RescueBodyNode node) {
        throw new UnsupportedOperationException("RescueBodyNode is a recursive data structure that is " +
                                                "handled by visitRescueNode and so should never be called.");
    }

    @Override
    public J visitRescueNode(RescueNode node) {
        Space prefix = sourceBefore("begin");
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
            J.Identifier exceptionName = getIdentifier(((INameNode) body.get(0)).getName());
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
                        getIdentifier(node.getName()),
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
    public J visitStrNode(StrNode node) {
        String value = new String(node.getValue().bytes(), StandardCharsets.UTF_8);
        Object parentValue = nodes.getParentOrThrow().getValue();
        boolean inDString = parentValue instanceof DStrNode || parentValue instanceof DXStrNode ||
                            parentValue instanceof DRegexpNode;
        Space prefix = inDString ? EMPTY : whitespace();
        String delimiter = "";
        if (!inDString) {
            if (source.charAt(cursor) == '%') {
                DStrNode dstr = new DStrNode(0, node.getValue().getEncoding());
                dstr.add(node);
                return convert(dstr).withPrefix(prefix);
            }
            delimiter = source.substring(cursor, ++cursor);
        }
        skip(value);
        J.Literal literal = new J.Literal(
                randomId(),
                prefix,
                Markers.EMPTY,
                value,
                String.format("%s%s%s", delimiter, value,
                        delimiter.equals("?") ? "" : delimiter),
                null,
                delimiter.equals("?") ? JavaType.Primitive.Char : JavaType.Primitive.String
        );
        if (!inDString) {
            skip(delimiter);
        }
        return literal;
    }

    private <J2 extends J> J2 convert(@Nullable Node t) {
        if (t == null) {
            //noinspection ConstantConditions
            return null;
        }
        nodes = new Cursor(nodes, t);
        //noinspection unchecked
        J2 j = (J2) t.accept(this);
        nodes = nodes.getParentOrThrow();
        return j;
    }

    private <J2 extends J> JRightPadded<J2> convert(Node t, Function<Node, Space> suffix) {
        J2 j = convert(t);
        //noinspection ConstantConditions
        return j == null ? null : new JRightPadded<>(j, suffix.apply(t), Markers.EMPTY);
    }

    private <J2 extends J> JContainer<J2> convertArgs(String before, @Nullable Node argsNode,
                                                      String after) {
        Markers markers = Markers.EMPTY;
        Space prefix = whitespace();
        boolean omitParentheses;
        if (source.startsWith(before, cursor)) {
            skip(before);
            omitParentheses = false;
        } else {
            markers = markers.add(new OmitParentheses(randomId()));
            omitParentheses = true;
        }

        List<Node> args;
        if (argsNode == null) {
            args = singletonList(new NilNode(0));
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
            args = new ArrayList<>(argsArgsNode.getArgs().length);
            Collections.addAll(args, argsArgsNode.getArgs());
            if (argsArgsNode.getRestArgNode() != null) {
                args.add(argsArgsNode.getRestArgNode());
            }
        } else {
            throw new UnsupportedOperationException("Unexpected args node type " + argsNode.getClass().getSimpleName());
        }

        return JContainer.build(
                prefix,
                convertAll(args, n -> sourceBefore(","),
                        n -> omitParentheses ? EMPTY : sourceBefore(after)),
                markers
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
        return Space.format(prefix);
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
        String prefix = source.substring(cursor, indexOfNextNonWhitespace(cursor, source));
        cursor += prefix.length();
        return format(prefix);
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
}
