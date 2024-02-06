# frozen_string_literal: true

# setup env
$LOAD_PATH << 'lib'

# load our gem and its jars
require 'openrewrite'

# load test jar
require_jar 'org.openrewrite', 'rewrite-ruby', '0.1.0-SNAPSHOT'
require_jar 'org.openrewrite', 'rewrite-test', '8.14.1'

#use it
print "used classpath:\n\t#{$CLASSPATH.collect(&:to_s).join("\n\t")}"
