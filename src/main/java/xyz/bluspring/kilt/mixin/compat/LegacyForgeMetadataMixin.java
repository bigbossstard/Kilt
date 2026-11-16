package xyz.bluspring.kilt.mixin.compat;

import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.bluspring.kilt.loader.compat.LegacyForgeMetadataAdapter;

/**
 * Lets the current FML discovery reader accept legacy Forge metadata filenames.
 *
 * The adapter creates a cached copy with the old metadata exposed under the
 * modern NeoForge filename, then delegates back to the normal FML reader.
 */
@Mixin(value = JarModsDotTomlModFileReader.class, remap = false)
public abstract class LegacyForgeMetadataMixin {
    @Inject(
        method = "createModFile",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void kilt$adaptLegacyForgeMetadata(
        JarContents contents,
        ModFileDiscoveryAttributes attributes,
        CallbackInfoReturnable<IModFile> cir
    ) {
        IModFile adapted = LegacyForgeMetadataAdapter.createModFile(contents, attributes);
        if (adapted != null) {
            cir.setReturnValue(adapted);
        }
    }
}
