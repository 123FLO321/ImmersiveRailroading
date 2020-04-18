package cam72cam.immersiverailroading.render.block;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.library.Augment;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.render.rail.RailBuilderRender;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.render.StandardModel;
import cam72cam.mod.util.Hand;
import org.lwjgl.opengl.GL11;

public class RailBaseModel {
	public static StandardModel getModel(TileRailBase te) {
		ItemStack bed = te.getRenderRailBed();
		if (bed == null) {
            // wait for tile to be initialized
			return null;
		}

		float height = te.getBedHeight();
		float tileHeight = height;
		int snow = te.getSnowLayers();
		Augment augment = te.getAugment();
		double gauged = te.getRenderGauge();
		Gauge gauge = Gauge.from(gauged);

		StandardModel model = new StandardModel();
		if (te instanceof TileRail && ((TileRail) te).info != null) {
			model.addCustom(() -> {
				RailInfo info = ((TileRail) te).info;
                if (info.settings.type == TrackItems.SWITCH) {
                    //TODO render switch and don't render turn
                    info = info.withType(TrackItems.STRAIGHT);
                }
                if (info.settings.type == TrackItems.TURNTABLE) {
                	if (MinecraftClient.getPlayer().getHeldItem(Hand.PRIMARY).is(IRItems.ITEM_TRACK_BLUEPRINT)) {
						info = info.withItemHeld(true);
					}
				}


				Vec3d pos = info.placementInfo.placementPosition;
				GL11.glTranslated(pos.x, pos.y, pos.z);

                RailBuilderRender.renderRailBuilder(info, te.world);
			});
		}

		if (augment != null) {
			height = height + 0.1f * (float)gauge.scale() * 1.25f;

			model.addColorBlock(augment.color(), Vec3d.ZERO, new Vec3d(1, height, 1));
			return model;
		}

		height = height + 0.1f * (float)gauge.scale();

		if (snow != 0) {
			model.addSnow(snow + (int)(height * 8), Vec3d.ZERO);
			return model;
		} else if (!bed.isEmpty() && tileHeight != 0.000001f) {
			model.addItemBlock(bed, Vec3d.ZERO, new Vec3d(1, height, 1));
			return model;
		}

		return model;
	}
}
