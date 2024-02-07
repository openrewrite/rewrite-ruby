## To build

First, run `./gradlew pTML` to install `rewrite-ruby` in Maven local. This is needed to generate the Ruby stub for `RubyVisitor`.

Then run ./gradlew generateRubyVisitors` to generate new visitor stubs (if any have changed).

Finally, run:

```
~/Downloads/jruby-9.4.5.0/bin/jruby -rjars/installer -e 'Jars::Installer.vendor_jars!'
~/Downloads/jruby-9.4.5.0/bin/gem build openrewrite.gemspec
```
