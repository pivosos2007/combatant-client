Javet V8 native binaries are copied here by the Gradle task:

    ./gradlew syncJavetNatives

Release builds do not require committing those binaries manually: processResources
runs unpackJavetNatives and places the resolved javet-v8-* artifacts under
/javet/natives in generated resources. The bundle includes desktop x86_64,
Linux/macOS ARM64, and Android ARM64 from javet-v8-android (AAR).
Android resources retain the android/arm64-v8a/ prefix to distinguish their ABI.
Combatant detects Android even on OpenJDK launchers such as FCL/Pojav and extracts
the Android binary under the filename Javet's JNI classloader expects. Linux
binaries are never used as a fallback on Android. Android testing must include
V8 runtime creation on a device; desktop tests only verify selection/packaging.
