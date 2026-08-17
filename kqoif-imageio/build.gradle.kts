plugins {
	alias(libs.plugins.kotlin.jvm)
	`maven-publish`
}

kotlin {
	jvmToolchain(21)
}

dependencies {
	api(project(":kqoif-core"))
	implementation(libs.kotlinx.io.core)
	implementation(libs.bundles.twelvemonkeys.imageio)
	testImplementation(kotlin("test"))
}

tasks.test {
	useJUnitPlatform()
}
