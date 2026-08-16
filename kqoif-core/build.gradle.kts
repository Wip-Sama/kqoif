import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
	kotlin("multiplatform")
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
			implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.7.0")
		}
		commonTest.dependencies {
			implementation(kotlin("test"))
		}
	}
}
