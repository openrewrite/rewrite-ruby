# noinspection RubySuperCallWithoutSuperclassInspection
class HclVisitor
  def visit_attribute(attribute, p)
    super
  end

  def is_acceptable(source_file, p)
    super
  end

  def language
    super
  end

  def visit_heredoc_template(heredoc_template, p)
    super
  end

  def visit_body_content(body_content, p)
    super
  end

  def visit_attribute_access(attribute_access, p)
    super
  end

  def visit_left_padded(left, loc, p)
    super
  end

  def visit_binary(binary, p)
    super
  end

  def visit_conditional(conditional, p)
    super
  end

  def visit_config_file(config_file, p)
    super
  end

  def visit_for_intro(for_intro, p)
    super
  end

  def visit_container(container, loc, p)
    super
  end

  def visit_for_object(for_object, p)
    super
  end

  def visit_index_position(index_position, p)
    super
  end

  def visit_right_padded(right, loc, p)
    super
  end

  def visit_literal(literal, p)
    super
  end

  def visit_object_value(object_value, p)
    super
  end

  def visit_parentheses(parentheses, p)
    super
  end

  def visit_quoted_template(template, p)
    super
  end

  def visit_splat_operator(splat_operator, p)
    super
  end

  def visit_template_interpolation(template, p)
    super
  end

  def visit_identifier(identifier, p)
    super
  end

  def visit_expression(expression, p)
    super
  end

  def visit_function_call(function_call, p)
    super
  end

  def visit_for_tuple(for_tuple, p)
    super
  end

  def visit_variable_expression(variable_expression, p)
    super
  end

  def visit_block(block, p)
    super
  end

  def auto_format(*args)
    super
  end

  def visit_space(space, loc, p)
    super
  end

  def visit_empty(empty, p)
    super
  end

  def visit_index(index, p)
    super
  end

  def visit_splat(splat, p)
    super
  end

  def visit_tuple(tuple, p)
    super
  end

  def visit_unary(unary, p)
    super
  end

  # noinspection RubySuperCallWithoutSuperclassInspection
  def method_missing(name, *args, &block)
    if name == :set_java_visitor_instance
      @visitor = args[0]
    elsif Mapping[name] == nil
      super
    elsif @visitor.respond_to?(Mapping[name])
      return @visitor.send("super_#{name}", *args, &block)
    else
      super
    end
  end

  def to_java
    visitor_instance = Class.new(Java::org.openrewrite.hcl.HclVisitor).new
    # noinspection RubyResolve
    self.set_java_visitor_instance(visitor_instance)
    ruby_visitor = self

    Mapping.each do |ruby, java|
      visitor_instance.class.alias_method("super_#{ruby}", java)
      visitor_instance.class.define_method(java) do |*args|
        ruby_visitor.send(ruby, *args)
      end
    end
    visitor_instance
  end

  private
  Mapping = {
    visit_attribute: :visitAttribute,
    is_acceptable: :isAcceptable,
    language: :getLanguage,
    visit_heredoc_template: :visitHeredocTemplate,
    visit_body_content: :visitBodyContent,
    visit_attribute_access: :visitAttributeAccess,
    visit_left_padded: :visitLeftPadded,
    visit_binary: :visitBinary,
    visit_conditional: :visitConditional,
    visit_config_file: :visitConfigFile,
    visit_for_intro: :visitForIntro,
    visit_container: :visitContainer,
    visit_for_object: :visitForObject,
    visit_index_position: :visitIndexPosition,
    visit_right_padded: :visitRightPadded,
    visit_literal: :visitLiteral,
    visit_object_value: :visitObjectValue,
    visit_parentheses: :visitParentheses,
    visit_quoted_template: :visitQuotedTemplate,
    visit_splat_operator: :visitSplatOperator,
    visit_template_interpolation: :visitTemplateInterpolation,
    visit_identifier: :visitIdentifier,
    visit_expression: :visitExpression,
    visit_function_call: :visitFunctionCall,
    visit_for_tuple: :visitForTuple,
    visit_variable_expression: :visitVariableExpression,
    visit_block: :visitBlock,
    auto_format: :autoFormat,
    visit_space: :visitSpace,
    visit_empty: :visitEmpty,
    visit_index: :visitIndex,
    visit_splat: :visitSplat,
    visit_tuple: :visitTuple,
    visit_unary: :visitUnary,
  }
end
