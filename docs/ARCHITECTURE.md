# Architecture and consistency

## Boundaries

```
Paper events / commands / menus (server thread)
                  │ immutable inputs
                  ▼
Engine: bounded queue, one ledger writer
                  │ pure Rules → candidate Universe
                  ▼
SqliteStore: changed records + allocation + audit in one transaction
                  │ COMMIT succeeds
                  ▼
volatile immutable Universe publication
                  │ futures → server-thread callback
                  ▼
messages / world jobs / menus / API events
```

- `domain/`: immutable aggregates, role/financial rules, stable grid math, ore distribution and dependency-free schematic plans.
- `service/Engine`: serializable command order and lock-free snapshot reads; never invokes Bukkit on its worker.
- `storage/`: a single SQLite JDBC connection after startup, prepared statements, JSON records, WAL and FULL synchronous mode.
- `paper/`: server-owned inventory sessions, protection, chunk-backed node provenance, batched builds, asynchronous chunk travel and presentation.
- `api/`: read-only Bukkit service and server-thread post-commit events.

Every Universe rebuild creates O(1) membership and plot-cell lookup indexes. Snapshot mutation currently copies
aggregate maps; it is intentionally simple, but costs O(island/profile count) per transaction. Benchmark before
operating very large servers. Island construction is tick-budgeted, but `world.save()` is a synchronous durability
barrier and may cause a spike on large worlds. This is not a distributed, sharded or unlimited-scale design.

## Guarantees

- Wallet-to-bank and player-to-player transfers are committed together; observers never see half a transfer.
- Rule or database failure leaves the published revision unchanged. Financial success is reported only after COMMIT.
- Simultaneous launches, claims, member joins and transfers are evaluated serially against the latest committed state.
- Audit insertion and plot allocation are in the same transaction as aggregate changes.
- Collections exposed to addons are immutable. No Bukkit `Player`, `World`, `ItemStack` or `Location` lives in the domain.
- Upgrade bounds, ownership uniqueness, membership uniqueness, balances and nonnegative progress are validated.
- Closing drains accepted ledger jobs before closing JDBC; new work is rejected.
- Unknown newer database schemas and malformed membership/plot data fail startup instead of wiping data.

## World lifecycle and crash boundaries

Creating an island first reserves a never-reused plot and persists `PROVISIONING`.
The server thread asynchronously loads the four starter chunks, pins them, and applies the deterministic schematic
in bounded slices. It fills the chest and saves the world, then commits `READY`. Only ready islands are accessible.
A normal restart replays unfinished provisioning; replay overwrites only the starter schematic, never another plot.

Minecraft world files and the SQLite database are **not one atomic storage system**. Disk corruption, partial backups,
unclean OS crashes, another plugin editing terrain, or an IO failure in Minecraft saving can still create divergence.
Financial commands are write-through, but activity rewards are buffered for 1–10 seconds (default 3).
A crash before the activity flush loses those recent XP/contract/income increments. Failed activity commits are logged
and not automatically retried; retrying without durable event IDs could duplicate rewards.

Node provenance lives with chunk data. It survives clean restarts and consumes once per successful break.
It cannot provide a transactional guarantee across a hard crash between a world block break and a chunk save.
External WorldEdit-like mutations do not fire every vanilla event; such administrative edits are trusted and can leave
stale node tags. Do not expose editing plugins, world files, SQL, API class loaders, or bypass permission to untrusted users.

## Protection policy

Outside the sky world, Astral leaves other gameplay alone. Inside it:

- Members build/use containers on their ready island; visitors are read-only. Unallocated space denies building.
- Private/unfinished island entry is denied; revoked access is periodically evacuated.
- Buckets, hanging entities, armor stands, crops, entity interaction/damage and item pickup are checked.
- Fluids, growing structures, spreading blocks, sponges, fertilization, pistons and hoppers may not cross plots.
- Explosions cannot change island terrain, fire does not spread, mob block edits are denied, and portals are disabled.
- PvP is disabled, natural mob populations are locally bounded, optional keep-inventory clears drops to avoid duplication.
- Explicit `astral.bypass` is a trusted administrative escape hatch, not default operator behavior.

Protection cannot arbitrate malicious plugins that uncancel events after Astral, make silent world edits, or teleport
players without honoring Bukkit events. Do not stack contradictory protection/economy plugins without staging tests.

## Verification

Pure JUnit tests cover rule authorization, currency conservation and overflow, upgrade/crew bounds, daily/contract
rotation, expedition persistence/exactly-once claims, all 200,000 unique plot addresses, every weighted ore outcome,
schematic bounds, immutable indexes, disk-failure rollback and 1,000 concurrent payment requests.
SQLite integration tests run with real native JDBC on GitHub-hosted Linux and Windows.
The Paper probe checks live build, events, chunk PDC, bank persistence and a two-boot restart.
It is not a full headless client simulation or a substitute for multi-player manual QA.
