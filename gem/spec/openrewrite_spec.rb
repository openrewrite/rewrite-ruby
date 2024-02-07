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

  context 'recipe name' do
    recipe_instance = MyRecipe.new.to_java
    puts recipe_instance.getDisplayName
    puts recipe_instance.getDescription
    puts recipe_instance.getEstimatedEffortPerOccurrence
    puts recipe_instance.getTags

    # Recipe.subclasses[0].outer_classes[0]
    # OpenRewrite::Ruby::Security::IOMethods.getName

    #
    # OpenRewrite.const_get(OpenRewrite.constants[0]).is_a? Module
    #
    # ObjectSpace.each_object(Recipe) do |r|
    #   puts r
    # end
  end
end
