plugins {
	kotlin("multiplatform") version "2.1.10" apply false
	kotlin("jvm") version "2.1.10" apply false
}

allprojects {
	group = "org.wip"
	version = "1.0.0-SNAPSHOT"

	repositories {
		mavenCentral()
	}
}