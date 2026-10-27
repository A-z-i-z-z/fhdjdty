# Called from native code by name (see jni_bridge.cpp).
-keep interface app.yerel.engine.LoadProgress { *; }
-keep interface app.yerel.engine.UpdateListener { *; }
-keepclassmembers class * implements app.yerel.engine.LoadProgress { boolean onProgress(float); }
-keepclassmembers class * implements app.yerel.engine.UpdateListener { void onUpdate(byte[]); }
-keepclasseswithmembernames class * { native <methods>; }
-keep class app.yerel.engine.NativeBridge { *; }
