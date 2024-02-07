# frozen_string_literal: true

require 'openrewrite'

describe OpenRewrite do
  include OpenRewrite::RewriteTest

  class MyRecipe < OpenRewrite::Recipe
    def display_name
      "My recipe"
    end

    def description
      "#{display_name}: is a description of my recipe."
    end

    # noinspection RubyInstanceMethodNamingConvention
    def effort_per_occurrence
      super # super calls get mapped to calls on the Java superclass
    end
  end

  context 'Convert recipe to a Java Recipe instance' do
    recipe_instance = MyRecipe.new.to_java
    puts recipe_instance.getDisplayName
    puts recipe_instance.getDescription
    puts recipe_instance.getEstimatedEffortPerOccurrence
    puts recipe_instance.getTags
  end

  context 'Convert visitor to a Java Visitor instance' do
    visitor_instance = Class.new(JsonVisitor) do
      def visit_array(array, p)
        array
      end
    end.new.to_java

    # proves that visit_array above is called, as nil would cause an NPE on the
    # Java-based JsonVisitor implementation of visitArray
    visitor_instance.visitArray(nil, 0)
  end

  # Recipe.subclasses[0].outer_classes[0]
  # OpenRewrite::Ruby::Security::IOMethods.getName

  #
  # OpenRewrite.const_get(OpenRewrite.constants[0]).is_a? Module
  #
  # ObjectSpace.each_object(Recipe) do |r|
  #   puts r
  # end
end
