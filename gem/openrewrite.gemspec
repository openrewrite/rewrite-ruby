# frozen_string_literal: true

Gem::Specification.new do |s|
  # s.name = File.basename(File.dirname(File.expand_path(__FILE__)))
  s.name = 'openrewrite'
  s.version = '1.0.0'
  s.author = ['Moderne']
  s.email = ['team@moderne.io']
  s.summary = "OpenRewrite automated source code refactoring"
  s.description = "OpenRewrite is a rule-based system for accurate and format-preserving auto remediation of source code."

  # important to get the jars installed
  s.platform = 'java'

  s.files = Dir['lib/**/*.rb']
  s.files += Dir['lib/**/*.jar']
  s.files += Dir['*file']
  s.files += Dir['*.gemspec']

  s.required_ruby_version = '>= 2.6'

  rewrite_version = '8.15.2'

  s.requirements << "jar org.openrewrite, rewrite-ruby, 0.1.0-SNAPSHOT"
  s.requirements << "jar org.openrewrite, rewrite-core, #{rewrite_version}"
  s.requirements << "jar org.openrewrite, rewrite-hcl, #{rewrite_version}"
  s.requirements << "jar org.openrewrite, rewrite-json, #{rewrite_version}"
  s.requirements << "jar org.openrewrite, rewrite-properties, #{rewrite_version}"
  s.requirements << "jar org.openrewrite, rewrite-protobuf, #{rewrite_version}"
  s.requirements << "jar org.openrewrite, rewrite-xml, #{rewrite_version}"
  s.requirements << "jar org.openrewrite, rewrite-yaml, #{rewrite_version}"
  s.requirements << "jar org.openrewrite, rewrite-test, #{rewrite_version}"
  s.requirements << 'jar org.slf4j, slf4j-api, 1.7.30'

  s.metadata['rubygems_mfa_required'] = 'true'
end
