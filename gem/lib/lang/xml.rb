# noinspection RubySuperCallWithoutSuperclassInspection
class XmlVisitor
  def visit_attribute(attribute, p)
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

  def maybe_auto_format(*args)
    super
  end

  def visit_xml_decl(xml_decl, p)
    super
  end

  def visit_processing_instruction(processing_instruction, p)
    super
  end

  def visit_char_data(char_data, p)
    super
  end

  def visit_doc_type_decl(doc_type_decl, p)
    super
  end

  def visit_prolog(prolog, p)
    super
  end

  def visit_element(element, p)
    super
  end

  def visit_comment(comment, p)
    super
  end

  def auto_format(*args)
    super
  end

  def visit_tag(tag, p)
    super
  end

  def visit_ident(ident, p)
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
    visitor_instance = Class.new(Java::org.openrewrite.xml.XmlVisitor).new
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
    visit_document: :visitDocument,
    maybe_auto_format: :maybeAutoFormat,
    visit_xml_decl: :visitXmlDecl,
    visit_processing_instruction: :visitProcessingInstruction,
    visit_char_data: :visitCharData,
    visit_doc_type_decl: :visitDocTypeDecl,
    visit_prolog: :visitProlog,
    visit_element: :visitElement,
    visit_comment: :visitComment,
    auto_format: :autoFormat,
    visit_tag: :visitTag,
    visit_ident: :visitIdent,
  }
end
