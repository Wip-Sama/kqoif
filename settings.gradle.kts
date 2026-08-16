plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "kqoif"

include(":kqoif-core")
include(":kqoif-imageio")
include(":kqoif-cli")