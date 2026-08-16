plugins {
	kotlin("jvm")
	`maven-publish`
}

kotlin {
	jvmToolchain(21)
}

dependencies {
	api(project(":kqoif-core"))
	testImplementation(kotlin("test"))
}

tasks.test {
	useJUnitPlatform()
}
