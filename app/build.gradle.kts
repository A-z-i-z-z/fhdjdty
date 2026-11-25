import java.nio.file.Files
import java.util.Properties
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

fun prop(name: String): String? = providers.gradleProperty(name).orNull?.trim()?.ifEmpty { null }

val gitCommit = prop("gitCommit") ?: gitOutput("rev-parse", "--short=12", "HEAD").ifEmpty { "unknown" }

// ---- Build identity (Wave 0 contract, sections 1.2 and 3.1) ----------------------------------------------------
// Three variants:
//   owner       : the release type. Package dev.pocketagent.app, R8 and resource shrinking, not debuggable.
//   twin        : the debug type. Package dev.pocketagent.app.debug, label "Agiri Test", marked icons, test hooks.
//   owner-debug : the debug type built with -PownerDebug=true. Package dev.pocketagent.app, no twin overlays, test hooks.
// Gradle inputs (all optional):
//   -PciRun=<int> (alias -PbuildNumber)  the CI run number the build belongs to (default 0)
//   -PlocalSeq=<0..99>                   0 = a CI build, 1..99 = a local build on top of that run
//   -PgitCommit=<text>                   short commit label
//   -PsigningKind=private|committed      CI only: says that CI signs the owner APK with the private key afterwards
//   -Pshrink=true|false                  R8 and resource shrinking of the release type (default true)
//   -PownerDebug=true                    the debug type with the owner's package (see above)
//   -PownerSigning=<path>                a properties file with the owner's private key (see resolveOwnerKey)
val ciRun = (prop("ciRun") ?: prop("buildNumber"))?.toIntOrNull()?.coerceAtLeast(0) ?: 0
val localSeq = (prop("localSeq")?.toIntOrNull() ?: 0).coerceIn(0, 99)
val ownerDebug = prop("ownerDebug")?.lowercase() == "true"
val shrink = prop("shrink")?.lowercase() != "false"
val ownerPackage = "dev.pocketagent.app"
// Version scheme: owner and owner-debug (10000+run)*100+seq; twin run*100+seq (never below 1).
// A local build (seq 1..99) can never pass the next CI run, so a CI build is never a downgrade.
val ownerVersionCode = (10000 + ciRun) * 100 + localSeq
val twinVersionCode = maxOf(ciRun * 100 + localSeq, 1)
val baseVersionName = "0.2.$ciRun" + (if (localSeq > 0) "-local$localSeq" else "")

