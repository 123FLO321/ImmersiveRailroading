package cam72cam.immersiverailroading.render.rail;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.tile.TileRailPreview;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.render.OpenGL;
import cam72cam.mod.render.StandardModel;
import org.lwjgl.opengl.GL11;

public class RailPreviewRender {
    public static StandardModel render(TileRailPreview te) {
        RailInfo info = te.getRailRenderInfo();
        if (info == null) {
            // Still loading...
            return null;
        }
		StandardModel model = new StandardModel();
        model.addCustom(() -> {
            MinecraftClient.startProfiler("tile_rail_preview");
            try (OpenGL.With transparency = OpenGL.transparency(1, 1, 1, 0.7f); OpenGL.With matrix = OpenGL.matrix()) {
                if (te.isAboveRails()) {
                    GL11.glTranslated(0, -1, 0);
                }
                // Move to specified position
                Vec3d placementPosition = info.placementInfo.placementPosition;
                GL11.glTranslated(placementPosition.x, placementPosition.y, placementPosition.z);
                if (!te.isMulti()) {
                    RailRenderUtil.render(info, te.getWorld(), te.isAboveRails() ? te.getPos().down() : te.getPos(), true);
                }
            }
            MinecraftClient.endProfiler();
		});

        model.addItem(new ItemStack(IRItems.ITEM_GOLDEN_SPIKE, 1), new Vec3d(0.5, 0.5, 0.5), new Vec3d(1, 1, 1));

    	return model;
	}
}
