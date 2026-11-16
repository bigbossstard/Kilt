package xyz.bluspring.twill

import net.neoforged.fml.FMLVersion

object TwillConstants {
    // Resolve the version from the FML source-set selected by Stonecutter.
    @JvmField val FML_VERSION: String = FMLVersion.getVersion()
}
