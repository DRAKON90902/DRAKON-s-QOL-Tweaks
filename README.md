# Drakon's QOL Tweaks

A purely client-side Quality of Life (QOL) mod for **Minecraft 26.2** and **Fabric Loader 0.19.2+**. Works seamlessly in Singleplayer worlds, LAN worlds, and Public Multiplayer Servers.

## Features

1. **Automatic Hotbar & Offhand Stack Refill**
   - Automatically moves a matching stack from your main inventory into your hotbar (or offhand) when your current stack runs out.
   - Configurable item filter (**All Items**, **Blocks Only**, or **Blocks & Food**), component matching, drop behavior (`Q`), and optional actionbar refill notification.

2. **Floating Lit TNT Countdown Timer**
   - Displays a floating countdown timer above lit `PrimedTnt` entities that follows the TNT as it moves.
   - Counts down in **seconds and milliseconds** (`3.850s` or `3.85s`) with dynamic color-coding from green to yellow, gold, and red as the fuse approaches zero.

3. **Animal Breeding Cooldown Timer**
   - Starts a background 5-minute (300s) cooldown timer when feeding an adult animal its breeding food, resetting to 5 minutes whenever another animal is fed (or optionally tracking per animal species).
   - Alerts you with a client-only chat message (and optional chime) when the animals are ready to be bred again.

4. **Furnace, Blast Furnace, & Smoker Smelting Timer**
   - Automatically calculates remaining smelt time and fuel burn time when you put items into a **Furnace** (10s/item), **Blast Furnace** (5s/item), or **Smoker** (5s/item).
   - Determines whether all items will finish smelting first or if fuel will run out first, displays a live countdown in the furnace GUI, tracks the timer in the background, and sends a client-only alert when it finishes.

5. **Structure Finder Command (`/dqol s`)**
   - Syntax:
     ```text
     /dqol s <structure> <minDistance> [maxDistance] [returnDeterminant]
     ```
   - Finds the nearest structure of the given type (e.g., `village_plains`, `village`, `mansion`, `stronghold`, `ancient_city`, `trial_chambers`, `bastion_remnant`, `fortress`, `monument`) within `[minDistance, maxDistance]` blocks (`minDistance` required, `maxDistance` optional).
   - `{returnDeterminant}` can be:
     - `journeymap` (or `waypoint` / `jm`): Outputs a clickable JourneyMap waypoint (`[name:"...", x:..., y:..., z:..., dim:...]`) in chat that can be clicked to instantly add it to JourneyMap.
     - `raw` (or `coords`): Outputs raw coordinates (`X: ..., Y: ..., Z: ...`) with click-to-copy support.

6. **In-Game Options Configuration Menu & Client Commands**
   - Accessible directly from the Minecraft **Options...** screen via the **Drakon's QOL Tweaks...** button, or via `/drakonsqol` (`/dqol`).
   - Organized into tabs for **Hotbar Refill**, **Timers**, **Mod Messages**, and **More / Status** (with `QolFeatureRegistry` support for future features).
