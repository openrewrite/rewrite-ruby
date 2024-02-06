# frozen_string_literal: true

require 'openrewrite-ruby_jars'
require 'facets/string/camelcase'

#   def java_visitor(&body)
#     Class.new(Java::org.openrewrite.java.JavaVisitor, &body).new
#   end

module OpenRewrite
  module Ruby
    Category = 'org.openrewrite.ruby'
  end

  class Recipe
    def get_display_name
      raise NotImplementedError, "Supply a name that is initial-capped (e.g. 'Remove unused requires') that does not end in a period."
    end

    def get_description
      raise NotImplementedError, "Supply a description in markdown format as a complete sentence(s) ending in a period."
    end

    # noinspection RubySuperCallWithoutSuperclassInspection
    def method_missing(name, *args, &block)
      openrewrite_name = name.to_s.camelcase
      openrewrite_name = openrewrite_name == 'getEstimatedEffortPerOccurrenceSeconds' ?
                           'getEstimatedEffortPerOccurrence' : openrewrite_name
      if name == :set_java_recipe_instance
        @recipe = args[0]
      elsif @recipe.respond_to?(openrewrite_name)
        return @recipe.send("super_#{name}", *args, &block)
      else
        super
      end
    end
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
      RewriteTestWrapper.new(recipe).rewriteRun(specs.to_java(:Java::org.openrewrite.test.SourceSpec))
    end
  end

  # TODO this should probably go somewhere else that is not public to recipe authors
  # since it is only needed to convert a Ruby recipe to a Java recipe at runtime
  def self.to_java(recipe)
    recipe_instance = Class.new(Java::org.openrewrite.Recipe).new
    # noinspection RubyResolve
    recipe.set_java_recipe_instance(recipe_instance)
    recipe.class.instance_methods(false).each do |m|
      recipe_instance.class.instance_eval {
        camel_name = m.to_s.camelcase
        openrewrite_name = camel_name == 'getEstimatedEffortPerOccurrenceSeconds' ?
                             'getEstimatedEffortPerOccurrence' : camel_name
        alias_method("super_#{m.name}", openrewrite_name)
        define_method(openrewrite_name) do
          ret = recipe.send(m.name)
          case openrewrite_name
          when 'getTags'
            tags = Java::java.util.HashSet.new
            ret.each { |tag| tags.add(tag) }
            tags
          when 'getEstimatedEffortPerOccurrence'
            ret.is_a?(Java::java.time.Duration) ? ret :
              Java::java.time.Duration.ofSeconds(ret)
          else
            ret
          end
        end
      }
    end
    recipe_instance
  end
end
