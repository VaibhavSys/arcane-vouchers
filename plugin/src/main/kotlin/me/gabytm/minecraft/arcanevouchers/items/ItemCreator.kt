package me.gabytm.minecraft.arcanevouchers.items

import com.google.common.base.Enums
import de.tr7zw.nbtapi.NBTContainer
import de.tr7zw.nbtapi.NBTItem
import dev.triumphteam.gui.builder.item.BaseItemBuilder
import dev.triumphteam.gui.builder.item.ItemBuilder
import me.gabytm.minecraft.arcanevouchers.ArcaneVouchers
import me.gabytm.minecraft.arcanevouchers.Constant
import me.gabytm.minecraft.arcanevouchers.ServerVersion
import me.gabytm.minecraft.arcanevouchers.functions.error
import me.gabytm.minecraft.arcanevouchers.functions.isPlayerHead
import me.gabytm.minecraft.arcanevouchers.functions.mini
import me.gabytm.minecraft.arcanevouchers.functions.warning
import me.gabytm.minecraft.arcanevouchers.items.skulls.SkullTextureProvider
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

class ItemCreator(plugin: ArcaneVouchers) {

    private val nbtHandler = NBTHandler(plugin)

    /**
     * Turn a list with 2 elements into a Pair<[Enchantment], [Int]>
     * @return pair or null if the enchantment or level are null
     */
    @Suppress("DEPRECATION")
    private fun List<String>.toEnchantmentPair(): Pair<Enchantment, Int>? {
        val enchantment = if (ServerVersion.HAS_KEYS) {
            val parts = first().split(Constant.Separator.COLON, 2)

            // The server has NamespacedKeys but this isn't one
            if (parts.size != 2) {
                Enchantment.getByName(parts[0])
            }

            Enchantment.getByKey(NamespacedKey(parts[0], parts[1]))
        } else {
            Enchantment.getByName(first())
        } ?: kotlin.run {
            warning("Unknown enchantment ${first()}")
            return null
        }
        val level = get(1).toIntOrNull() ?: return null
        return enchantment to level
    }

    /**
     * Attempt to parse a [Color] from a string with format `red,green,blue`
     * @return [Color] or null
     */
    private fun String.toColor(): Color? {
        val parts = split(Constant.Separator.COMMA, 3)

        if (parts.size != 3) {
            return null
        }

        val red = parts[0].toIntOrNull(16) ?: return null
        val green = parts[1].toIntOrNull(16) ?: return null
        val blue = parts[2].toIntOrNull(16) ?: return null

        return try {
            Color.fromRGB(red, green, blue)
        } catch (e: IllegalArgumentException) {
            error("Could not parse color from '$this'", e)
            null
        }
    }

    /**
     * Set the general meta to the item and build it
     * @param isVoucher whether the item is a voucher
     * @param config the section from where values are read
     * @return an [ItemStack] created according to the specified values
     */
    private fun BaseItemBuilder<*>.setGeneralMeta(isVoucher: Boolean, config: ConfigurationSection): ItemStack {
        val flags = config.getStringList("flags")
            .mapNotNull { Enums.getIfPresent(ItemFlag::class.java, it.uppercase()).orNull() }
            .toTypedArray()

        // Enchantments are saved in a list as 'Enchantment;level'
        val enchants = config.getStringList("enchantments")
            .map { it.split(Constant.Separator.SEMICOLON, 2) }
            .mapNotNull { it.toEnchantmentPair() }.toMap()

        val customModelData = config.getInt("customModelData")

        val item = this
            .name(config.getString("name")?.mini() ?: Component.empty())
            .lore(config.getStringList("lore").map { it.mini() })
            .flags(*flags)
            .unbreakable(config.getBoolean("unbreakable"))
            .apply {
                enchants.forEach { (enchantment, level) -> enchant(enchantment, level, true) }

                if (customModelData > 0) {
                    model(customModelData)
                }

                if (enchants.isEmpty()) {
                    glow(config.getBoolean("glow"))
                }

                config.getString("color")?.toColor()?.let { color(it) }
            }.build()

        if (isVoucher) {
            // Get the JSON of this voucher
            val nbt = nbtHandler.getNbt(config.parent?.name ?: "") ?: return item
            return NBTItem(item).apply { mergeCompound(NBTContainer(nbt)) }.item
        }

        return item
    }

    /**
     * Load the NBT string from file
     * @see NBTHandler.load
     */
    fun loadNbt() {
        this.nbtHandler.load()
    }

    /**
     * Create an item from a [ConfigurationSection]
     * @param isVoucher whether the item is a voucher
     * @param config the section from where values are read
     * @param defaultMaterial the default material used for when the config section is null or the specified material is
     *                        invalid
     * @return an [ItemStack] created according to the specified values
     */
    // TODO: 14-Aug-21 add support for banners 
    fun create(isVoucher: Boolean, config: ConfigurationSection?, defaultMaterial: Material? = null): ItemStack {
        // Return a default item if the config section is null
        if (config == null) {
            // If even the default material is null return an *invalid config section* item
            if (defaultMaterial == null) {
                return ItemBuilder.from(Material.PAPER)
                    .name(Component.text("Invalid config section", NamedTextColor.RED))
                    .build()
            }

            // Return an item with the default material
            return ItemStack(defaultMaterial)
        }

        val materialString = config.getString("material") ?: ""
        val material = if (materialString.isEmpty()) {
            defaultMaterial
        } else {
            Material.matchMaterial(materialString, false)
        }

        // If the material specified is null return a fallback item
        if (material == null) {
            return ItemBuilder.from(Material.PAPER)
                .name(Component.text("Unknown material $materialString", NamedTextColor.RED))
                .build()
        }

        val damage = config.getInt("damage").toShort()

        // Decide what ItemBuilder implementation should be used
        val builder = if (material.isPlayerHead(damage)) {
            SkullTextureProvider.applyTexture(config.getString("texture") ?: "")
        } else {
            ItemBuilder.from(ItemStack(material, 1, damage))
        }

        // Set the general meta to the item and build it
        return builder.setGeneralMeta(isVoucher, config)
    }

}