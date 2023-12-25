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
package org.openrewrite.ruby.tree;

import lombok.Getter;

public class RubyLeftPadded {

    @Getter
    public enum Location {
        BINARY_OPERATOR(RubySpace.Location.BINARY_OPERATOR),
        CLASS_METHOD_NAME_PREFIX(RubySpace.Location.CLASS_METHOD_NAME_PREFIX),
        KEY_VALUE_VALUE_PREFIX(RubySpace.Location.KEY_VALUE_VALUE_PREFIX),
        SUB_ARRAY_LENGTH_PREFIX(RubySpace.Location.SUB_ARRAY_LENGTH_PREFIX);

        private final RubySpace.Location beforeLocation;

        Location(RubySpace.Location beforeLocation) {
            this.beforeLocation = beforeLocation;
        }
    }
}
