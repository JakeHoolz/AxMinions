package com.artillexstudios.axminions.nms.v1_21_R7

import com.artillexstudios.axminions.api.events.PreMinionDamageEntityEvent
import com.artillexstudios.axminions.api.minions.Minion
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.fox.Fox
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

object DamageHandler {
    private var DUMMY_ENTITY = Fox(EntityType.FOX, (Bukkit.getWorlds()[0] as CraftWorld).handle)
    private var minion: Minion? = null

    fun getUUID(): UUID = DUMMY_ENTITY.uuid
    fun getMinion(): Minion? = minion

    fun damage(source: Minion, entity: Entity) {
        val nmsEntity = (entity as CraftEntity).handle

        synchronized(DUMMY_ENTITY) {
            this.minion = source
            var baseDamage = 1

            val toolBukkit = source.getTool()
            val nmsItem: ItemStack =
                if (toolBukkit == null) ItemStack.EMPTY
                else CraftItemStack.asNMSCopy(toolBukkit)

            // Add attack damage modifiers from the held item (if any)
            nmsItem.get(DataComponents.ATTRIBUTE_MODIFIERS)
                ?.forEach(EquipmentSlotGroup.MAINHAND) { h: Holder<Attribute>, m ->
                    if (h.unwrapKey().orElseThrow() == Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow()) {
                        baseDamage += m.amount().toInt()
                    }
                }

            DUMMY_ENTITY.setItemSlot(EquipmentSlot.MAINHAND, nmsItem)

            if (!nmsEntity.isAttackable || entity is Player) {
                this.minion = null
                return
            }

            val f2 = 1.0f
            val damageSource = nmsEntity.damageSources().noAggroMobAttack(DUMMY_ENTITY)

            var enchantBonus = EnchantmentHelper.modifyDamage(
                nmsEntity.level() as ServerLevel,
                nmsItem,
                nmsEntity,
                damageSource,
                baseDamage.toFloat()
            )

            baseDamage = (baseDamage * (0.2f + f2 * f2 * 0.8f)).toInt()
            enchantBonus *= f2

            if (baseDamage > 0.0f || enchantBonus > 0.0f) {
                val knockbackLevel = (toolBukkit?.getEnchantmentLevel(Enchantment.KNOCKBACK) ?: 0)
                val i = knockbackLevel

                baseDamage = (baseDamage * 1.5f).toInt()
                baseDamage = (baseDamage + enchantBonus).toInt()

                // "weapon" component check (kept as you had it)
                val isWeapon = nmsItem.item.components().get(DataComponents.WEAPON) != null

                var preHealth = 0.0f
                var litTargetTemporarily = false
                val fireAspect = (toolBukkit?.getEnchantmentLevel(Enchantment.FIRE_ASPECT) ?: 0)

                if (nmsEntity is LivingEntity) {
                    preHealth = nmsEntity.health
                    if (fireAspect > 0 && !nmsEntity.isOnFire) {
                        litTargetTemporarily = true
                        nmsEntity.igniteForSeconds(1f, false)
                    }
                }

                val event = PreMinionDamageEntityEvent(source, entity as org.bukkit.entity.LivingEntity, baseDamage.toDouble())
                Bukkit.getPluginManager().callEvent(event)
                if (event.isCancelled) {
                    this.minion = null
                    return
                }

                val serverLevel = (source.getLocation().world as CraftWorld).handle as ServerLevel
                val hit = nmsEntity.hurtServer(serverLevel, damageSource, baseDamage.toFloat())

                if (hit) {
                    // Compute yaw trig as Double (stable across 1.21.11 changes)
                    val yawRad = Math.toRadians(source.getLocation().yaw.toDouble())
                    val sinYaw = sin(yawRad)
                    val cosYaw = cos(yawRad)

                    if (i > 0) {
                        val strength = (i.toDouble() * 0.5)

                        if (nmsEntity is LivingEntity) {
                            nmsEntity.knockback(
                                strength,
                                sinYaw,
                                -cosYaw
                            )
                        } else {
                            nmsEntity.push(
                                (-sinYaw * strength),
                                0.1,
                                (cosYaw * strength)
                            )
                        }
                    }

                    if (isWeapon) {
                        val sweep = toolBukkit?.getEnchantmentLevel(Enchantment.SWEEPING_EDGE) ?: 0
                        val sweepRatio = if (sweep > 0) getSweepingDamageRatio(sweep) else 0.0f
                        val sweepDamage = 1.0f + sweepRatio * baseDamage

                        val list: List<LivingEntity> = serverLevel
                            .getEntitiesOfClass(LivingEntity::class.java, nmsEntity.boundingBox.inflate(1.0, 0.25, 1.0))
                            .filter { it !is Player }

                        for (entityliving in list) {
                            if ((entityliving !is ArmorStand || !entityliving.isMarker) &&
                                source.getLocation().distanceSquared((entity as Entity).location) < 9.0
                            ) {
                                val damageEvent = PreMinionDamageEntityEvent(
                                    source,
                                    entityliving.bukkitEntity as org.bukkit.entity.LivingEntity,
                                    sweepDamage.toDouble()
                                )
                                Bukkit.getPluginManager().callEvent(damageEvent)
                                if (damageEvent.isCancelled) { // <-- FIXED (was checking `event`)
                                    this.minion = null
                                    return
                                }

                                // Only apply knockback if the sweep damage hits
                                if (entityliving.hurtServer(
                                        serverLevel,
                                        nmsEntity.damageSources().noAggroMobAttack(DUMMY_ENTITY),
                                        sweepDamage
                                    )
                                ) {
                                    entityliving.knockback(
                                        0.4,
                                        sinYaw,
                                        -cosYaw
                                    )
                                }
                            }
                        }

                        // Sweep particle
                        val d0 = -sinYaw
                        val d1 = cosYaw
                        serverLevel.sendParticles(
                            ParticleTypes.SWEEP_ATTACK,
                            source.getLocation().x + d0,
                            source.getLocation().y + 0.5,
                            source.getLocation().z + d1,
                            0,
                            d0,
                            0.0,
                            d1,
                            0.0
                        )
                    }

                    if (nmsEntity is LivingEntity) {
                        val delta: Float = preHealth - nmsEntity.health

                        if (fireAspect > 0) {
                            nmsEntity.igniteForSeconds(fireAspect * 4f, false)
                        }

                        if (delta > 2.0f) {
                            val k = (delta.toDouble() * 0.5).toInt()
                            serverLevel.sendParticles(
                                ParticleTypes.DAMAGE_INDICATOR,
                                nmsEntity.x,
                                nmsEntity.getY(0.5),
                                nmsEntity.z,
                                k,
                                0.1,
                                0.0,
                                0.1,
                                0.2
                            )
                        }
                    }
                } else {
                    if (litTargetTemporarily) nmsEntity.clearFire()
                }
            }

            this.minion = null
        }
    }

    fun getSweepingDamageRatio(level: Int): Float {
        return 1.0f - 1.0f / (level + 1).toFloat()
    }
}
