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

public final class StringUtils {

    private StringUtils() {
    }

    public static String escapeRuby(String delimiter, String value, String source, int cursor) {
        if (!delimiter.equals("\"")) {
            return value;
        }

        StringBuilder sb = new StringBuilder();
        char[] valueChars = value.toCharArray();
        for (int i = 0; i < valueChars.length; i++) {
            char c = source.charAt(cursor);
            if (c == '\\') {
                cursor++;
                char next = source.charAt(cursor);
                switch (next) {
                    case '\\':
                    case 'a':
                    case 'b':
                    case 'f':
                    case 'e':
                    case 's':
                    case 'n':
                    case 'r':
                    case 't':
                    case 'v':
                    case '"':
                        sb.append("\\");
                        sb.append(next);
                        break;
                    case '0':
                    case 'x':
                        // differentiate between single, double, and triple digit octals
                        int j;
                        for (j = 1; j <= 2; j++) {
                            if (!Character.isDigit(source.charAt(cursor + j))) {
                                break;
                            }
                        }
                        sb.append("\\");
                        sb.append(source, cursor, cursor + j);
                        cursor += j;
                        break;
                    case 'u':
                        sb.append("\\u");
                        cursor++;
                        if (source.charAt(cursor) == '{') {
                            sb.append(source, cursor, cursor + 6);
                        } else {
                            sb.append(source, cursor, cursor + 4);
                        }
                        break;
                    case 'c':
                        sb.append(source, cursor - 1, cursor + 2);
                        cursor++;
                        break;
                    case 'C':
                    case 'M':
                        sb.append("\\");
                        sb.append(c);
                        cursor++;
                        if (source.charAt(cursor) == '-') {
                            sb.append(source, cursor, cursor + 2);
                            cursor++;
                        } else {
                            sb.append(c);
                        }
                        break;
                    default:
                        sb.append(next);
                }
            } else {
                sb.append(c);
            }
            cursor++;
        }

        return sb.toString();
    }

    public static String endSymbol(String beginDelimiter) {
        if (beginDelimiter.equals(":")) {
            return "";
        } else if (beginDelimiter.startsWith(":")) {
            return endDelimiter(beginDelimiter.substring(1));
        }
        return endDelimiter(beginDelimiter);
    }

    public static String endDelimiter(String beginDelimiter) {
        if (beginDelimiter.startsWith("%")) {
            char t = beginDelimiter.charAt(1);
            if (t == 'q' || t == 'Q' || t == 's' || t == 'i' || t == 'I' ||
                t == 'w' || t == 'W' || t == 'r' || t == 'x') {
                return matchBeginDelimiter(beginDelimiter.charAt(2), beginDelimiter.substring(2));
            }
            // for the %[foo bar baz] case
            return matchBeginDelimiter(beginDelimiter.charAt(1), beginDelimiter.substring(1));
        }
        if (beginDelimiter.equals("?")) {
            return ""; // the character literal case and the bare symbol case
        }
        if (beginDelimiter.length() == 1) {
            return matchBeginDelimiter(beginDelimiter.charAt(0), beginDelimiter);
        }
        return beginDelimiter;
    }

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
