# Command reference

Aliases: `/is`, `/island`, `/skyblock`, `/astral`. Names and enum arguments are case-insensitive.
Target players must have joined the server at least once; online-name lookups do not block on Mojang.
Coin amounts are positive base-10 whole numbers, without commas, suffixes, or decimals.

| Command | Authorization / behavior |
|---|---|
| `/is [menu]` | Everyone; dashboard or origin selector |
| `/is help` | Everyone, including console |
| `/is create [grove\|crystal\|ember]` | No current island; defaults to grove |
| `/is home` | Member; safe async travel to shared home |
| `/is visit <player>` | Public island, member, or explicit bypass |
| `/is top` / `/is browse` | Paginated ranking atlas; private islands are visible but cannot be visited |
| `/is info` / `/is balance` | Wallet, streak and current island details |
| `/is hud` | Toggle your persistent HUD preference |
| `/is invite <player>` | Owner/steward; ten-minute invitation, capacity checked on accept |
| `/is accept` | No current island; valid invitation to ready island |
| `/is leave` | Builder/steward; owner must first transfer ownership |
| `/is kick <member>` | Owner can remove non-owners; steward can remove builders |
| `/is role <member> <builder\|steward>` | Owner only |
| `/is transfer <member>` | Owner only; old owner becomes steward |
| `/is bank` | Open treasury menu |
| `/is bank deposit <amount>` | Any member; wallet → bank |
| `/is bank withdraw <amount>` | Owner/steward; bank → wallet |
| `/is pay <player> <amount>` | Anyone with sufficient wallet coins; atomic transfer |
| `/is daily` | One reward per UTC day; wallet reward |
| `/is upgrades` | Engineering menu |
| `/is upgrade <horizon\|generator\|expedition>` | Owner/steward; charges bank |
| `/is contracts` / `/is missions` | Shared daily objectives |
| `/is claim <miner\|gardener\|angler\|sentinel>` | Owner/steward; active, complete, unclaimed objective |
| `/is expedition` / `/is expeditions` | Star docks menu |
| `/is expedition launch <drift\|nebula\|eclipse>` | Owner/steward; capacity, level and bank requirements |
| `/is expedition claim` | Owner/steward; collects all returned expeditions once |
| `/is settings` / `/is members` | Crew and attunement menu |
| `/is visitors` | Owner/steward; toggles public visits |
| `/is rename <name>` | Owner/steward; 3–32 Unicode letters/numbers, spaces, apostrophes, `_`, `-` |
| `/is sethome` | Owner/steward; safe solid floor within current island |
| `/is attune <solar\|lunar\|verdant>` | Owner/steward; 500 bank coins, six-hour cooldown |
| `/is admin diagnostics` / `/is diagnostics` | `astral.admin`; version/world/aggregate counts |
| `/is admin audit` | `astral.admin`; latest ten audit records |
| `/is admin grant <player> <amount>` | `astral.admin`; audited wallet issuance; console supported |

All mutating player commands have one outstanding transaction per player. A stale menu cannot bypass the domain's
current authorization, money, dock, or capacity checks. A success confirmation is sent only after database commit.

`/is ci-smoke` is an integration harness command, not gameplay: console only and disabled unless the JVM has
`-Dastral.ci=true`. It creates one reserved test island; **do not enable that flag in production**.
