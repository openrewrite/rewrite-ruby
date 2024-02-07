# noinspection RubySuperCallWithoutSuperclassInspection
class RubyVisitor
  def visit_module(module, p)
    super
  end

  def visit_array(array, p)
    super
  end

  def is_acceptable(source_file, p)
    super
  end

  def language
    super
  end

  def visit_binary(binary, p)
    super
  end

  def visit_container(container, loc, p)
    super
  end

  def visit_compilation_unit(compilation_unit, p)
    super
  end

  def visit_pre_execution(begin, p)
    super
  end

  def visit_block_argument(block_argument, p)
    super
  end

  def visit_boolean_check(boolean_check, p)
    super
  end

  def visit_class_method(class_method, p)
    super
  end

  def visit_delimited_array(delimited_array, p)
    super
  end

  def visit_complex_string(complex_string, p)
    super
  end

  def visit_complex_string_value(value, p)
    super
  end

  def visit_post_execution(end, p)
    super
  end

  def visit_expression_type_tree(expression_type_tree, p)
    super
  end

  def visit_heredoc(heredoc, p)
    super
  end

  def visit_key_value(key_value, p)
    super
  end

  def visit_multiple_assignment(multiple_assignment, p)
    super
  end

  def visit_numeric_domain(numeric_domain, p)
    super
  end

  def visit_open_eigenclass(open_eigenclass, p)
    super
  end

  def visit_rescue(rescue, p)
    super
  end

  def visit_rightward_assignment(rightward_assignment, p)
    super
  end

  def visit_struct_pattern(struct_pattern, p)
    super
  end

  def visit_right_padded(right, loc, p)
    super
  end

  def visit_sub_array_index(sub_array_index, p)
    super
  end

  def visit_symbol(symbol, p)
    super
  end

  def visit_assignment_operation(assign_op, p)
    super
  end

  def visit_block(block, p)
    super
  end

  def visit_break(a_break, p)
    super
  end

  def visit_alias(alias_, p)
    super
  end

  def visit_space(space, loc, p)
    super
  end

  def visit_splat(splat, p)
    super
  end

  def visit_unary(binary, p)
    super
  end

  def visit_begin(begin, p)
    super
  end

  def visit_hash(hash, p)
    super
  end

  def visit_next(next, p)
    super
  end

  def visit_redo(redo, p)
    super
  end

  def visit_retry(retry, p)
    super
  end

  def visit_yield(yield, p)
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
    visitor_instance = Class.new(Java::org.openrewrite.ruby.RubyVisitor).new
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
    visit_module: :visitModule,
    visit_array: :visitArray,
    is_acceptable: :isAcceptable,
    language: :getLanguage,
    visit_binary: :visitBinary,
    visit_container: :visitContainer,
    visit_compilation_unit: :visitCompilationUnit,
    visit_pre_execution: :visitPreExecution,
    visit_block_argument: :visitBlockArgument,
    visit_boolean_check: :visitBooleanCheck,
    visit_class_method: :visitClassMethod,
    visit_delimited_array: :visitDelimitedArray,
    visit_complex_string: :visitComplexString,
    visit_complex_string_value: :visitComplexStringValue,
    visit_post_execution: :visitPostExecution,
    visit_expression_type_tree: :visitExpressionTypeTree,
    visit_heredoc: :visitHeredoc,
    visit_key_value: :visitKeyValue,
    visit_multiple_assignment: :visitMultipleAssignment,
    visit_numeric_domain: :visitNumericDomain,
    visit_open_eigenclass: :visitOpenEigenclass,
    visit_rescue: :visitRescue,
    visit_rightward_assignment: :visitRightwardAssignment,
    visit_struct_pattern: :visitStructPattern,
    visit_right_padded: :visitRightPadded,
    visit_sub_array_index: :visitSubArrayIndex,
    visit_symbol: :visitSymbol,
    visit_assignment_operation: :visitAssignmentOperation,
    visit_block: :visitBlock,
    visit_break: :visitBreak,
    visit_alias: :visitAlias,
    visit_space: :visitSpace,
    visit_splat: :visitSplat,
    visit_unary: :visitUnary,
    visit_begin: :visitBegin,
    visit_hash: :visitHash,
    visit_next: :visitNext,
    visit_redo: :visitRedo,
    visit_retry: :visitRetry,
    visit_yield: :visitYield,
  }
end
