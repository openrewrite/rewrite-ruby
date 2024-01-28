# frozen_string_literal: true

Gem::Specification.new do |s|
  s.name = File.basename(File.dirname(File.expand_path(__FILE__)))
  s.version = '1.0.0'
  s.author = ['Moderne']
  s.email = ['team@moderne.io']
  s.summary = "Rewrite Ruby language support"
  s.description = "Automated refactoring of Ruby code."

  # important to get the jars installed
  s.platform = 'java'

  s.files = Dir['lib/**/*.rb']
  s.files += Dir['lib/**/*.jar']
  s.files += Dir['*file']
  s.files += Dir['*.gemspec']

  s.required_ruby_version = '>= 2.6'

  s.requirements << 'jar org.openrewrite, rewrite-ruby, 0.1.0-SNAPSHOT'
  s.requirements << 'jar org.slf4j, slf4j-api, 1.7.30'
  s.requirements << 'jar org.openrewrite, rewrite-test, 8.14.1'
  s.metadata['rubygems_mfa_required'] = 'true'
end
