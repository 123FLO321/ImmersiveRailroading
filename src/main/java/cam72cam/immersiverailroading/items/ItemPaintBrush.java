package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.mod.item.CreativeTab;
import cam72cam.mod.item.CustomItem;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.Recipes;

import java.util.Collections;
import java.util.List;

public class ItemPaintBrush extends CustomItem {
	public ItemPaintBrush() {
		super(ImmersiveRailroading.MODID, "item_paint_brush");

		Recipes.shapedRecipe(this, 1,
				Fuzzy.WOOL_BLOCK, Fuzzy.IRON_INGOT, Fuzzy.WOOD_STICK);
	}

	@Override
	public int getStackSize() {
		return 1;
	}

	@Override
	public List<CreativeTab> getCreativeTabs() {
		return Collections.singletonList(ItemTabs.MAIN_TAB);
	}

}
