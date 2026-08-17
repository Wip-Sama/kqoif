plugins {
	alias(libs.plugins.kotlin.jvm)
	application
}

kotlin {
	jvmToolchain(21)
}

application {
	mainClass.set("org.wip.kqoif.cli.MainKt")
}

dependencies {
	implementation(project(":kqoif-core"))
	implementation(project(":kqoif-imageio"))
	implementation(libs.clikt)
	testImplementation(kotlin("test"))
}

tasks.test {
	useJUnitPlatform()
}
