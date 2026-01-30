package com.artillexstudios.axminions.nms

import com.artillexstudios.axapi.utils.Version
import com.artillexstudios.axminions.api.minions.Minion
import java.util.UUID
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack

interface NMSHandler {
    companion object {
        private val handler: NMSHandler = run {
            val serverVersion = Version.getServerVersion()
            check(serverVersion == Version.v1_21_11) {
                "AxMinions supports only Minecraft 1.21.11. Detected: ${serverVersion.getVersions()}"
            }
            Class.forName("com.artillexstudios.axminions.nms.${serverVersion.getNMSVersion()}.NMSHandler")
                .getConstructor()
                .newInstance() as NMSHandler
        }

        fun get(): NMSHandler {
            return handler
        }
    }

    fun attack(source: Minion, target: Entity)

    fun generateRandomFishingLoot(minion: Minion, waterLocation: Location): List<ItemStack>

    fun isAnimal(entity: Entity): Boolean

    fun getAnimalUUID(): UUID

    fun getMinion(): Minion?

    fun getExp(block: Block, itemStack: ItemStack): Int
}
