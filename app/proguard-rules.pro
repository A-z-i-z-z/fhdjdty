# Agiri optimised build (the release type, the owner's build). Names stay readable on purpose:
# crash traces in app.log must be readable without a mapping file.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
# sherpa-onnx: the native code looks up classes, fields and methods by name (see the JNI trap in AGENTS.md)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
# enums stored in preferences or JSON by name, read back with valueOf / entries
-keepclassmembers enum dev.pocketagent.app.** { <fields>; public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
# the rootfs extractor reads tar and xz through commons-compress and xz
-keep class org.apache.commons.compress.archivers.tar.** { *; }
-keep class org.apache.commons.compress.compressors.xz.** { *; }
-keep class org.tukaani.xz.** { *; }
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
# the first R8 run lists anything else it misses in app/build/outputs/mapping/release/missing_rules.txt: copy those -dontwarn lines here
