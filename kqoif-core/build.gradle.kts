import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	`maven-publish`
}

kotlin {
	jvmToolchain(21)

	jvm()

	@OptIn(ExperimentalWasmDsl::class)
	wasmJs {
		browser()
		nodejs()
	}

	@OptIn(ExperimentalWasmDsl::class)
	wasmWasi {
		nodejs()
	}

	js {
		browser()
		nodejs()
	}

	mingwX64()
	linuxX64()
	macosX64()
	macosArm64()

	sourceSets {
		commonMain.dependencies {
			implementation(libs.kotlinx.io.core)
		}
		commonTest.dependencies {
			implementation(kotlin("test"))
		}
	}
}
