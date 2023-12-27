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

public class RubySpace {
    public enum Location {
        ALIAS_PREFIX,
        BEGIN_PREFIX,
        BLOCK_ARGUMENT_PREFIX,
        BLOCK_PARAMETERS,
        BLOCK_PARAMETERS_SUFFIX,
        CLASS_METHOD_DECLARATION_PARAMETERS,
        CLASS_METHOD_DECLARATION_PARAMETERS_SUFFIX,
        CLASS_METHOD_NAME_PREFIX,
        CLASS_METHOD_PREFIX,
        COMPILATION_UNIT_STATEMENT_SUFFIX,
        DELIMITED_STRING_PREFIX,
        DELIMITED_STRING_VALUE_PREFIX,
        DELIMITED_STRING_VALUE_SUFFIX,
        END_PREFIX,
        EXPANSION_PREFIX,
        EXPRESSION_TYPE_TREE_PREFIX,
        HASH,
        HASH_ELEMENTS_SUFFIX,
        HASH_PREFIX,
        KEY_VALUE_PREFIX,
        KEY_VALUE_VALUE_PREFIX,
        LIST_LITERAL,
        LIST_LITERAL_SUFFIX,
        MULTIPLE_ASSIGNMENT_INITIALIZERS,
        MULTIPLE_ASSIGNMENT_INITIALIZERS_SUFFIX,
        MULTIPLE_ASSIGNMENT_PREFIX,
        MULTIPLE_ASSIGNMENT_SUFFIX,
        OPEN_EIGENCLASS_IDENTIFIER_PREFIX,
        OPEN_EIGENCLASS_PREFIX,
        RATIONAL_DENOMINATOR_SUFFIX,
        RATIONAL_PREFIX,
        REDO_PREFIX,
        RESCUE_PREFIX,
        RESCUE_TYPE_SUFFIX,
        RETRY_PREFIX,
        SPLAT_PREFIX,
        STRUCT_EXTENDS_ARGUMENTS,
        STRUCT_EXTENDS_ARGUMENTS_SUFFIX,
        SUB_ARRAY_INDEX_PREFIX,
        SUB_ARRAY_LENGTH_PREFIX,
        YIELD,
        YIELD_DATA_SUFFIX, RATIONAL_NUMERATOR_SUFFIX,
    }
}
