Javet V8 native binaries are copied here by the Gradle task:

    ./gradlew syncJavetNatives

Release builds do not require committing those binaries manually: processResources
runs unpackJavetNatives and places the resolved javet-v8-* artifacts under
/javet/natives in generated resources.
