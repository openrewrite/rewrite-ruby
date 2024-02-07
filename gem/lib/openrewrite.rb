# frozen_string_literal: true

require 'openrewrite-ruby_jars'

require_relative 'lang/hcl'
require_relative 'lang/json'
require_relative 'lang/plaintext'
require_relative 'lang/properties'
require_relative 'lang/xml'
require_relative 'lang/yaml'

#   def java_visitor(&body)
#     Class.new(Java::org.openrewrite.java.JavaVisitor, &body).new
#   end

module OpenRewrite
  module Ruby
    Category = 'org.openrewrite.ruby'
  end

  class Recipe
    def display_name
      raise NotImplementedError, "Supply a name that is initial-capped (e.g. 'Remove unused requires') that does not end in a period."
    end

    def description
      raise NotImplementedError, "Supply a description in markdown format as a complete sentence(s) ending in a period."
    end

    # @return numeric value in seconds (e.g. 300 for 5 minutes estimated manual effort)
    def effort_per_occurrence
      300
    end

    def tags
      []
    end

    # noinspection RubySuperCallWithoutSuperclassInspection
    def method_missing(name, *args, &block)
      if name == :set_java_recipe_instance
        @recipe = args[0]
      elsif Mapping[name] == nil
        super
      elsif @recipe.respond_to?(Mapping[name])
        return @recipe.send("super_#{name}", *args, &block)
      else
        super
      end
    end

    def to_java
      recipe_instance = Class.new(Java::org.openrewrite.Recipe).new
      # noinspection RubyResolve
      self.set_java_recipe_instance(recipe_instance)
      ruby_recipe = self

      Mapping.each do |ruby, java|
        recipe_instance.class.alias_method("super_#{ruby}", java)
        recipe_instance.class.define_method(java) do |*args|
          ret = ruby_recipe.send(ruby, *args)
          case ruby
          when :tags
            tags = Java::java.util.HashSet.new
            ret.each { |tag| tags.add(tag) }
            tags
          when :effort_per_occurrence
            if ret.is_a?(Java::java.time.Duration)
              ret
            else
              Java::java.time.Duration.ofSeconds(ret)
            end
          else
            ret
          end
        end
      end
      recipe_instance
    end

    private

    Mapping = {
      display_name: :getDisplayName,
      description: :getDescription,
      effort_per_occurrence: :getEstimatedEffortPerOccurrence,
      tags: :getTags
    }
  end

  module RewriteTest
    def ruby(*args)
      case args.size
      when 1
        Java::org.openrewrite.ruby.Assertions.ruby(args[0])
      when 2
        Java::org.openrewrite.ruby.Assertions.ruby(args[0], args[1])
      else
        raise "Expected 1 or 2 arguments, but got #{args.size}"
      end
    end

    class RewriteTestWrapper
      include org.openrewrite.test.RewriteTest

      def initialize(recipe)
        @recipe = recipe
      end

      def defaults(spec)
        spec.recipe(@recipe).validateRecipeSerialization(false)
      end
    end

    def rewrite_run(recipe, *specs)
      RewriteTestWrapper.new(recipe.to_java).rewriteRun(specs.to_java(:Java::org.openrewrite.test.SourceSpec))
    end
  end
end
