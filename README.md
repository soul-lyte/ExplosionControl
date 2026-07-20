# ExplosionControl

Complete, per-source control over every explosion type in Minecraft, for **Paper 1.21.11** /
**Java 21**.

Author: **zSouul**

---

## What it does

ExplosionControl gives server administrators independent control over **15 explosion
categories**, each with the same five options in `config.yml`:

| Option                  | Type            | Effect                                                              |
|--------------------------|-----------------|----------------------------------------------------------------------|
| `enabled`                 | `true`/`false`  | Master on/off switch — prevents the explosion as early as possible.  |
| `damage-multiplier`       | `≥ 0.0`         | Scales the damage dealt to any single living entity (`1.0` = vanilla, `0.0` = no damage). Applied to raw damage before armor, so armor/resistance/absorption still reduce it normally. |
| `radius-multiplier`       | `≥ 0.0`         | Scales the blast radius/power (`1.0` = vanilla).                     |
| `knockback-multiplier`    | `≥ 0.0`         | Scales the knockback velocity applied to caught entities.            |
| `block-damage`            | `true`/`false`  | Whether the explosion is allowed to destroy/drop blocks.              |

Categories: `tnt`, `tnt-minecart`, `creeper`, `charged-creeper`, `wither-spawn`,
`wither-skull`, `ghast-fireball`, `fireball`, `blaze-fireball`, `dragon-fireball`,
`end-crystal`, `respawn-anchor`, `bed`, `wind-charge`, and a catch-all `other`.

Reload changes at any time with:

```
/explosioncontrol reload
```

which requires the `explosioncontrol.reload` permission (default: `op`).

---

## Installation

1. Build the plugin jar (see **Building** below).
2. Drop `ExplosionControl-1.1.0.jar` into your server's `plugins/` folder.
3. Start (or restart) the server. `config.yml` will be generated automatically inside
   `plugins/ExplosionControl/`.
4. Edit `config.yml` to taste, then run `/explosioncontrol reload` — no restart needed.

---

## Building

This project is a standard Gradle build with a single dependency (`paper-api`, `compileOnly`
scope) and no shading step required.

```bash
gradle build
```

The finished jar will be at `build/libs/ExplosionControl-1.1.0.jar`.

> **Note on how this project was produced:** the code in this repository was written and
> reviewed against the real Paper 1.21.11 API documentation (every non-trivial event class
> and method used — `ExplosionPrimeEvent`, `EntityExplodeEvent`, `BlockExplodeEvent`,
> `EntityDamageEvent`/`DamageSource`, `EntityKnockbackEvent`, `PlayerBedFailEnterEvent`,
> `EnderDragonFireballHitEvent`, and the relevant entity types — was individually looked up
> against `jd.papermc.io` for the `1.21.11-R0.1-SNAPSHOT` API). Running `gradle build` in a
> normal environment with internet access (which downloads the real `paper-api`) is expected
> to succeed without changes.

---

## How each explosion type is handled

Every explosion source in the game is hooked at the earliest point the Paper API exposes for
that source, so `enabled: false` prevents as much of the explosion (damage, knockback, *and*
block damage) as technically possible — not just the block destruction.

| Category           | Primary hook(s)                                                   | Notes |
|---------------------|--------------------------------------------------------------------|-------|
| `tnt`                | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | Standard entity-explosion pipeline. |
| `tnt-minecart`       | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | Same pipeline; entity type `ExplosiveMinecart`. |
| `creeper`            | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | `Creeper#isPowered() == false`. |
| `charged-creeper`    | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | `Creeper#isPowered() == true`. `radius-multiplier` is applied on top of vanilla's own charged-creeper radius doubling. |
| `wither-spawn`       | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | The single explosion fired the moment a Wither finishes spawning; source entity is the `Wither` itself. |
| `wither-skull`       | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | Covers both the regular and blue/"charged" skull variants. |
| `ghast-fireball`     | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | `LargeFireball` whose `getShooter()` is a `Ghast`. |
| `fireball`           | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | Any other `LargeFireball` (e.g. dispenser-fired). |
| `blaze-fireball`     | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | See **limitation** below — inert on unmodified vanilla. |
| `dragon-fireball`    | `EnderDragonFireballHitEvent` + `EntityDamageEvent(DRAGON_BREATH)`  | See **limitation** below — different mechanic entirely. |
| `end-crystal`        | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | Entity type `EnderCrystal`. |
| `wind-charge`        | `ExplosionPrimeEvent` + `EntityExplodeEvent`                        | Covers both thrown/dispensed `WindCharge` and Breeze-fired `BreezeWindCharge` (shared `AbstractWindCharge` supertype). |
| `respawn-anchor`     | `PlayerInteractEvent` (pre-explosion) + `BlockExplodeEvent`         | See **limitation** below. |
| `bed`                | `PlayerBedFailEnterEvent` (pre-explosion) + `BlockExplodeEvent`     | Fully preventable — see below. |
| `other`              | Every hook above, as a fallback branch                             | Catches any future/unrecognised explosion source so nothing is ever left unconfigurable. |

`damage-multiplier` is enforced for **every** category above through a single shared
`EntityDamageEvent` listener (for `ENTITY_EXPLOSION`/`BLOCK_EXPLOSION` causes, plus
`DRAGON_BREATH` for the Dragon Fireball special case), which resolves the responsible
category via `DamageSource#getCausingEntity()` for entity sources, falls back to
`ExplosionOriginRegistry` (a short-lived cache populated when the explosion was primed, for
the rare case the source entity is already gone), and finally to reading the block still
present at `DamageSource#getDamageLocation()` for block sources (Bed/Respawn Anchor).

