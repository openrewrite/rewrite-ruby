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

import org.jetbrains.annotations.NotNull;

public final class DelimiterMatcher {

    private DelimiterMatcher() {
    }

    public static String end(String beginDelimiter) {
        if (beginDelimiter.startsWith("%")) {
            char t = beginDelimiter.charAt(1);
            if (t == 'q' || t == 'Q' || t == 's' || t == 'i' || t == 'I' ||
                t == 'w' || t == 'W' || t == 'r' || t == 'x') {
                return matchBeginDelimiter(beginDelimiter.charAt(2), beginDelimiter.substring(2));
            }
            // for the %[foo bar baz] case
            return matchBeginDelimiter(beginDelimiter.charAt(1), beginDelimiter.substring(1));
        }
        return beginDelimiter;
    }

    @NotNull
    private static String matchBeginDelimiter(char c, String orElse) {
        switch (c) {
            case '[':
                return "]";
            case '(':
                return ")";
            case '{':
                return "}";
            case '<':
                return ">";
            default:
                return orElse;
        }
    }
}
