# Combatant sound system

Sound catalogs live next to their owner and are discovered automatically by ClassGraph.
There is no central registration list. A new WAV or OGG sound needs one annotated enum
constant and the resource itself:

```java
@SoundCatalog(namespace = "combatant", root = "sounds/my_module", idPrefix = "my_module")
private enum MySound implements SoundKey {
    @SoundAsset("alert.ogg")
    ALERT
}
```

Normal UI-style playback stays lightweight and relative to the listener:

```java
MySound.ALERT.play();
MySound.ALERT.play(SoundOptions.gain(0.75));
```

Positional playback is opt-in. Stereo input is cached as a mono buffer for spatial
playback because OpenAL only spatializes mono sources:

```java
SoundSpatialization spatial = SoundSpatialization.at(entity.position())
        .withVelocity(entity.getDeltaMovement())
        .withDistances(1.0f, 64.0f, 1.0f)
        .withCone(lookDirection, 60.0f, 120.0f, 0.15f)
        .withEnvironment(1.5f, 0.25f);

SoundInstance instance = MySound.ALERT.play(
        SoundOptions.DEFAULT.withSpatialization(spatial)
);
instance.setPosition(entity.position());
instance.stop();
```

The optional equalizer maps to the complete OpenAL EFX four-band equalizer. It is
bypassed when not requested or when the device has no `ALC_EXT_EFX` support:

```java
SoundEqualizer eq = SoundEqualizer.FLAT
        .withLow(4.0f, 180.0f)
        .withMid1(-2.5f, 650.0f, 0.7f)
        .withMid2(2.0f, 3_200.0f, 0.5f)
        .withHigh(-3.0f, 8_000.0f)
        .withWetMix(1.0f);

MySound.ALERT.play(SoundOptions.DEFAULT.withEqualizer(eq));
```

`SoundPlayEvent` is cancellable and exposes a mutable `SoundRequest`. Lifecycle events
also include `SoundStartedEvent`, `SoundStoppedEvent`, `SoundLoadedEvent`, and
`SoundErrorEvent`; listeners use the regular `@EventHandler` bus API.
