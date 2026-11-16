package xyz.bluspring.twill.compat;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/** No-op replacement for FML's AccessTransformerService; Kilt applies ATs itself. */
public enum InactiveAccessTransformerProcessor implements ClassProcessor {
    INSTANCE;

    private static final ProcessorName NAME = new ProcessorName("kilt", "inactive_access_transformer");

    @Override
    public ProcessorName name() {
        return NAME;
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        return false;
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        return ComputeFlags.NO_REWRITE;
    }
}
