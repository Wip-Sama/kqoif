plugins {
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.jvm) apply false
}

val projectVersion = providers.gradleProperty("version").orNull?.takeIf { it != "unspecified" } ?: "1.0.1"


allprojects {
	group = "org.wip"
	version = projectVersion

	repositories {
		mavenCentral()
	}
}

tasks.register("testAll") {
	group = "verification"
	description = "Runs all tests across all multiplatform targets and modules."
	dependsOn(":kqoif-core:check", ":kqoif-imageio:check", ":kqoif-cli:check")
}