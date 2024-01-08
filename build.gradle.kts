plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite"
description = "Rewrite Ruby"

val latest = "latest.integration"

dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:${latest}"))
    api("com.fasterxml.jackson.core:jackson-annotations")
    implementation("org.openrewrite:rewrite-java")

    implementation("io.micrometer:micrometer-core:1.9.+")
    implementation("org.jruby:jruby-base:latest.release")
    implementation("org.apache.commons:commons-text:latest.release")

    implementation("org.openrewrite:rewrite-test")
}
