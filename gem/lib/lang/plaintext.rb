# noinspection RubySuperCallWithoutSuperclassInspection
class PlainTextVisitor
  def is_acceptable(source_file, p)
    super
  end

  def is_adaptable_to(adapt_to)
    super
  end

  def visit_snippet(snippet, p)
    super
  end

  def visit_text(text, p)
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
    visitor_instance = Class.new(Java::org.openrewrite.text.PlainTextVisitor).new
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
    is_acceptable: :isAcceptable,
    is_adaptable_to: :isAdaptableTo,
    visit_snippet: :visitSnippet,
    visit_text: :visitText,
  }
end
