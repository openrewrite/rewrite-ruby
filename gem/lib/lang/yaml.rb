# noinspection RubySuperCallWithoutSuperclassInspection
class YamlVisitor
  def is_acceptable(source_file, p)
    super
  end

  def language
    super
  end

  def visit_document(document, p)
    super
  end

  def maybe_auto_format(*args)
    super
  end

  def visit_documents(documents, p)
    super
  end

  def visit_mapping(mapping, p)
    super
  end

  def visit_mapping_entry(entry, p)
    super
  end

  def visit_scalar(scalar, p)
    super
  end

  def visit_sequence(sequence, p)
    super
  end

  def visit_sequence_entry(entry, p)
    super
  end

  def visit_anchor(anchor, p)
    super
  end

  def maybe_coalesce_properties
    super
  end

  def remove_unused(cursor_parent)
    super
  end

  def visit_alias(alias_, p)
    super
  end

  def auto_format(*args)
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
    visitor_instance = Class.new(Java::org.openrewrite.yaml.YamlVisitor).new
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
    visit_document: :visitDocument,
    maybe_auto_format: :maybeAutoFormat,
    visit_documents: :visitDocuments,
    visit_mapping: :visitMapping,
    visit_mapping_entry: :visitMappingEntry,
    visit_scalar: :visitScalar,
    visit_sequence: :visitSequence,
    visit_sequence_entry: :visitSequenceEntry,
    visit_anchor: :visitAnchor,
    maybe_coalesce_properties: :maybeCoalesceProperties,
    remove_unused: :removeUnused,
    visit_alias: :visitAlias,
    auto_format: :autoFormat,
  }
end
