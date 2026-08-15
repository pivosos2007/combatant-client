# Combatant Memory Guard

This JNI library provides process-wide user-mode hardening for Windows and
Linux. macOS is intentionally unsupported.

## Client resources

The regular Gradle build does not compile native code. Release binaries are
stored directly in `src/main/resources/combatant/nativeguard` and Gradle packs
them into the Combatant JAR as ordinary resources.

- Windows resource: `combatant/nativeguard/windows-x86_64/combatant_memory_guard.dll`
- Linux resource: `combatant/nativeguard/linux-x86_64/libcombatant_memory_guard.so`

The Windows build requires CMake, Visual Studio 2022 C++ tools, a Windows SDK,
and a JDK with JNI headers. The Linux build requires CMake, a C++17 compiler,
and JDK development headers. `JAVA_HOME` should point to that JDK.

## Manual Windows build

```powershell
cmake -S src/main/native/memoryguard -B build/native/memoryGuard -A x64 -DJAVA_HOME="C:/path/to/jdk"
cmake --build build/native/memoryGuard --config Release
```

## Manual Linux build

```bash
cmake -S src/main/native/memoryguard -B build/native/memoryGuard -DCMAKE_BUILD_TYPE=Release -DJAVA_HOME="$JAVA_HOME"
cmake --build build/native/memoryGuard --config Release
```

Build artifacts are host-specific. After a manual build, copy the resulting
binary to its resource path above before packaging the client. A Windows build
does not produce the Linux SO.

## Security boundary

On Windows the guard denies new same-user process handles with VM read/write,
VM operation, remote-thread, duplication, suspension, and DACL replacement
rights. On Linux it disables core dumps and marks the process non-dumpable.

This cannot revoke a Windows handle opened before activation and cannot stop an
administrator/root process, `SeDebugPrivilege`, `CAP_SYS_PTRACE`, a kernel
driver, or a hypervisor.
