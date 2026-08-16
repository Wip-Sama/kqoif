plugins {
	kotlin("multiplatform") version "2.1.10" apply false
	kotlin("jvm") version "2.1.10" apply false
}

val projectVersion = providers.gradleProperty("version").orNull?.takeIf { it != "unspecified" } ?: "1.0.0-SNAPSHOT"

allprojects {
	group = "org.wip"
	version = projectVersion

	repositories {
		mavenCentral()
	}
}