# noinspection RubyInstanceMethodNamingConvention
module OpenRewrite
  module Org::Openrewrite::Ruby::Security # the category of the recipe
    class IOMethods < Java::org.openrewrite.Recipe
      def getDisplayName
        "IO Methods"
      end

      def getDescription
        "Replace insecure usages of `IO` with `File`."
      end

      def getVisitor
        Class.new(org.openrewrite.java.JavaIsoVisitor) {
          def visitMethodInvocation(method, ctx)
            if method.select&.print_trimmed(cursor) == "IO" && method.name.simple_name == "read"
              method.with_select(method.select.with_simple_name("File"))
            else
              method
            end
          end
        }.new
      end
    end
  end
end
