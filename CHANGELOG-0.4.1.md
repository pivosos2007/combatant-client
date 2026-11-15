## 0.4.1

Bug-fix release for 0.4.0.

For the main changelog, see the [0.4.0 release notes](https://github.com/pivosos2007/combatant-client/releases/tag/0.4.0).

### Nullability fixes

- Fixed **ElytraTarget** throwing a `NullPointerException` after player death when checking an absent target against an immutable candidate list. Target selection now handles missing KillAura and previous targets safely.
- **ElytraTarget** now resets pursuit and queued flight actions when the player dies or is removed.
- Added missing null checks to boat-control methods in **EntityFlight** and **EntityControl**. EntityControl no longer reports Vulcan motion support for an absent vehicle.

### Rendering fixes

- Fixed OpenGL capability detection to use the extensions actually selected by the rendering backend.
- Guarded optional OpenGL memory-barrier, shader-storage, image-query, and 3D-texture operations against unavailable support or entry points.
- Fixed Vulkan shader capability detection to respect features enabled on the logical device.

### Internal maintenance

- Added regression tests for target selection with missing targets and immutable candidate lists, and for optional GPU capabilities.
