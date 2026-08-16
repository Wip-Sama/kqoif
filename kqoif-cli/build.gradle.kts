plugins {
	kotlin("jvm")
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
	implementation("com.github.ajalt.clikt:clikt:5.0.3")
	testImplementation(kotlin("test"))
}

tasks.test {
	useJUnitPlatform()
}
