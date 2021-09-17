package me.gabytm.minecraft.arcanevouchers.voucher

import de.tr7zw.nbtapi.NBTItem
import me.gabytm.minecraft.arcanevouchers.ArcaneVouchers
import me.gabytm.minecraft.arcanevouchers.Constant.Nbt
import me.gabytm.minecraft.arcanevouchers.functions.toArgsMap
import org.apache.commons.lang.StringUtils
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class VoucherManager(private val plugin: ArcaneVouchers) {

    private val loadedVouchers = mutableMapOf<String, Voucher>()

    fun loadVouchers() {
        loadedVouchers.clear()

        val vouchersSection = plugin.vouchersConfig.getSection("vouchers") ?: kotlin.run {
            plugin.logger.warning("Could not find the 'vouchers' section")
            return
        }

        for (it in vouchersSection.getValues(false).keys) {
            val section = vouchersSection.getConfigurationSection(it) ?: continue
            this.loadedVouchers[it] = Voucher.from(section, plugin.actionManager, plugin.itemCreator)
        }

        plugin.logger.info("Loaded ${this.loadedVouchers.size} voucher(s): ${this.loadedVouchers.keys.joinToString(", ")}")
    }

    fun giveVoucher(player: Player, id: String, amount: Int = 1, args: Array<String>) {
        val voucher = this.loadedVouchers[id] ?: return

        val argsMap = args.toArgsMap()
        val nbt = NBTItem(voucher.item.clone())

        // Set the arguments and player's name inside the item
        val compound = nbt.getCompound(Nbt.VOUCHER_COMPOUND)
        val argumentsCompound = compound.getCompound(Nbt.ARGUMENTS_COMPOUND)

        argsMap.entries.forEach { (key, value) -> argumentsCompound.setString(key, value) }
        compound.setString(Nbt.RECEIVER_NAME, player.name)
        // -----

        val item = nbt.item
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(item.type) ?: return

        // Replace the arguments on item name and lore
        val keys = argsMap.keys.toTypedArray()
        val values = argsMap.values.toTypedArray()

        if (meta.hasDisplayName()) {
            meta.setDisplayName(StringUtils.replaceEach(meta.displayName, keys, values))
        }

        if (meta.hasLore()) {
            meta.lore = meta.lore?.map { StringUtils.replaceEach(it, keys, values) }
        }

        item.itemMeta = meta
        item.amount = amount
        // -----

        // Add the voucher to player's inventory and drop the leftovers on the ground
        player.inventory.addItem(item).values.forEach {
            player.world.dropItemNaturally(player.location, it)
        }
        // Send the message to the player and also add the {amount} placeholder
        voucher.settings.messages.receiveMessage.send(
            plugin.audiences.player(player),
            argsMap.also { it["{amount}"] = amount.toString() }
        )
    }

}