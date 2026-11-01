# Filesystem backend (26.3, UI deferred)

This implements the backend portion of `docs/123.md`. ClickGUI, schematic screens,
localizations, input/focus handling and `ConfigFileDialogs` remain unchanged at
the user's request. No directory is relocated merely by installing this build.

## Configuration storage

`ConfigPaths.root()` resolves through `ConfigurationRootManager` on first use.
Resolution and recovery finish before a consumer can cache a path. The root is
immutable for the running process. Existing caches in serializers, profile,
theme, preset, proxy, map, chat and script storage therefore remain valid.
Addon state and addon-owned directories now use this root too; the public addon
`configDir()` API retains its meaning of the instance-wide Fabric config directory.

The default is `<gameDir>/config/combatant`. Bootstrap metadata and its lock are
outside that tree: `<gameDir>/config/combatant-storage.properties` and
`combatant-storage.lock`. Paths come from Fabric's actual instance directory.
A new default installation does not need bootstrap metadata until it schedules
a relocation. Each root also contains `.combatant-root.lock`, excluded from
the transferred data. The runtime holds locks until OS process teardown so
concurrent shutdown hooks cannot write after the lock has been released.
Locks coordinate versions of Combatant implementing this protocol; older builds
and third-party programs do not participate.

Backend API:

```java
var manager = ConfigPaths.manager();
Path exactDestination = manager.destinationUnder(selectedParent);
// The future UI must show source and exactDestination and obtain confirmation first.
manager.schedule(exactDestination);
// Alternatively, schedule the default destination under the same conflict rules:
manager.restoreDefault();
// Before exiting, a scheduled operation can be cancelled:
manager.cancelScheduled();
```

Only one migration can be pending. Scheduling persists PREPARING and does not
change active writers. The next process acquires the instance and source locks,
builds a complete manifest (including unknown files and empty directories),
checks destination conflicts, nesting, links/reparse points, accessibility and
available space, then copies into a unique target-filesystem staging directory.
Source and staging trees must match the size/SHA-256 manifest before COMMITTING.
The staging rename refuses replacement. The destination is locked and verified
again before publishing the new active root and COMMITTED in one metadata write.
Original files are never deleted. Restoring the default therefore rejects the
preserved original directory until the operator explicitly moves that backup
elsewhere while the client is stopped; it does not merge or overwrite it.

Metadata is fsynced and atomically replaced; filesystems lacking atomic metadata
replacement fail closed. File contents are forced before activation. Directory
fsync is used where Java exposes it (not Windows). Actual durability on network
or removable storage also depends on the filesystem/server's guarantees.

Interrupted COPYING/VERIFYING is restarted with a new staging directory, leaving
the abandoned copy intact. COMMITTING verifies the journal's manifest and
continues either before or after the final directory rename. After COMMITTED,
the destination is authoritative and subsequent ordinary saves are permitted;
it is not compared with the old migration manifest on every startup.

Failures preserve the source pointer and record `lastError` when metadata can
still be written. An inaccessible active custom root or corrupt bootstrap stops
initialization; it never falls back to an empty default configuration. Correct
the underlying disk/permission problem and restart to retry. Before activation,
`ConfigurationRootManager.cancelPendingMigration(gameDir)` offers offline
recovery: it acquires both locks, keeps the source active and preserves all
staging/destination files. It refuses an already committed migration. Corrupt
bootstrap files require operator recovery from known metadata; guessing a root
is deliberately unsupported. Abandoned staging directories and manifest files
are retained for manual review rather than recursively deleted.

## Native dialogs

Per the user's follow-up decision, SDL3 replaces the original specification's
NFD requirement. No NFD dependency, additional LWJGL core, or extra native binary
is bundled. Minecraft 26.3 already provides LWJGL SDL3 and its native runtime.

`NativeFileDialogService` exposes folder, single-file and save dialogs with
extension filters, normalized `Path` results and distinct selection, cancellation,
native failure, invalid-path and busy results. Supply
`Minecraft.getInstance()::schedule` as the result executor. Filesystem validation
runs on an I/O worker; dialog invocation is dispatched to SDL's main thread using
`SDL_RunOnMainThread` if the client executor is not already on that thread.
SDL's dialog APIs return asynchronously, leaving rendering unblocked.

