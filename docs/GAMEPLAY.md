# The astral progression loop

## Islands, XP and constellations

Island level = `1 + floor(sqrt(XP / 100))`. Therefore level N begins at `100 × (N − 1)²` XP.
Stars unlock at levels **11, 21, 31, 41, 51**, capped at five.

Each star adds two crew slots (4 → 14), adds 100 bank coins to each claimed daily contract,
and adds three percentage points to expedition discovery chance. Generated blocks, mature crop harvests,
real fishing catches and eligible monster kills each grant **2 XP**, or **3 XP with Verdant attunement**.
Creative mode never grants activity rewards. Batch caps are 200 actions of each activity per actor per flush.

## Attunement

| Focus | Effect |
|---|---|
| Solar (default) | 2 bank coins per tracked generator block instead of 1 |
| Lunar | +0.40 percentage points diamond chance when diamond is unlocked; +10 percentage points discovery chance |
| Verdant | 3 XP per eligible activity instead of 2 |

Changing focus costs **500 bank coins**, may happen once every **six real-time hours**, and survives restarts.
It does not change terrain or require your crew to be online.

## Engineering

Upgrade prices are `base × 2^currentRank`, paid from the shared bank.

| Upgrade | Base | Purchasable ranks | Effect |
|---|---:|---:|---|
| Horizon | 2,000 | 1–3 | Radius 32 → 64 → 96 → 128; diameter is `2 × radius + 1` blocks |
| Generator | 1,500 | 1–4 | Unlocks more ores and improves iron/coal weights |
| Expedition | 2,500 | 1–3 | Docks 1 → 2 → 3 → 4, plus 10% base voyage coins per rank |

### Ore distribution

Weights use a **10,000-point** sampler. There are no hidden client-side rolls.

- Tier 0: coal 10%; otherwise cobblestone.
- Tier 1+: copper 8%; iron `(700 + 120 × tier)/100` percent.
- All tiers: coal `10 + tier` percent.
- Tier 2+: gold 2%, redstone 2.5%, lapis 1.8%.
- Tier 3+: diamond `0.8 + 0.08 × stars + (Lunar ? 0.4 : 0)` percent.
- Tier 4: emerald `0.45 + 0.05 × stars` percent.
- Cobblestone receives all remaining probability.

Only natural cobble/stone `BlockFormEvent`s inside ready islands are transformed. Their coordinates are tagged
in the chunk's persistent-data container. Successful breaks consume that tag once; placing or moving a block
removes it. Silk Touch and Fortune can affect Minecraft drops, but cannot turn placed blocks into reward nodes.

## Daily contracts

Three of these four are active per UTC day, determined by `(epochDay + contractOrdinal) mod 4 != 0`.
Progress and claims are shared by the crew. Progress from the previous day cannot be claimed on a new day.

| Contract | Goal | Bank coins | XP |
|---|---:|---:|---:|
| Miner | 64 tracked generator blocks | 800 | 320 |
| Gardener | 32 mature crops / berry harvests | 600 | 260 |
| Angler | 8 actual caught fishing items inside the island | 750 | 300 |
| Sentinel | 24 naturally spawned / reinforcement monsters | 700 | 280 |

Spawner, egg and custom-spawned monsters do not contribute. Members contribute; owners/stewards claim.
Daily wallet rewards are independent: `250 + 50 × streak`, streak capped at 30. Miss a UTC day and the streak resets.
Backwards clock movement cannot re-claim a day. Use an NTP-synchronized host.

## Offline star expeditions

| Voyage | Required level | Bank cost | Duration | Base coins | Base XP |
|---|---:|---:|---:|---:|---:|
| Drift | 1 | 600 | 5 min | 900 | 240 |
| Nebula | 5 | 2,000 | 20 min | 3,000 | 700 |
| Eclipse | 15 | 6,000 | 60 min | 9,000 | 1,800 |

Discovery chance: `20% + 3% × stars + (Lunar ? 10% : 0%)`.
A discovery adds **50% of base coins and XP**, plus one permanent discovery to the island record.
Dock upgrades add `10% × rank` of base coins independently. Results are rolled from a server-generated UUID
and persisted with the launch; reconnecting, rebooting or changing focus cannot reroll an existing voyage.
Returned expeditions still occupy docks until collected. Collection atomically removes the trips and credits rewards.

This is a deliberately inflationary progression economy, not a real-money or casino system.
Balances and XP are bounded; there is no floating-point money, debt, interest, or stake loss.
