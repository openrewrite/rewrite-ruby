/*
 * Copyright 2021 the original author or authors.
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

import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

public class RubySpace {
    public enum Location {
        ALIAS_PREFIX,
        ARRAY_ELEMENTS,
        ARRAY_PREFIX,
        BEGIN_PREFIX,
        BLOCK_ARGUMENT_PREFIX,
        BLOCK_PARAMETERS,
        BLOCK_PARAMETERS_SUFFIX,
        BLOCK_STATEMENT_SUFFIX,
        BOOLEAN_CHECK_IN_PREFIX,
        BOOLEAN_CHECK_PREFIX,
        CLASS_METHOD_DECLARATION_PARAMETERS,
        CLASS_METHOD_DECLARATION_PARAMETER_SUFFIX,
        CLASS_METHOD_NAME_PREFIX,
        CLASS_METHOD_PREFIX,
        COMPILATION_UNIT_STATEMENT_SUFFIX,
        DELIMITED_ARRAY_ELEMENTS,
        DELIMITED_ARRAY_ELEMENT_SUFFIX,
        DELIMITED_ARRAY_PREFIX,
        DELIMITED_STRING_PREFIX,
        DELIMITED_STRING_VALUE_PREFIX,
        DELIMITED_STRING_VALUE_SUFFIX,
        END_PREFIX,
        EXPRESSION_TYPE_TREE_PREFIX,
        HASH,
        HASH_ELEMENTS,
        HASH_ELEMENTS_SUFFIX,
        HASH_PREFIX,
        HEREDOC_PREFIX,
        KEY_VALUE_PREFIX,
        KEY_VALUE_SEPARATOR_PREFIX,
        LIST_LITERAL_SUFFIX,
        MODULE_PREFIX,
        MULTIPLE_ASSIGNMENT_ASSIGNMENTS,
        MULTIPLE_ASSIGNMENT_INITIALIZERS,
        MULTIPLE_ASSIGNMENT_INITIALIZER_SUFFIX,
        MULTIPLE_ASSIGNMENT_PREFIX,
        MULTIPLE_ASSIGNMENT_SUFFIX,
        NUMERIC_DOMAIN_PREFIX,
        NUMERIC_VALUE_SUFFIX,
        OPEN_EIGENCLASS_IDENTIFIER_PREFIX,
        OPEN_EIGENCLASS_PREFIX,
        RATIONAL_DENOMINATOR_SUFFIX,
        REDO_PREFIX,
        RESCUE_PREFIX,
        RESCUE_TYPE_SUFFIX,
        RETRY_PREFIX,
        RIGHTWARD_ASSIGNMENT_PREFIX,
        SPLAT_PREFIX,
        STRUCT_EXTENDS_ARGUMENTS,
        STRUCT_EXTENDS_ARGUMENTS_SUFFIX,
        STRUCT_PATTERN_ELEMENT,
        STRUCT_PATTERN_ELEMENT_SUFFIX,
        STRUCT_PATTERN_PREFIX,
        SUB_ARRAY_LENGTH_PREFIX,
        SUB_ARRAY_PREFIX,
        SYMBOL_PREFIX,
        YIELD_DATA,
        YIELD_DATA_SUFFIX,
    }

    // FIXME many recipes use Space.format(..) directly. we will need to make this non-static and use a
    // "service" much like we do for auto-format.
    public static Space format(String formatting) {
        return format(formatting, 0, formatting.length());
    }

    public static Space format(String formatting, int beginIndex, int toIndex) {
        if (beginIndex == toIndex) {
            return Space.EMPTY;
        } else if (toIndex == beginIndex + 1 && ' ' == formatting.charAt(beginIndex)) {
            return Space.SINGLE_SPACE;
        } else {
            rangeCheck(formatting.length(), beginIndex, toIndex);
        }

        StringBuilder prefix = new StringBuilder();
        StringBuilder comment = new StringBuilder();
        List<Comment> comments = new ArrayList<>(1);

        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;

        for (int i = beginIndex; i < toIndex; i++) {
            char c = formatting.charAt(i);
            char next = i + 1 < toIndex ? formatting.charAt(i + 1) : '\0';
            switch (c) {
                case '#':
                    if (inSingleLineComment) {
                        comment.append(c);
                    } else if (!inMultiLineComment) {
                        inSingleLineComment = true;
                        comment.setLength(0);
                    }
                    break;
                case '\r':
                case '\n':
                    if (inSingleLineComment) {
                        inSingleLineComment = false;
                        comments.add(new RubyTextComment(false, comment.toString(), prefix.toString(), Markers.EMPTY));
                        prefix.setLength(0);
                        comment.setLength(0);
                        prefix.append(c);
                    } else if (inMultiLineComment) {
                        comment.append(c);
                        if (next == '=' && formatting.startsWith("=end", i + 1)) {
                            i += "=end".length();
                            inMultiLineComment = false;
                            comments.add(new RubyTextComment(true, comment.toString(), prefix.toString(), Markers.EMPTY));
                            prefix.setLength(0);
                            comment.setLength(0);
                        }
                    } else {
                        if (next == '=' && formatting.startsWith("=begin", i + 1)) {
                            i += "=begin".length();
                            inMultiLineComment = true;
                            comment.setLength(0);
                        }
                        prefix.append(c);
                    }
                    break;
                default:
                    if (inSingleLineComment || inMultiLineComment) {
                        comment.append(c);
                    } else {
                        prefix.append(c);
                    }
            }
        }
        // If a file ends with a single-line comment there may be no terminating newline
        if (comment.length() > 0) {
            comments.add(new RubyTextComment(false, comment.toString(), prefix.toString(), Markers.EMPTY));
            prefix.setLength(0);
        }

        // Shift the whitespace on each comment forward to be a suffix of the comment before it, and the
        // whitespace on the first comment to be the whitespace of the tree element. The remaining prefix is the suffix
        // of the last comment.
        String whitespace = prefix.toString();
        if (!comments.isEmpty()) {
            for (int i = comments.size() - 1; i >= 0; i--) {
                Comment c = comments.get(i);
                String next = c.getSuffix();
                comments.set(i, c.withSuffix(whitespace));
                whitespace = next;
            }
        }

        assert whitespace.matches("[\\s\\\\]*") : String.format("Invalid whitespace |%s|", whitespace);
        return Space.build(whitespace, comments);
    }

    // FIXME maybe can be made public in Space so it isn't duplicated here, or put in some
    // other utility class to not pollute API surface of Space
    static void rangeCheck(int arrayLength, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException(
                    "fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        if (fromIndex < 0) {
            throw new StringIndexOutOfBoundsException(fromIndex);
        }
        if (toIndex > arrayLength) {
            throw new StringIndexOutOfBoundsException(toIndex);
        }
    }
}