SDL owns initialization, platform backend selection and shutdown. The service
checks the host's video initialization and never calls SDL_Init or SDL_Quit.
On Linux, SDL can require the desktop portal, D-Bus and the host SDL event pump.
Native failures preserve SDL_GetError details and are logged/reported as results.
The callback copies SDL's UTF-8 path before SDL frees its result list. Request
filter buffers survive until that callback and are then freed. Two shared callback
trampolines have process lifetime, avoiding freeing executable callback memory
while a native worker is still returning through it. Cancelling the Java future
does not prematurely release resources or unblock another dialog.

The existing SDL `.cbcfg` UI dialogs remain intact. Adapting `ConfigFileDialogs`
to this common service and unifying its busy state is deferred with UI changes.
The new service uses unparented dialogs, matching the existing implementation;
window ownership, fullscreen and focus/input behavior still need in-game review.
No GLFW dependency or window-handle assumption was introduced.

Primary references:

- [SDL open-file dialog and threading contract](https://wiki.libsdl.org/SDL3/SDL_ShowOpenFileDialog)
- [SDL folder selection](https://wiki.libsdl.org/SDL3/SDL_ShowOpenFolderDialog)
- [SDL save-file dialog](https://wiki.libsdl.org/SDL3/SDL_ShowSaveFileDialog)
- [Callback ownership](https://wiki.libsdl.org/SDL3/SDL_DialogFileCallback)

## Schematics

`SchematicLibrary.changeDirectory(Path)` and `restoreDefaultDirectory()` run
on its existing serial worker and refresh its header cache/list. They do not
change `SchematicState` or move any existing schematic. The independent override
is `<gameDir>/config/combatant-schematics.properties`; the default remains
`<gameDir>/schematics`. An unavailable override reports a failure without creating
an empty replacement directory.

`SchematicLibrary.importFile(Path)` copies to an isolated temporary directory,
parses the copied bytes with the existing `SchematicFormats.read` implementation,
then publishes without replacement and refreshes the library. Duplicate names
become `name (1).ext`, etc. Invalid files never enter the library list; originals
are preserved. Arbitrary-path loading continues to use the existing `load(Path)`.
The future picker must derive filters from `SchematicFormats.EXTENSIONS`
(`.litematic`, `.schem`, `.nbt`), then pass selections to `SchematicActions.load`.
There is no new parser or dependency on optional Litematica/MaLiLib.

## NativeMemoryGuard archive

Git tag `archive/native-memory-guard-26.3-20261031` preserves the original Java,
C++, CMake, README, Windows binary and surrounding historical source tree.
The active implementation, JNI binary, startup/shutdown calls, runtime setting,
MainConfig accessors, metadata field and old distribution filtering were removed.
Existing serialized keys are harmless unknown values under the existing config
loader. UI localization entries and the historical UI comment were intentionally
left alone; they do not enable or package the removed implementation.

## Deferred UI work and validation boundaries

No screen, control, translation or UI-runtime file was edited. This includes the
addon-manager button that still opens the old default addon folder. Update that
button, adapt `ConfigFileDialogs`, add configuration confirmation/recovery controls
and wire schematic file/folder actions during the later UI task.

Automated backend tests cover migration persistence and late saves, source/unknown
file preservation, conflicts, root locks, unavailable roots, cancellation,
interrupted copying, commit recovery, corrupt copies/metadata, restore-default,
schematic directory persistence and import failure/duplicates. Dialog backend tests cover client dispatch, rejection, overlapping requests and
Java-future cancellation without displaying windows. Packaging checks cover
Windows and Linux artifacts; these are not Linux runtime tests.

Still requires real Minecraft/desktop sessions: open/save/folder selection and
cancellation, focus/fullscreen recovery, Windows native interaction, Linux X11
and Wayland with portal and missing-service cases, actual disk-full/permission
faults, mounted/network filesystems, and all supported schematic parser/preview
workflows. This Windows workspace has no available Linux desktop test session.
