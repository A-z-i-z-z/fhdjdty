import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun gitOutput(vararg args: String): String = try {
    val p = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText().trim()
    if (p.waitFor() == 0) out else ""
} catch (e: Exception) { "" }

val gitCommit = providers.gradleProperty("gitCommit").orNull
    ?: gitOutput("rev-parse", "--short=12", "HEAD").ifEmpty { "unknown" }
val buildNumber = providers.gradleProperty("buildNumber").orNull?.toIntOrNull() ?: 0

// Runtime inputs. In CI these point at real build products; locally they may be
// absent, in which case a clearly-marked stub is packaged instead.
//   -ProotfsArchive=/path/rootfs-arm64.tar.xz   (+ sibling rootfs-arm64.info)
//   -PprootDir=/path/with/libproot.so+libproot-loader.so (+ proot-build.info)
val rootfsArchive = providers.gradleProperty("rootfsArchive").orNull
val prootDir = providers.gradleProperty("prootDir").orNull

abstract class PrepareRuntimeTask : DefaultTask() {
    @get:Optional @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val rootfsArchive: RegularFileProperty
    @get:Optional @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val rootfsInfo: RegularFileProperty
    @get:Optional @get:InputDirectory @get:PathSensitive(PathSensitivity.NONE)
    abstract val prootDir: DirectoryProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val launcherScript: RegularFileProperty
    @get:OutputDirectory
    abstract val assetsOut: DirectoryProperty
    @get:OutputDirectory
    abstract val jniOut: DirectoryProperty

    @TaskAction
    fun run() {
        val assets = assetsOut.get().asFile
        val jni = jniOut.get().asFile
        assets.deleteRecursively(); jni.deleteRecursively()
        assets.mkdirs(); File(jni, "arm64-v8a").mkdirs()
        Files.copy(launcherScript.get().asFile.toPath(), File(assets, "pocketagent-proot.sh").toPath(),
            StandardCopyOption.REPLACE_EXISTING)
        val info = StringBuilder()
        if (rootfsArchive.isPresent) {
            val dst = File(assets, "rootfs.tar.xz")
            try { Files.createLink(dst.toPath(), rootfsArchive.get().asFile.toPath()) }
            catch (e: Exception) { Files.copy(rootfsArchive.get().asFile.toPath(), dst.toPath()) }
            if (rootfsInfo.isPresent) info.append(rootfsInfo.get().asFile.readText())
            info.append("stub_rootfs=false\n")
        } else {
            File(assets, "rootfs.tar.xz").writeText("STUB: this build contains no rootfs\n")
            info.append("stub_rootfs=true\n")
        }
        File(assets, "rootfs.info").writeText(info.toString())
        if (prootDir.isPresent) {
            val d = prootDir.get().asFile
            for (n in listOf("libproot.so", "libproot-loader.so")) {
                Files.copy(File(d, n).toPath(), File(jni, "arm64-v8a/$n").toPath())
            }
            val pi = File(d, "proot-build.info")
            File(assets, "proot.info").writeText(if (pi.exists()) pi.readText() + "stub_proot=false\n" else "stub_proot=false\n")
        } else {
            for (n in listOf("libproot.so", "libproot-loader.so")) {
                File(jni, "arm64-v8a/$n").writeText("#!/system/bin/sh\necho 'stub proot: no real proot in this build' >&2\nexit 99\n")
            }
            File(assets, "proot.info").writeText("stub_proot=true\n")
        }
    }
}

android {
    namespace = "dev.pocketagent.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.pocketagent.app"
        minSdk = 26
        // Deliberate: apps targeting <= 28 may execve() files in app-private data,
        // which the extracted rootfs needs. See README.
        targetSdk = 28
        versionCode = maxOf(buildNumber, 1)
        versionName = "0.0.$buildNumber-phase0"
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Committed keystore with the conventional android/android credentials.
        // ONLY for sideload test builds so that CI builds can update each other.
        create("sideload") {
            storeFile = file("sideload-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sideload") }
        release {
            signingConfig = signingConfigs.getByName("sideload")
            isMinifyEnabled = false
        }
    }

    packaging {
        jniLibs {
            // proot + loader must exist as real files on disk at install time.
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
    }
    // The rootfs archive must stay uncompressed inside the APK (it is already xz'd,
    // and openFd() needs an uncompressed entry to report its length).
    androidResources { noCompress += listOf("xz", "zst") }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = false }
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val prep = tasks.register<PrepareRuntimeTask>("prepareRuntime$cap") {
            launcherScript.set(rootProject.layout.projectDirectory.file("runtime/pocketagent-proot.sh"))
            rootfsArchive?.let {
                this.rootfsArchive.set(file(it))
                val infoFile = File(file(it).parentFile, file(it).name.replace(".tar.xz", ".info"))
                if (infoFile.exists()) rootfsInfo.set(infoFile)
            }
            prootDir?.let { this.prootDir.set(file(it)) }
            assetsOut.set(layout.buildDirectory.dir("generated/pa-runtime/${variant.name}/assets"))
            jniOut.set(layout.buildDirectory.dir("generated/pa-runtime/${variant.name}/jniLibs"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(prep, PrepareRuntimeTask::assetsOut)
        variant.sources.jniLibs?.addGeneratedSourceDirectory(prep, PrepareRuntimeTask::jniOut)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
