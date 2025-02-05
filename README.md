# Cowardless
A Minecraft plugin that replaces players who log out during combat with an NPC to be killed instead.

- [<img width="16px" src="https://modrinth.com/favicon.ico"/> Modrinth](https://modrinth.com/plugin/cowardless)
- [<img width="16px" src="https://www.spigotmc.org/favicon.ico"/> SpigotMC](https://www.spigotmc.org/resources/cowardless.115111/)
- [<img width="16px" src="https://hangar.papermc.io/_nuxt/hangar-logo.DNKyJEtq.svg"/> Hangar](https://hangar.papermc.io/BlurOne/Cowardless)

## The problem
Have you ever engaged in an intense PvP battle, only to have your opponent disconnect abruptly? It’s frustrating, right? You’ve meticulously set up a trap, but they slip away because you missed their exact disconnection point. Cowards ruin the thrill of combat.

## The solution
Introducing Cowardless, the ultimate plugin for Minecraft warriors. Say goodbye to cowardly opponents who vanish mid-fight. Here’s how it works:
1. **Clone Activation:** When you’re in combat or constantly taking damage (think drowning or lava), Cowardless springs into action. If you attempt to disconnect, a clone takes your place on the server—a mere illusion for other players.
2. **Transfer of Consequences:** But here’s the twist: Everything the clone experiences—position, health, inventory, even death—gets transferred back to you when you log back in.
3. **Automatic Despawn:** Fear not! If spared and left unattended, the clone will eventually despawn, allowing you to enjoy the peacefulness of having disconnected.

## The extra info
In the `config.yml`\([Paper](https://github.com/BlurOne-GIT/Cowardless/blob/paper/src/main/resources/config.yml)/[Spigot](https://github.com/BlurOne-GIT/Cowardless/blob/spigot/src/main/resources/config.yml)), you can adjust how long a player is considered to be in combat, and how long a clone lasts before despawning. You can also turn on a logger that will print to the console when an NPC is spawned or despawned, and a feature that will add the red vignette effect to a player when they are in combat, so that they know not to disconnect. You can also exempt some disconnection reasons from spawning NPCs (**this feature is Paper only**).

If you have any ideas or feature requests for the plugin, feel free to share them in the [repo's discussions](https://github.com/BlurOne-GIT/Cowardless/discussions).
