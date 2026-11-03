# ✦ Astral Islands

[![CI](https://github.com/SHOOP111/astral-islands/actions/workflows/ci.yml/badge.svg)](https://github.com/SHOOP111/astral-islands/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![Paper 1.21.4](https://img.shields.io/badge/Paper-1.21.4-blue)](https://papermc.io/)
[![License MIT](https://img.shields.io/badge/license-MIT-purple)](LICENSE)

**A self-contained co-op skyblock game mode about building a home among the stars.**
Handcrafted floating origins, an evolving ore economy, rotating contracts, constellation progression,
and expeditions that return even while your crew is offline. Built as a Java 21 / Gradle plugin for **Paper 1.21.4**.

No economy plugin, schematic loader, NMS, or packet library required. This is source code you can own,
extend, inspect, and operate—not a collection of advertised features with empty implementations.

## What you can play

| System | Implemented gameplay |
|---|---|
| **Three origins** | Verdant Grove, Crystal Sanctuary, Ember Bastion; trees, hanging terrain, warm/cool lighting, starter chest |
| **Co-op islands** | Owner → steward → builder permissions, expiring invitations, offline member management, atomic ownership transfer |
| **Protected sky** | Stable 512-block plot grid, upgradeable 32–128 block radius, private/public visits, safe async home travel, void rescue |
| **Treasury** | Integer wallets, shared banks, deposits/withdrawals, player payments, daily streak rewards, audit history |
| **Engineering** | Horizon expansion, five generator tiers including tier zero, four expedition docks including the starter dock |
| **Living generator** | Real water/lava cobble and stone formation can become ores; persistent provenance prevents placed-ore reward farming |
| **Daily contracts** | Three of four shared mining, farming, fishing, and natural-monster objectives; UTC rotation and one-time claims |
| **Constellations** | Five stars, unlocked every ten levels; larger crews, stronger contract returns, more expedition discoveries |
| **Attunement** | Solar income, Lunar rarities/discoveries, or Verdant XP; meaningful 500-coin / six-hour switching constraint |
| **Star expeditions** | Drift, Nebula, Eclipse; durable, pre-rolled discoveries, offline countdowns, atomic return collection |
| **Presentation** | Clickable inventory dashboard and submenus, paginated sky atlas, sounds, optional non-invasive sidebar |
| **Operations** | SQLite WAL/FULL transactions, single bounded writer, immutable indexed snapshots, restart recovery, schema downgrade refusal |
| **Extension API** | Bukkit service exposing immutable island/profile values, post-commit ready and activity-level events |

## Install

1. Run **Paper 1.21.4** on **Java 21**. Other Minecraft versions and Folia are **not supported**.
2. Download `astral-islands-1.0.0.jar` from [Releases](https://github.com/SHOOP111/astral-islands/releases),
   or the `AstralIslands-plugin` artifact from a successful [Actions run](https://github.com/SHOOP111/astral-islands/actions).
3. Put the **shaded** JAR in `plugins/`, then start the server. Do not install the `-thin.jar`.
4. Use `/is` and choose your origin. `/is help` lists all commands.
5. Back up the database **and** world together. Try the plugin on staging before opening a live economy.

The plugin creates `astral_islands/` and `plugins/AstralIslands/astral.db`. It never edits your normal world's
terrain. Its wallets are intentionally independent of Vault or another server economy.

### First session

- `/is create crystal` → travel to your origin.
- Take the lava bucket and ice from the chest; build a normal cobblestone generator.
- Mine tracked generator blocks for island XP and bank income. Placed ores grant neither.
- `/is daily` → claim wallet coins. `/is bank deposit 1000` → invest in the crew's treasury.
- `/is upgrade generator` → unlock copper/iron and progressively rarer ores.
- `/is contracts` → inspect today's shared objectives; stewards/owners claim completed contracts.
- `/is expedition launch drift` → invest 600 bank coins in a five-minute expedition.
- `/is invite Friend` → friend uses `/is accept`. `/is visitors` opens your island to visitors.

## Commands & permissions

See [the command reference](docs/COMMANDS.md) for all syntax and authorization.

- `astral.use`: default **everyone**; `/is`, `/island`, `/skyblock`, `/astral`.
- `astral.admin`: default **operators**; diagnostics, latest ten audit records, wallet grants.
- `astral.bypass`: default **false**, **not** implicitly granted to operators; explicitly bypasses protection.

No destructive island-delete command is shipped. Plot indices are monotonic, ownership cannot be abandoned,
and repeatedly deleting/recreating a starter chest is not a supported resource loop.

## Remote-only build and testing

All project validation is automated in [`.github/workflows/ci.yml`](.github/workflows/ci.yml):

1. Gradle wrapper validation and Java 21 provisioning.
2. Compilation and JUnit tests on **GitHub-hosted Ubuntu and Windows**.
3. JaCoCo reports and an enforced **80% domain line-coverage** floor.
4. Shadow packaging of the standalone plugin and artifact upload.
5. A **real Paper 1.21.4 server**, bound to localhost, boots twice on GitHub-hosted Ubuntu.
   The live probe checks terrain, starter inventory, safe home, committed balances, generator provenance,
   fluid boundary cancellation, API registration, and persistence across a clean server restart.
6. A pushed `v*` tag publishes a release JAR and SHA-256 checksum **only after both build jobs and the live probe pass**.

Use **Actions → CI → Run workflow** to validate changes without compiling or testing on your computer.
The CI server is ephemeral and accepts the [Minecraft EULA](https://www.minecraft.net/eula) solely for the server probe.
CI logs, JUnit results, and coverage HTML/XML are downloadable artifacts. Tests do not require Bukkit mocks.

## Design, progression & operations

- [Game design and exact progression](docs/GAMEPLAY.md)
- [Architecture, consistency guarantees, and limitations](docs/ARCHITECTURE.md)
- [Configuration, backups, recovery, and compatibility](docs/OPERATIONS.md)
- [Read-only API and events](docs/API.md)
- [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Changelog](CHANGELOG.md)

### Scope, honestly

This release is for **one Paper server**, not a proxy network or Folia. There is no auction house,
Vault bridge, custom resource pack, external schematic import, island reset, season reset, or custom dungeon world.
Automated validation does not replace multi-player, adversarial, performance, and plugin-compatibility testing
on a staging server. World state and SQLite are two different storage systems; financial commands are transactional,
but they cannot form one atomic transaction with Minecraft's region files. See the consistency notes before operation.

**MIT licensed.** Minecraft and Paper are their respective owners' projects; no affiliation is implied.