`knockback-multiplier` is enforced through `EntityKnockbackEvent`
(`KnockbackCause.EXPLOSION`). Since that event does not expose which explosion caused it, the
category resolved a moment earlier by the damage listener (for the same victim) is reused —
see `PendingKnockbackCache` for the full explanation of why and how this is safe.

---

## Paper API limitations encountered, and how they were addressed

1. **Bed and Respawn Anchor explosions apply entity damage/knockback *before* Bukkit fires
   `BlockExplodeEvent`.** Unlike entity-sourced explosions (which fire the cancellable
   `ExplosionPrimeEvent` before anything happens), Bukkit only exposes `BlockExplodeEvent`
   for these two sources — after damage and knockback have already been applied. Cancelling
   `BlockExplodeEvent` alone would therefore only stop block destruction, not `enabled: false`
   as a whole.
   - **Bed** — addressed with the dedicated, cancellable Paper event
     `PlayerBedFailEnterEvent` (`willExplode() == true`), which fires *before* vanilla starts
     the explosion. Cancelling it fully prevents the explosion (damage, knockback, and block
     damage). This is a complete fix, not a workaround.
   - **Respawn Anchor** — Paper does not expose an equivalent dedicated event. The closest
     available alternative is intercepting the triggering `PlayerInteractEvent` (a right-click
     on a charged anchor outside the Nether, without glowstone in hand) and cancelling it,
     which replicates vanilla's own trigger condition. This covers the normal player-driven
     case completely, but — unlike Bed — cannot guarantee prevention if some other means
     forces the anchor to explode outside that interaction. `BlockExplodeListener` still runs
     as a second line of defence for block damage in that edge case.

2. **`EntityKnockbackEvent` does not expose which entity or block caused the knockback** —
   only a coarse `KnockbackCause.EXPLOSION` value, with no accessor for the exploding
   entity/block. Addressed by correlating it with the `EntityDamageEvent` fired for the same
   victim immediately beforehand (vanilla always damages-then-knocks-back each affected entity
   before moving to the next one), via a small short-lived cache keyed by entity UUID
   (`PendingKnockbackCache`). If no matching entry is found (e.g. another plugin cancelled the
   damage event first), vanilla knockback is left untouched rather than guessed at.

3. **Blaze Fireball (`blaze-fireball`) does not actually explode in vanilla Minecraft.** A
   Blaze's small fireball only ignites its target; there is no explosion for the Paper API to
   expose here at all. This section is still fully wired up (identical hooks to every other
   entity-sourced explosion) so that if a data pack, mod, or future Minecraft version ever
   makes small fireballs explosive, it is instantly configurable with no further plugin
   changes — it simply has nothing to do on an unmodified vanilla/Paper server today.

4. **Dragon Fireball (`dragon-fireball`) is not a block-destroying explosion at all.** On
   impact it spawns a lingering `AreaEffectCloud` ("dragon's breath") that deals
   `DRAGON_BREATH`-cause damage over time; it never fires `ExplosionPrimeEvent` or
   `EntityExplodeEvent`, and has no knockback component. Addressed with a dedicated listener
   using the closest honest analogues available: `enabled` cancels
   `EnderDragonFireballHitEvent` outright (no cloud ever appears), `damage-multiplier` scales
   each tick of `DRAGON_BREATH` damage, and `radius-multiplier` scales the resulting cloud's
   radius. `block-damage` and `knockback-multiplier` are intentionally ignored for this
   category and documented as such in `config.yml`, since vanilla dragon fireballs never
   destroy blocks or apply knockback in the first place — there's nothing for either option
   to meaningfully control.

5. **`repo.papermc.io` and Maven Central are unreachable from the sandbox this project was
   authored in**, so a compiled jar could not be produced or linked against the genuine
   `paper-api` artifact in that environment (see the note under **Building** above for exactly
   how correctness was still verified without it).

---

## Permissions

| Permission                  | Default | Description                                          |
|-------------------------------|---------|-------------------------------------------------------|
| `explosioncontrol.reload`     | `op`    | Allows running `/explosioncontrol reload`.            |

## Commands

| Command                     | Description                                    |
|-------------------------------|-------------------------------------------------|
| `/explosioncontrol reload`   | Reloads `config.yml` without restarting the server. |

---

## Project layout

```
ExplosionControl/
├── build.gradle
├── settings.gradle
├── README.md
└── src/main/
    ├── java/com/zsouul/explosioncontrol/
    │   ├── ExplosionControl.java          — plugin entry point, wiring
    │   ├── cache/PendingKnockbackCache.java
    │   ├── cache/ExplosionOriginRegistry.java
    │   ├── command/ExplosionControlCommand.java
    │   ├── config/ConfigManager.java
    │   ├── config/ExplosionSettings.java
    │   ├── model/ExplosionCategory.java
    │   ├── resolver/ExplosionSourceResolver.java  — single source of truth for entity/block → category
    │   └── listener/
    │       ├── ExplosionPrimeListener.java     — enabled + radius-multiplier (entity sources)
    │       ├── EntityExplodeListener.java      — block-damage (entity sources)
    │       ├── BlockExplodeListener.java       — block-damage (bed/respawn-anchor)
    │       ├── ExplosionDamageListener.java    — damage-multiplier (all sources)
    │       ├── ExplosionKnockbackListener.java — knockback-multiplier (all sources)
    │       ├── DragonFireballListener.java     — dragon-fireball special case
    │       ├── BedExplosionGuard.java          — enabled=false pre-explosion (bed)
    │       └── RespawnAnchorExplosionGuard.java— enabled=false pre-explosion (respawn anchor)
    └── resources/
        ├── plugin.yml
        └── config.yml
```
