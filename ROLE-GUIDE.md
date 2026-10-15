# Halloween Hunt — added monster roles (Forge 1.21.1)

All abilities are server-authoritative, disabled for eliminated monsters, and reset each round.
The 30-second hunter head start is for hiding; role attacks and teleport contracts unlock afterward.
The coin animation can be tested by anyone, including outside a match.

## Coin test

`/give @s halloweenhunt:coin`

Hold the coin in the main hand and right-click. The right arm flicks upward, the coin separates,
spins low on the right side, and lands on the hand. A server-selected front/back result appears
after the roughly 3-second sequence. A running toss cannot be started again. The last result stays
visible while holding the coin. The original PNG is 128×128; the JSON metadata and namespaced
texture references have been corrected without changing the supplied UV coordinates or pixels.

## night_way — Frankenstein

- Scale 1.3 in the lobby/as a monster; scale 1.0 as hunter. Helmet: carved pumpkin, CustomModelData 1002.
- Three golden carrots per round. Taking damage consumes 2 food points (one hunger icon), clears
  saturation and grants Regeneration II for 5 seconds. Requires at least 2 food points.
- First death leaves a protected, labeled armor-stand corpse for 60 seconds. Another living monster
  must stay within 3 blocks and crouch continuously for 7 seconds; leaving or changing rescuer resets
  progress. Revival restores half health and grants 3 seconds of damage immunity. One attempt per round.
- A pending corpse delays an all-caught win; an expired corpse cannot revive. At the match time limit,
  a corpse alone does not count as a living monster.

## seolhee1207 — Medusa

- Stick with CustomModelData 3: right-click to bind other participating players within a spherical
  radius of 5 blocks for 2 seconds. Gray dust ring/cloud; 60-second cooldown.
- Death explicitly drops one carved pumpkin with CustomModelData 1003 even with keepInventory on.
  Only living monsters can pick it up and use it for one petrification. Other inventory is preserved.

## DamDaDam — Jack

- Coin: front = success (50%), back = failure. On success, after the animation, teleports to another
  randomly chosen living monster. Only a completed teleport starts the 120-second cooldown.
  Failure/no destination allows another attempt. Sacked monsters are excluded as destinations.
- Tagged lantern: transform for 10 seconds, wear carved pumpkin CustomModelData 1004 and take no
  damage. Right-click the lantern during transformation to launch fireballs (0.5-second shot interval).
  Transformation cooldown: 120 seconds from activation. The previous helmet returns afterward.
- Fireballs deal 4 health points to the hunter; they do not damage monsters or ignite terrain.

CustomModelData heads/skills require the corresponding external resource-pack models (not supplied).
Install the same updated mod JAR on the server and every client. Compilation/package checks are
performed; first-person framing and multiplayer gameplay still require in-game verification.

Role overrides: see ROLE-COMMANDS.md for `/hunt role <players> <role>` and `auto` restoration.
Coin release is now 0.25 seconds after clicking (0.2 seconds earlier), and both faces settle upright.

