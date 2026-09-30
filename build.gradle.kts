plugins {
    application
}

group = "dev.testbuild"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("dev.testbuild.HybridBuilder")
}

tasks.register<JavaExec>("selfTest") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.testbuild.HybridBuilderSelfTest")
}
