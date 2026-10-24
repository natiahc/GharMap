plugins { id("com.android.application") }

val arcoreNative by configurations.creating
val extractArcore by tasks.registering(Sync::class) {
    from({ arcoreNative.map { zipTree(it) } }) { include("jni/**") }
    into(layout.buildDirectory.dir("arcore"))
}

android {
    namespace = "com.gharmap.app"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.gharmap.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild { cmake {
            cppFlags += listOf("-std=c++17", "-Wall", "-Wextra")
            arguments += "-DARCORE_LIB_DIR=${layout.buildDirectory.get().asFile}/arcore/jni"
        } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs.pickFirsts += "**/libarcore_sdk_c.so" }
}
tasks.configureEach {
    if (name.startsWith("configureCMake") || name == "preBuild") dependsOn(extractArcore)
}
dependencies {
    implementation("com.google.ar:core:1.48.0")
    arcoreNative("com.google.ar:core:1.48.0@aar")
}
