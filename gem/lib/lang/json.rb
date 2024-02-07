# noinspection RubySuperCallWithoutSuperclassInspection
class JsonVisitor
  def visit_array(array, p)
    super
  end

  def is_acceptable(source_file, p)
    super
  end

  def language
    super
  end

  def visit_document(document, p)
    super
  end

  def visit_object(obj, p)
    super
  end

  def visit_right_padded(right, p)
    super
  end

  def visit_literal(literal, p)
    super
  end

  def visit_identifier(identifier, p)
    super
  end

  def visit_member(member, p)
    super
  end

  def visit_space(space, p)
    super
  end

  def visit_empty(empty, p)
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
    visitor_instance = Class.new(Java::org.openrewrite.json.JsonVisitor).new
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
    visit_array: :visitArray,
    is_acceptable: :isAcceptable,
    language: :getLanguage,
    visit_document: :visitDocument,
    visit_object: :visitObject,
    visit_right_padded: :visitRightPadded,
    visit_literal: :visitLiteral,
    visit_identifier: :visitIdentifier,
    visit_member: :visitMember,
    visit_space: :visitSpace,
    visit_empty: :visitEmpty,
  }
end
