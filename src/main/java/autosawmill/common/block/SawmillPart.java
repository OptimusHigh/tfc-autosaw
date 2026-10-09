package autosawmill.common.block;

import net.minecraft.util.StringRepresentable;

public enum SawmillPart implements StringRepresentable {
    BED("bed"),
    FRAME_TOP("frame_top");

    private final String name;

    SawmillPart(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
