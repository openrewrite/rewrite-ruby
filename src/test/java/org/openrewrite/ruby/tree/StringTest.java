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
package org.openrewrite.ruby.tree;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.ruby.Assertions.ruby;

public class StringTest implements RewriteTest {

    @Test
    void empty() {
        rewriteRun(
          ruby(
            """
              ''
              """
          )
        );
    }

    @Test
    void string() {
        rewriteRun(
          ruby(
            """
              "The programming language is"
              """
          )
        );
    }

    @Test
    void singleQuoteDelimiter() {
        rewriteRun(
          ruby(
            """
              'The programming language is'
              """
          )
        );
    }

    @Test
    void delimitedString() {
        rewriteRun(
          ruby(
            """
              "#{a1} The programming language is #{a1}"
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Q", "q"})
    void percentDelimitedString(String delim) {
        rewriteRun(
          ruby(
            """
              %%%s[#{a1} The programming language is #{a1}]
              """.formatted(delim)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "i", "x", "m", "u", "e", "s", "n"})
    void regex(String options) {
        rewriteRun(
          ruby(
            """
              /^[yY]/%s
              """.formatted(options)
          )
        );
    }

    @Test
    void regexRDString() {
        rewriteRun(
          ruby(
            """
              %r[^/usr/local/.*]
              """
          )
        );
    }

    @Test
    void regexDString() {
        rewriteRun(
          ruby(
            """
              /my name is #{name}/o
              """
          )
        );
    }

    @Test
    void backReferences() {
        rewriteRun(
          ruby(
            """
              /h/.match("hello")
              $&
              """
          )
        );
    }

    @Test
    void nthMatch() {
        rewriteRun(
          ruby(
            """
              /h/.match("hello")
              $1
              """
          )
        );
    }

    /**
     * Squiggly "here docs" remove the extra indentation.
     */
    @ParameterizedTest
    @ValueSource(strings = {"<<~", "<<-"})
    void hereDocuments(String startDelim) {
        rewriteRun(
          ruby(
            """
              document = %sHERE.chomp
                  This is a string literal.
                  It has two lines and abruptly ends...
              HERE
              
              1 and 2
              """.formatted(startDelim)
          )
        );
    }

    @Disabled
    @Test
    void multipleHeredocs() {
        rewriteRun(
          ruby(
            """
              greeting = <<-HERE + <<-THERE + "World"
              Hello
              HERE
              There
              THERE
              """
          )
        );
    }

    @Test
    void xString() {
        rewriteRun(
          ruby(
            """
              `mkdir -p src/main/java`
              """
          )
        );
    }

    @Test
    void xStringPercentDelimited() {
        rewriteRun(
          ruby(
            """
              %x(echo 1)
              """
          )
        );
    }

    @Test
    void nonInterpolableStringArrayLiteral() {
        rewriteRun(
          ruby(
            """
              %w[foo bar baz]
              """
          )
        );
    }

    @Test
    void interpolableStringArrayLiteral() {
        rewriteRun(
          ruby(
            """
              %W(#{1 + 1})
              """
          )
        );
    }

    @Test
    void characterLiteral() {
        rewriteRun(
          ruby(
            """
              ?A
              """
          )
        );
    }

    /**
     * Just to ensure we are grouping StrNode/DNode children of ArrayNode together correctly.
     */
    @Test
    void stringArray() {
        rewriteRun(
          ruby(
            """
              ["a", "b", "c"]
              """
          )
        );
    }

    @Test
    void implicitConcatenation() {
        rewriteRun(
          ruby(
            """
              DIGITS = 'AB' \\
                       'ab' \\
                       '01'
              """
          ),
          ruby(
            """
              DIGITS = 'AB' 'ab' '01'
              """
          )
        );
    }

    @Test
    void implicitConcatenationNotLastStatement() {
        rewriteRun(
          ruby(
            """
              expect(
                "This terraform provider syntax is now deprecated.\\n" \\
                "See https://www.terraform.io/docs/language/providers/requirements.html " \\
                "for the new Terraform v0.13+ provider syntax!"
              )
              """
          )
        );
    }

    @Test
    void heredocWithClosingParen() {
        rewriteRun(
          ruby(
            """
              expect(<<~HCL)
                module "s3-webapp"
              HCL
              
              a = "hello world"
              """
          )
        );
    }

    @Test
    void heredocStartingOnNextLine() {
        rewriteRun(
          ruby(
            """
              expect(updated_file.content).to include(
                <<~DEP
                  module "origin_label" {
                    source     = "git::https://github.com/cloudposse/terraform-null-label.git?ref=tags/0.4.1"
                DEP
              )
              
              1 and 2
              """
          )
        );
    }
}