// ---- Signing of the owner build (release type) ------------------------------------------------------------------
// Resolution, key by key: environment (AGIRI_KEYSTORE_FILE, AGIRI_KEYSTORE_PASSWORD, AGIRI_KEY_ALIAS, AGIRI_KEY_PASSWORD)
// over the properties file (storeFile, storePassword, keyAlias, keyPassword; file = -PownerSigning or
// <user.home>/.agiri/owner-signing.properties). All four present = the private key. None = the committed test key
// (sideload). Some but not all = the build FAILS with the names of the missing ones: never a silent fallback.
// A signing lineage (key rotation) is not a Gradle feature: AGIRI_SIGNING_LINEAGE is used by tools/ci/sign-owner.sh only.
class OwnerKey(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

// A release build is asked for by name (assembleRelease, bundleRelease ...) or by the umbrella tasks that include it.
val releaseRequested = gradle.startParameter.taskNames.any { raw ->
    val name = raw.substringAfterLast(':')
    name.contains("release", ignoreCase = true) || name == "assemble" || name == "build" || name == "bundle"
}
// Set when the key is partly configured or its file is missing but the task names did not say "release" (an IDE sync, a
// task run through a plugin). The release packaging tasks fail on it below, so the committed key never signs silently.
var ownerKeyProblem: String? = null

fun resolveOwnerKey(): OwnerKey? {
    val fileProps = Properties()
    val explicit = prop("ownerSigning")
    val propsFile = if (explicit != null) File(explicit)
        else File(File(System.getProperty("user.home"), ".agiri"), "owner-signing.properties")
    if (explicit != null && !propsFile.isFile) throw GradleException("AGIRI: -PownerSigning points at a file that does not exist: $propsFile")
    if (propsFile.isFile) propsFile.inputStream().use { fileProps.load(it) }
    fun pick(env: String, key: String): String? =
        System.getenv(env)?.trim()?.ifEmpty { null } ?: fileProps.getProperty(key)?.trim()?.ifEmpty { null }
    val store = pick("AGIRI_KEYSTORE_FILE", "storeFile")
    val storePw = pick("AGIRI_KEYSTORE_PASSWORD", "storePassword")
    val alias = pick("AGIRI_KEY_ALIAS", "keyAlias")
    val keyPw = pick("AGIRI_KEY_PASSWORD", "keyPassword")
    val have = listOf(store, storePw, alias, keyPw)
    if (have.all { it == null }) return null
    if (have.any { it == null }) {
        val missing = listOf("AGIRI_KEYSTORE_FILE or storeFile" to store, "AGIRI_KEYSTORE_PASSWORD or storePassword" to storePw,
            "AGIRI_KEY_ALIAS or keyAlias" to alias, "AGIRI_KEY_PASSWORD or keyPassword" to keyPw)
            .filter { it.second == null }.map { it.first }
        val msg = "AGIRI: the owner signing key is only partly configured; missing: ${missing.joinToString(", ")}. " +
            "Set all four, or none of them (then the committed test key is used)."
        // A debug build or an IDE sync must not fail over a half-set environment; the release packaging tasks do (see below).
        if (releaseRequested) throw GradleException(msg)
        ownerKeyProblem = msg
        return null
    }
    val storeName: String = store!!
    val ks = File(storeName).let { if (it.isAbsolute) it else File(rootDir, storeName) }
    if (!ks.isFile) {
        val msg = "AGIRI: the owner keystore does not exist: $ks"
        if (releaseRequested) throw GradleException(msg)
        ownerKeyProblem = msg
        return null
    }
    return OwnerKey(ks, storePw!!, alias!!, keyPw!!)
}

val ownerKey: OwnerKey? = resolveOwnerKey()
if (ownerKeyProblem != null) {
    val problem: String = ownerKeyProblem!!
    // Lazy failure: whatever route leads to packaging or signing the release type stops here, never a silent fallback.
    tasks.matching { it.name.startsWith("package") && it.name.endsWith("Release") ||
        it.name.startsWith("sign") && it.name.endsWith("ReleaseBundle") }.configureEach {
        doFirst { throw GradleException(problem) }
    }
}
val signingKindProp = prop("signingKind")?.lowercase()
if (signingKindProp != null && signingKindProp != "private" && signingKindProp != "committed") {
    throw GradleException("AGIRI: -PsigningKind must be private or committed, got: $signingKindProp")
}
// BuildConfig must tell the truth: a key found here signs the build; otherwise CI may say it signs afterwards.
val ownerSigningKind = if (ownerKey != null) "private" else (signingKindProp ?: "committed")
if (releaseRequested) {
    logger.lifecycle(if (ownerKey != null) "AGIRI: owner build signed with the private key"
        else "AGIRI: owner build signed with the committed test key" + (if (ownerSigningKind == "private") " (CI signs it with the private key afterwards)" else ""))
}

// Runtime inputs. In CI these point at real build products; locally they may be
// absent, in which case a clearly-marked stub is packaged instead.
//   -ProotfsArchive=/path/rootfs-arm64.tar.xz   (+ sibling rootfs-arm64.info)
//   -PprootDir=/path/with/libproot.so+libproot-loader.so (+ proot-build.info)
val rootfsArchivePath = providers.gradleProperty("rootfsArchive").orNull
val prootDirPath = providers.gradleProperty("prootDir").orNull

abstract class PrepareRuntimeTask : DefaultTask() {
    @get:Optional @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val rootfsArchive: RegularFileProperty
    @get:Optional @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val rootfsInfo: RegularFileProperty
    @get:Optional @get:InputDirectory @get:PathSensitive(PathSensitivity.NONE)
    abstract val prootDir: DirectoryProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val launcherScript: RegularFileProperty
    // The practice model and its starter (Wave 0): copies of rootfs/smoke/mock_llm.py and runtime/practice-start.sh.
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val practiceModel: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val practiceStart: RegularFileProperty
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
        // Asset names are PracticeModel.ASSET_SCRIPT and PracticeModel.ASSET_START.
        Files.copy(practiceModel.get().asFile.toPath(), File(assets, "practice_model.py").toPath(),
            StandardCopyOption.REPLACE_EXISTING)
        Files.copy(practiceStart.get().asFile.toPath(), File(assets, "practice-start.sh").toPath(),
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
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // The release type and the owner-debug variant keep this id; only the twin adds a suffix (buildTypes.debug below).
        // The namespace above stays dev.pocketagent.app for every variant: classes, R and BuildConfig keep their names.
        applicationId = ownerPackage
        minSdk = 26
        // Deliberate: apps targeting <= 28 may execve() files in app-private data,
        // which the extracted rootfs needs. See README.
        targetSdk = 28
        // The owner scheme is the default; the twin's code is set per variant in androidComponents below.
        versionCode = ownerVersionCode
        versionName = baseVersionName
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "OWNER_PACKAGE", "\"$ownerPackage\"")
        buildConfigField("int", "CI_RUN", "$ciRun")
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
        // The owner's private key: exists only when resolveOwnerKey found all four values.
        val key = ownerKey
        if (key != null) {
            create("owner") {
                storeFile = key.storeFile
                storePassword = key.storePassword
                keyAlias = key.keyAlias
                keyPassword = key.keyPassword
            }
        }
    }
    buildTypes {
        // debug = the twin, or owner-debug with -PownerDebug=true. Always the committed sideload key.
        debug {
            signingConfig = signingConfigs.getByName("sideload")
            if (!ownerDebug) {
                applicationIdSuffix = ".debug"
                versionNameSuffix = "-test"
            }
            buildConfigField("boolean", "TEST_HOOKS", "true")
            buildConfigField("boolean", "IS_TWIN", if (ownerDebug) "false" else "true")
            buildConfigField("String", "APP_VARIANT", if (ownerDebug) "\"owner-debug\"" else "\"twin\"")
            buildConfigField("String", "SIGNING_KIND", "\"committed\"")
            buildConfigField("String", "DEEP_LINK_SCHEME", if (ownerDebug) "\"agiri\"" else "\"agiri-test\"")
            manifestPlaceholders["deepLinkScheme"] = if (ownerDebug) "agiri" else "agiri-test"
            manifestPlaceholders["appVariant"] = if (ownerDebug) "owner-debug" else "twin"
        }
        // release = the owner's build: optimised (R8 and resource shrinking, switch -Pshrink=false), not debuggable.
        // Class and method names stay readable (-dontobfuscate in proguard-rules.pro) so crash traces in app.log make sense.
        release {
            signingConfig = if (ownerKey != null) signingConfigs.getByName("owner") else signingConfigs.getByName("sideload")
            isMinifyEnabled = shrink
            isShrinkResources = shrink
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "TEST_HOOKS", "false")
            buildConfigField("boolean", "IS_TWIN", "false")
            buildConfigField("String", "APP_VARIANT", "\"owner\"")
            buildConfigField("String", "SIGNING_KIND", "\"$ownerSigningKind\"")
            buildConfigField("String", "DEEP_LINK_SCHEME", "\"agiri\"")
            manifestPlaceholders["deepLinkScheme"] = "agiri"
            manifestPlaceholders["appVariant"] = "owner"
        }
    }

    // All twin overlays (label, marked launcher icons, shortcuts that target the twin) live in app/src/twin/res/, NOT in
    // app/src/debug/. They are attached to the debug source set by
    // android.sourceSets.getByName("debug").res.srcDir("src/twin/res") only when -PownerDebug is not set.
    // app/src/debug/res/values-en-rXZ/ is the stretched test language (TEXT's) and stays for both debug variants.
    if (!ownerDebug) {
        sourceSets.getByName("debug").res.srcDir("src/twin/res")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
        // The JVM maps file names through the locale; rootfs file names are UTF-8.
        unitTests.all { it.environment("LC_ALL", "C.UTF-8"); it.environment("POCKETAGENT_ROOTFS_ARCHIVE", System.getenv("POCKETAGENT_ROOTFS_ARCHIVE") ?: "") }
    }
    // Lint stays advisory; the release build is not blocked by it.
    lint { abortOnError = false; checkReleaseBuilds = false }
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val prep = tasks.register<PrepareRuntimeTask>("prepareRuntime$cap") {
            launcherScript.set(rootProject.layout.projectDirectory.file("runtime/pocketagent-proot.sh"))
            practiceModel.set(rootProject.layout.projectDirectory.file("rootfs/smoke/mock_llm.py"))
            practiceStart.set(rootProject.layout.projectDirectory.file("runtime/practice-start.sh"))
            rootfsArchivePath?.let {
                rootfsArchive.set(file(it))
                val infoFile = File(file(it).parentFile, file(it).name.replace(".tar.xz", ".info"))
                if (infoFile.exists()) rootfsInfo.set(infoFile)
            }
            prootDirPath?.let { prootDir.set(file(it)) }
            assetsOut.set(layout.buildDirectory.dir("generated/pa-runtime/${variant.name}/assets"))
            jniOut.set(layout.buildDirectory.dir("generated/pa-runtime/${variant.name}/jniLibs"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(prep, PrepareRuntimeTask::assetsOut)
        variant.sources.jniLibs?.addGeneratedSourceDirectory(prep, PrepareRuntimeTask::jniOut)
        // Only the twin has its own version scheme (run*100+seq); every other variant keeps the owner scheme set in defaultConfig.
        if (variant.buildType == "debug" && !ownerDebug) {
            variant.outputs.forEach { output -> output.versionCode.set(twinVersionCode) }
        }
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
    // On-device neural TTS engine (Supertonic 3 INT8 multilingual EN/FR/TR). Stripped to arm64-v8a via abiFilters.
    implementation("k2-fsa:sherpa-onnx:2.0.3") {
        artifact {
            name = "sherpa-onnx-static-link-onnxruntime"
            type = "aar"
        }
    }

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
