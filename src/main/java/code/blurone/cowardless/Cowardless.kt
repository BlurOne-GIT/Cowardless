package code.blurone.cowardless

import net.kyori.adventure.key.Key
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.translation.Argument
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore
import net.kyori.adventure.translation.GlobalTranslator
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.model.PlayerModelPart
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.profile.PlayerProfile
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.*
import kotlin.math.max

@Suppress("unused")
class Cowardless : JavaPlugin(), Listener {
    companion object {
        var adventure: BukkitAudiences? = null
    }

    private val cowardsByName: MutableMap<String, Mannequin> = mutableMapOf()
    private val retargetableMobs: MutableMap<Mob, String> = mutableMapOf()
    private val hurtByTickstamps: MutableMap<String, Long> = mutableMapOf()
    private val combatTicksThreshold = config.getLong("combat_seconds_threshold", 30) * 20L
    private val despawnTicksThreshold = config.getInt("despawn_seconds_threshold", 30) * 20
    private val resetDespawnThreshold = config.getBoolean("reset_despawn_threshold", true)
    private val redWarning = config.getBoolean("red_warning", false)
    private val pvpOnly = config.getBoolean("pvp_only", false)
    private val twoSided = config.getBoolean("two_sided_pvp", true)
    private val actionBar = config.getBoolean("action_bar_message", true)
    private val chatMessages = config.getBoolean("chat_message", true)
    private val actionBarRunnables: MutableMap<String, BukkitRunnable> = mutableMapOf()
    private val redUnwarnScheduledTasks: MutableMap<String, BukkitTask> = mutableMapOf()
    private val redUnwarnRunnables: MutableMap<String, BukkitRunnable> = mutableMapOf()
    private val commandBlacklist: MutableSet<String> = mutableSetOf()

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()

        adventure = BukkitAudiences.create(this)

        // Register plugin events
        server.pluginManager.registerEvents(this, this)

        object : BukkitRunnable() {
            override fun run() {
                for (coward in cowardsByName.values) {
                    if (coward.isValid && coward.ticksLived > despawnTicksThreshold) {
                        coward.remove()
                        logger.info("${coward.name}'s NPCoward has expired.")
                    }
                }
            }
        }.runTaskTimer(this, 1, 20)

        commandBlacklist.addAll(config.getStringList("command_blacklist"))

