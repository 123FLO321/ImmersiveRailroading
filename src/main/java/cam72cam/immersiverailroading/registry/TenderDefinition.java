package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.entity.Tender;
import cam72cam.immersiverailroading.util.DataBlock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.model.FreightTankModel;
import cam72cam.immersiverailroading.model.StockModel;

import java.util.List;

public class TenderDefinition extends CarTankDefinition {
    private int numSlots;
    private int width;
    private boolean showCurrentLoadOnly;

    public TenderDefinition(String defID, DataBlock data) throws Exception {
        super(Tender.class, defID, data);
    }

    @Override
    public void loadData(DataBlock data) throws Exception {
        super.loadData(data);

        DataBlock tender = data.getBlock("tender");
        this.numSlots = (int) Math.ceil(tender.getValue("slots").asInteger() * internal_inv_scale);
        this.width = (int) Math.ceil(tender.getValue("width").asInteger() * internal_inv_scale);
        this.showCurrentLoadOnly = tender.getValue("show_current_load_only").asBoolean(false);
    }

    @Override
    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = super.getTooltip(gauge);
        tips.add(GuiText.FREIGHT_CAPACITY_TOOLTIP.toString(this.getInventorySize(gauge)));
        return tips;
    }

    public int getInventorySize(Gauge gauge) {
        return (int) Math.ceil(numSlots * gauge.scale());
    }

    public int getInventoryWidth(Gauge gauge) {
        return (int) Math.ceil(width * gauge.scale());
    }

    public boolean shouldShowCurrentLoadOnly() {
        return this.showCurrentLoadOnly;
    }

    @Override
    protected StockModel<?, ?> createModel() throws Exception {
        return new FreightTankModel<>(this);
    }
}
