import org.openrewrite.hcl.HclVisitor
import org.openrewrite.json.JsonVisitor
import org.openrewrite.properties.PropertiesVisitor
import org.openrewrite.ruby.RubyVisitor
import org.openrewrite.text.PlainTextVisitor
import org.openrewrite.xml.XmlVisitor
import org.openrewrite.yaml.YamlVisitor
import java.io.PrintWriter
import java.lang.reflect.Modifier
import java.nio.file.Paths
import kotlin.io.path.bufferedWriter

buildscript {
    repositories {
        mavenCentral()
        mavenLocal()
    }

    dependencies {
        classpath("org.openrewrite:rewrite-core:latest.release")
        classpath("org.openrewrite:rewrite-hcl:latest.release")
        classpath("org.openrewrite:rewrite-json:latest.release")
        classpath("org.openrewrite:rewrite-properties:latest.release")
        classpath("org.openrewrite:rewrite-protobuf:latest.release")
        classpath("org.openrewrite:rewrite-xml:latest.release")
        classpath("org.openrewrite:rewrite-yaml:latest.release")
        classpath("org.openrewrite:rewrite-ruby:latest.integration")
    }
}

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite"
description = "Rewrite Ruby"

val latest = "latest.release"

dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:${latest}"))
    api("com.fasterxml.jackson.core:jackson-annotations")
    implementation("org.openrewrite:rewrite-java")

    implementation("io.micrometer:micrometer-core:1.9.+")
    implementation("org.jruby:jruby-base:latest.release")
    implementation("org.apache.commons:commons-text:latest.release")

    implementation("org.openrewrite:rewrite-test")
}

tasks.create("generateRubyVisitors") {
    val visitors = listOf(
            HclVisitor::class.java,
            JsonVisitor::class.java,
            PlainTextVisitor::class.java,
            PropertiesVisitor::class.java,
            RubyVisitor::class.java,
            XmlVisitor::class.java,
            YamlVisitor::class.java,
    )

    fun String.snakeCase(): String {
        val pattern = "(?<=.)[A-Z]".toRegex()
        return this.replace(pattern, "_$0").lowercase()
    }

    visitors.forEach { v ->
        val language = v.simpleName.substringBefore("Visitor")
        PrintWriter(Paths.get("gem/lib/lang/${language.lowercase()}.rb").bufferedWriter()).use { out ->
            val mappings = mutableMapOf<String, String>()
            val overloads = mutableMapOf<String, Int>()

            for (it in v.declaredMethods) {
                var methodName = it.name
                if (it.isSynthetic || !Modifier.isPublic(it.modifiers)) {
                    continue
                }

                if (methodName.startsWith("get")) {
                    methodName = methodName.substringAfter("get")
                }
                methodName = methodName.snakeCase()
                mappings[it.name] = methodName
                overloads.compute(it.name) { n, count -> count?.plus(1) ?: 0 }
            }

            out.println("# noinspection RubySuperCallWithoutSuperclassInspection")
            out.println("class ${v.simpleName}")

            mappings.entries.forEach { (java, ruby) ->
                val parameters = if (overloads[java]!! > 0) "*args" else
                    v.declaredMethods.first { it.name == java }.parameters.joinToString(", ") { p ->
                        when (p.name) {
                            "alias" -> "alias_"
                            else -> p.name.snakeCase()
                        }
                    }
                out.println("  def $ruby${if (parameters.isEmpty()) "" else "($parameters)"}")
                out.println("    super")
                out.println("  end")
                out.println()
            }

            out.println("""
                    |  # noinspection RubySuperCallWithoutSuperclassInspection
                    |  def method_missing(name, *args, &block)
                    |    if name == :set_java_visitor_instance
                    |      @visitor = args[0]
                    |    elsif Mapping[name] == nil
                    |      super
                    |    elsif @visitor.respond_to?(Mapping[name])
                    |      return @visitor.send("super_#{name}", *args, &block)
                    |    else
                    |      super
                    |    end
                    |  end
                    |
                    |  def to_java
                    |    visitor_instance = Class.new(Java::${v.name}).new
                    |    # noinspection RubyResolve
                    |    self.set_java_visitor_instance(visitor_instance)
                    |    ruby_visitor = self
                    |
                    |    Mapping.each do |ruby, java|
                    |      visitor_instance.class.alias_method("super_#{ruby}", java)
                    |      visitor_instance.class.define_method(java) do |*args|
                    |        ruby_visitor.send(ruby, *args)
                    |      end
                    |    end
                    |    visitor_instance
                    |  end
                    """.trimMargin())

            out.println()
            out.println("  private")
            out.println("  Mapping = {")
            mappings.entries.forEach {
                out.println("    ${it.value}: :${it.key},")
            }
            out.println("  }")

            out.println("end")
        }
    }
}