        if (actionBar || chatMessages)
            server.scheduler.runTaskAsynchronously(this) { _ -> setupTranslations() }
    }

    override fun onDisable() {
        cowardsByName.values.forEach(Mannequin::remove)
        cowardsByName.clear()
        adventure!!.close()
        adventure = null
    }

    fun warnMissingTranslation(key: String, locale: String): String {
        logger.warning("No $key translation found for $locale")
        return key
    }

    fun setupTranslations() {
        val file = File(dataFolder, "messages.yml")
        if (!file.exists()) {
            saveResource("messages.yml", false)
        }
        val messages = YamlConfiguration.loadConfiguration(file)
        val store = MiniMessageTranslationStore.create(Key.key( "cowardless:messages"))

        val defaultLang = messages.getString("default", "en")!!
        messages.getConfigurationSection(defaultLang)?.let { defaultSection ->
            store.registerAll(Locale.ROOT, defaultSection.getKeys(false)) { key ->
                defaultSection.getString(key) ?: warnMissingTranslation(key, defaultLang)
            }
        }

        val entries = messages.getKeys(false)
        for (entry in entries) {
            if (entry == "default") continue

            val localeSection = messages.getConfigurationSection(entry) ?: continue
            val locales = Locale.getAvailableLocales().filter { locale ->
                val tag = locale.toLanguageTag()
                tag == entry || tag.startsWith(entry) && tag !in entries
            }
            for (locale in locales) {
                store.registerAll(locale, localeSection.getKeys(false)) { key ->
                    localeSection.getString(key) ?: warnMissingTranslation(key, locale.toLanguageTag())
                }
            }
        }

        GlobalTranslator.translator().addSource(store)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onNpcDamagedByPlayer(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return

        if (event.damager is Player && event.damage > 0) {
            if (pvpOnly && player.name !in hurtByTickstamps) damageHandler(player, event.cause)

            if (twoSided) damageHandler(event.damager as Player, event.cause)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        if (event.damage <= 0) return

        // Reset timer for NPC
        if (event.entity.type == EntityType.MANNEQUIN)
            cowardsByName[event.entity.name]?.let {
                if (resetDespawnThreshold && it.health != 0.0)
                    it.ticksLived = 1
                return
            }

        val player = event.entity as? Player ?: return
        if (!pvpOnly || player.name in hurtByTickstamps)
            damageHandler(player, event.cause)
    }

    fun damageHandler(player: Player, cause: DamageCause) {
        val inTicks = when (cause) {
            // Constant damage
            DamageCause.CONTACT,
            DamageCause.SUFFOCATION,
            DamageCause.FIRE,
            DamageCause.FIRE_TICK,
            DamageCause.LAVA,
            DamageCause.DROWNING,
            DamageCause.VOID,
            DamageCause.HOT_FLOOR,
            DamageCause.CAMPFIRE,
            DamageCause.CRAMMING,
            DamageCause.FREEZE
                -> if ((hurtByTickstamps[player.name] ?: 0L) > player.world.gameTime + 50L) combatTicksThreshold else 40L

            // Pvp damage
            DamageCause.ENTITY_ATTACK,
            DamageCause.ENTITY_SWEEP_ATTACK,
            DamageCause.PROJECTILE,
            DamageCause.BLOCK_EXPLOSION,
            DamageCause.ENTITY_EXPLOSION,
            DamageCause.POISON,
            DamageCause.MAGIC,
            DamageCause.WITHER,
            DamageCause.THORNS,
            DamageCause.SONIC_BOOM
                -> combatTicksThreshold

            else -> return
        }

        setCombatTicks(player, inTicks)
    }

    fun setCombatTicks(player: Player, ticks: Long) {
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

        // Set timestamp for cowards
        hurtByTickstamps[player.name] = player.world.gameTime + ticks

        if (redWarning) addRedWarning(player, ticks)
        if (actionBar) {
            actionBarRunnables.remove(player.name)?.cancel()
            val runnable = ActionBarRunnable(player, ticks / 20L)
            actionBarRunnables[player.name] = runnable
            runnable.runTaskTimer(this, 20L, 20L)

            // We set initial delays to 20 ticks and run first time now to avoid 1 tick dephasing with red warning tasks
            runnable.run()
        }
    }

    fun addRedWarning(player: Player, ticks: Long) {
        redUnwarnScheduledTasks.remove(player.name)?.cancel()

        redUnwarnRunnables.remove(player.name)?.run()
        val oldWorldBorder = player.worldBorder ?: run {
            player.worldBorder = Bukkit.createWorldBorder()
            player.worldBorder!!
        }
        val oldWarningDistance = oldWorldBorder.warningDistance
        oldWorldBorder.warningDistance = Int.MAX_VALUE
        val runnable = object : BukkitRunnable() {
            override fun run() {
                if (oldWorldBorder == player.worldBorder)
                    oldWorldBorder.warningDistance = oldWarningDistance
            }
        }
        redUnwarnRunnables[player.name] = runnable

        redUnwarnScheduledTasks[player.name] = runnable.runTaskLater(this, ticks)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDead(event: PlayerDeathEvent) {
        // Get rid of the timestamp
        hurtByTickstamps.remove(event.entity.name)
        redUnwarnScheduledTasks.remove(event.entity.name)?.cancel()
        redUnwarnRunnables.remove(event.entity.name)?.run()
        actionBarRunnables.remove(event.entity.name)?.cancel()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onNpcDead(event: EntityDeathEvent) {
        if (event.entityType != EntityType.MANNEQUIN) return

        // Remove the NPC if present
        cowardsByName.remove(event.entity.name)?.let {
            // Removal is already prevented by not being in the map
            logger.info("${it.name}'s NPCoward has died.")
            hurtByTickstamps[event.entity.name] = -1
            event.droppedExp = max(it.getMetadata("cowardless_lvl")[0].asInt() * 7, 100)
            event.drops.clear()
            event.drops.addAll(it.getMetadata("cowardless_inv")[0].value() as List<ItemStack>)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onLeave(event: PlayerQuitEvent) {
        if ((hurtByTickstamps.remove(event.player.name) ?: return) <= event.player.world.gameTime) return

        val player = event.player
        val profile = player.playerProfile.clone()

        if (chatMessages)
            adventure!!.players().sendMessage(Component.translatable("chat_coward",
                Argument.component("p", Component.text(event.player.name))
            ).colorIfAbsent(NamedTextColor.YELLOW))

        server.scheduler.runTaskTimer(this, { task ->
            if (player.isOnline) return@runTaskTimer
            if (!chatMessages)
                logger.info("${player.name} is a COWARD!")
            // Create and spawn NPC
            task.cancel()
            createNpc(player, profile)
        }, 1L, 1L)


        player.world.getEntitiesByClass(Mob::class.java).forEach {
            if (it.target == player)
                retargetableMobs[it] = player.name
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onTargetChange(event: EntityTargetLivingEntityEvent) {
        if (event.target != null) return
        val name = retargetableMobs.remove(event.entity) ?: return
        val mannequin = cowardsByName[name]!!
        event.target = mannequin
        event.isCancelled = false
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        cowardsByName[event.name]?.let {
            if (!it.isValid) return
            hurtByTickstamps[event.name] = combatTicksThreshold
            server.scheduler.callSyncMethod(this, it::remove) //it.remove()
            logger.info("${event.name}'s NPCoward has been replaced by the real player.")
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        hurtByTickstamps[event.player.name]?.let { hurtByTickstamp ->
            if (hurtByTickstamp >= 0) {
                setCombatTicks(event.player, hurtByTickstamp)
            } else {
                event.player.inventory.clear()
                event.player.totalExperience = 0
                event.player.health = 0.0
                return
            }
        }
        cowardsByName.remove(event.player.name)?.let {
            // Ok now back
            event.player.teleport(it.location)
            copyCommon(it, event.player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val remaining = (hurtByTickstamps[event.player.name] ?: return) - event.from.gameTime
        if (remaining <= 0) {
            hurtByTickstamps.remove(event.player.name)
            return
        }

        hurtByTickstamps[event.player.name] = remaining + event.player.world.gameTime

        if (redWarning)
            addRedWarning(event.player, remaining)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGameMode(event: PlayerGameModeChangeEvent) {
        if (event.newGameMode != GameMode.CREATIVE && event.newGameMode != GameMode.SURVIVAL) return

        hurtByTickstamps.remove(event.player.name)
        redUnwarnScheduledTasks.remove(event.player.name)?.cancel()
        redUnwarnRunnables.remove(event.player.name)?.run()
        actionBarRunnables.remove(event.player.name)?.cancel()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerCommandPreprocessEvent(event: PlayerCommandPreprocessEvent) {
        if ((hurtByTickstamps[event.player.name] ?: return) <= event.player.world.gameTime) return

        val commandName = event.message.split(' ').first().removePrefix("/")
        if (commandName !in commandBlacklist) return
        event.isCancelled = true

        if (chatMessages)
            adventure!!.player(event.player).sendMessage(Component.translatable("command_blocked").colorIfAbsent(NamedTextColor.RED))
    }

    val equipmentSlots = setOf(
        EquipmentSlot.HAND,
        EquipmentSlot.OFF_HAND,
        EquipmentSlot.FEET,
        EquipmentSlot.LEGS,
        EquipmentSlot.CHEST,
        EquipmentSlot.HEAD,
    )

    private fun createNpc(player: Player, playerProfile: PlayerProfile) {
        val mannequin = player.world.spawnEntity(player.location, EntityType.MANNEQUIN) as Mannequin
        cowardsByName[player.name] = mannequin

        // Things we need
        mannequin.isPersistent = false
        mannequin.noDamageTicks = 0
        mannequin.setMetadata("cowardless_inv", FixedMetadataValue(this, player.inventory.toList()))
        mannequin.setMetadata("cowardless_lvl", FixedMetadataValue(this, player.level))

        // Ok now copy all from the player
        copyCommon(player, mannequin)

        // Mannequin
        mannequin.mainHand = player.mainHand
        for (part in PlayerModelPart.entries)
            mannequin.setModelPartShown(part, player.isModelPartShown(part))
        mannequin.playerProfile = playerProfile

        // Attributable
        for (attribute in Attribute.values()) {
            val playerAttribute = player.getAttribute(attribute) ?: continue
            val mannequinAttribute = mannequin.getAttribute(attribute) ?: continue
            mannequinAttribute.baseValue = playerAttribute.baseValue
            playerAttribute.modifiers.forEach(mannequinAttribute::addModifier)
        }

        // Entity
        player.scoreboardTags.forEach(mannequin::addScoreboardTag)
        //mannequin.isInvulnerable = player.isInvulnerable NO
        mannequin.isSilent = player.isSilent
        mannequin.isVisualFire = player.isVisualFire
        mannequin.setRotation(player.location.yaw, player.location.pitch)

        // LivingEntity
        mannequin.isCollidable = player.isCollidable
        val mannequinEquipment = mannequin.equipment!!
        val playerEquipment = player.equipment!!
        for (slot in equipmentSlots)
            mannequinEquipment.setItem(slot, playerEquipment.getItem(slot), true)

        // Nameable
        mannequin.customName = player.name
    }

    private fun copyCommon(from: LivingEntity, to: LivingEntity) {
        // Damageable
        to.absorptionAmount = from.absorptionAmount
        to.health = from.health

        // Entity
        from.passengers.forEach(to::addPassenger)
        to.fallDistance = from.fallDistance
        to.fireTicks = from.fireTicks
        to.freezeTicks = from.freezeTicks
        to.isGlowing = from.isGlowing
        to.setGravity(from.hasGravity())
        to.portalCooldown = from.portalCooldown
        to.velocity = from.velocity

        // LivingEntity
        to.addPotionEffects(from.activePotionEffects)
        to.arrowsInBody = from.arrowsInBody
        to.arrowCooldown = from.arrowCooldown
        to.maximumAir = from.maximumAir
        to.remainingAir = from.remainingAir
        to.waypointColor = from.waypointColor
        to.setWaypointStyle(from.waypointStyle)
    }
}