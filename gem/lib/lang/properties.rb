# noinspection RubySuperCallWithoutSuperclassInspection
class PropertiesVisitor
  def is_acceptable(source_file, p)
    super
  end

  def language
    super
  end

  def visit_file(file, p)
    super
  end

  def visit_comment(comment, p)
    super
  end

  def visit_entry(entry, p)
    super
  end

  def visit_value(value, p)
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
    visitor_instance = Class.new(Java::org.openrewrite.properties.PropertiesVisitor).new
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
    language: :getLanguage,
    visit_file: :visitFile,
    visit_comment: :visitComment,
    visit_entry: :visitEntry,
    visit_value: :visitValue,
  }
end
