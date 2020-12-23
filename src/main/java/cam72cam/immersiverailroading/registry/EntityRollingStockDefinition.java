package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityBuildableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.library.ItemComponentType;
import cam72cam.immersiverailroading.library.RenderComponentType;
import cam72cam.immersiverailroading.model.RenderComponent;
import cam72cam.immersiverailroading.util.RealBB;
import cam72cam.mod.entity.EntityRegistry;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.resource.IdentifierFileContainer;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapped;
import cam72cam.mod.text.TextUtil;
import cam72cam.mod.world.World;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import friedrichlp.renderlib.library.RenderEffect;
import friedrichlp.renderlib.math.HitBox3;
import friedrichlp.renderlib.model.ModelLoaderProperty;
import friedrichlp.renderlib.tracking.Model;
import friedrichlp.renderlib.tracking.ModelInfo;
import friedrichlp.renderlib.tracking.ModelManager;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

@TagMapped(EntityRollingStockDefinition.TagMapper.class)
public abstract class EntityRollingStockDefinition {
    private static Identifier default_wheel_sound = new Identifier(
            ImmersiveRailroading.MODID,
            "sounds/default/track_wheels.ogg"
    );
    private static Identifier default_clackFront = new Identifier(
            ImmersiveRailroading.MODID,
            "sounds/default/clack.ogg"
    );
    private static Identifier default_clackRear = new Identifier(
            ImmersiveRailroading.MODID,
            "sounds/default/clack.ogg"
    );

    public final String defID;
    private final Class<? extends EntityRollingStock> type;
    public Map<String, String> textureNames = null;
    public float dampeningAmount;
    public Gauge recommended_gauge;
    public Boolean shouldSit;
    public Identifier wheel_sound;
    public Identifier clackFront;
    public Identifier clackRear;
    protected ModelInfo model;
    protected double frontBounds;
    protected double rearBounds;
    protected double heightBounds;
    protected double widthBounds;
    protected Map<RenderComponentType, List<RenderComponent>> renderComponents;
    double internal_model_scale;
    double internal_inv_scale;
    private String name = "Unknown";
    private String modelerName = "N/A";
    private String packName = "N/A";
    private Vec3d passengerCenter = new Vec3d(0, 0, 0);
    private float bogeyFront;
    private float bogeyRear;
    private float couplerOffsetFront;
    private float couplerOffsetRear;
    private boolean scalePitch;
    private double passengerCompartmentLength;
    private double passengerCompartmentWidth;
    private int weight;
    private int maxPassengers;
    private ArrayList<ItemComponentType> itemComponents;
    private HeightmapCache cache;

    public EntityRollingStockDefinition(Class<? extends EntityRollingStock> type, String defID, JsonObject data) throws Exception {
        this.type = type;
        this.defID = defID;
        if (data == null) {
            // USED ONLY RIGHT BEFORE REMOVING UNKNOWN STOCK

            renderComponents = new HashMap<>();
            itemComponents = new ArrayList<>();

            return;
        }

        parseJson(data);

        model.getParts().onInit(parts -> {
            model.getHitbox().onInit(box -> {
                RenderComponent rc = RenderComponent.parse(RenderComponentType.REMAINING, this,
                                                           parseComponents(parts.data.keySet()));
                addComponentIfExists(rc, true);
            });
        });

        cache = model.getAdditionalCacheData(HeightmapCache.class);
    }

    public final EntityRollingStock spawn(World world, Vec3d pos, float yaw, Gauge gauge, String texture) {
        EntityRollingStock stock = (EntityRollingStock) EntityRegistry.create(world, type);
        stock.setPosition(pos);
        stock.setRotationYaw(yaw);
        // Override prev
        stock.setRotationYaw(yaw);
        stock.setup(defID, gauge, texture);

        return stock;
    }

    public boolean shouldScalePitch() {
        return scalePitch;
    }

    public void parseJson(JsonObject data) throws Exception {
        name = data.get("name").getAsString();
        if (data.has("modeler")) {
            this.modelerName = data.get("modeler").getAsString();
        }
        if (data.has("pack")) {
            this.packName = data.get("pack").getAsString();
        }
        float darken = 0;
        if (data.has("darken_model")) {
            darken = data.get("darken_model").getAsFloat();
        }
        internal_model_scale = 1;
        internal_inv_scale = 1;
        // TODO Gauge.from(Gauge.STANDARD).value() what happens when != Gauge.STANDARD
        this.recommended_gauge = Gauge.from(Gauge.STANDARD);
        if (data.has("model_gauge_m")) {
            this.recommended_gauge = Gauge.from(data.get("model_gauge_m").getAsDouble());
            internal_model_scale = Gauge.STANDARD / data.get("model_gauge_m").getAsDouble();
        }
        if (data.has("recommended_gauge_m")) {
            this.recommended_gauge = Gauge.from(data.get("recommended_gauge_m").getAsDouble());
        }
        if (this.recommended_gauge != Gauge.from(Gauge.STANDARD)) {
            internal_inv_scale = Gauge.STANDARD / recommended_gauge.value();
        }

        model = ModelManager.registerModel(
                new IdentifierFileContainer(new Identifier(data.get("model").getAsString())),
                new ModelLoaderProperty(darken).addAdditionalCacheData(HeightmapCache.class)
        );
        //model.addRenderEffect(RenderEffect.NORMAL_LIGHTING);

        // recalculate the cache if the model is loaded from the obj file (when the cache is updated)
        model.registerMeshHook(mesh -> {
            cache.recalculate(this, mesh);
        });
        model.getHitbox().onInit(box -> {
            frontBounds = -box.minX + couplerOffsetFront;
            rearBounds = box.maxX + couplerOffsetRear;
            widthBounds = box.maxZ - box.minZ;
        });
        model.forceLoad();

        textureNames = new LinkedHashMap<>();
        textureNames.put(null, "Default");
        if (data.has("tex_variants")) {
            JsonElement variants = data.get("tex_variants");
            for (Entry<String, JsonElement> variant : variants.getAsJsonObject().entrySet()) {
                textureNames.put(variant.getValue().getAsString(), variant.getKey());
            }
        }

        Identifier alt_textures = new Identifier(ImmersiveRailroading.MODID, defID.replace(".json", "_variants.json"));
        try {
            List<InputStream> alts = alt_textures.getResourceStreamAll();
            for (InputStream input : alts) {
                JsonParser parser = new JsonParser();
                JsonElement variants = parser.parse(new InputStreamReader(input));
                for (Entry<String, JsonElement> variant : variants.getAsJsonObject().entrySet()) {
                    textureNames.put(variant.getValue().getAsString(), variant.getKey());
                }
            }
        } catch (java.io.FileNotFoundException ex) {
            //ignore
        }

        JsonObject passenger = data.get("passenger").getAsJsonObject();
        passengerCenter = new Vec3d(0, passenger.get("center_y").getAsDouble() - 0.35,
                                    passenger.get("center_x").getAsDouble()
        ).scale(internal_model_scale);
        passengerCompartmentLength = passenger.get("length").getAsDouble() * internal_model_scale;
        passengerCompartmentWidth = passenger.get("width").getAsDouble() * internal_model_scale;
        maxPassengers = passenger.get("slots").getAsInt();
        if (passenger.has("should_sit")) {
            shouldSit = passenger.get("should_sit").getAsBoolean();
        }

        bogeyFront = (float) (data.get("trucks").getAsJsonObject().get("front").getAsFloat() * internal_model_scale);
        bogeyRear = (float) (data.get("trucks").getAsJsonObject().get("rear").getAsFloat() * internal_model_scale);

        dampeningAmount = 0.75f;
        if (data.has("sound_dampening_percentage")) {
            if (data.get("sound_dampening_percentage").getAsFloat() >= 0.0f && data.get(
                    "sound_dampening_percentage").getAsFloat() <= 1.0f) {
                dampeningAmount = data.get("sound_dampening_percentage").getAsFloat();
            }
        }

        scalePitch = true;
        if (data.has("scale_pitch")) {
            scalePitch = data.get("scale_pitch").getAsBoolean();
        }

        if (data.has("couplers")) {
            couplerOffsetFront = (float) (data.get("couplers").getAsJsonObject().get(
                    "front_offset").getAsFloat() * internal_model_scale);
            couplerOffsetRear = (float) (data.get("couplers").getAsJsonObject().get(
                    "rear_offset").getAsFloat() * internal_model_scale);
        }

        // Bad hack for height bounds
        model.getParts().onInit(parts -> {
            ArrayList<String> heightParts = new ArrayList<>();
            for (String part : parts.data.keySet()) {
                boolean ignore = false;
                for (RenderComponentType rct : RenderComponentType.values()) {
                    if (rct.collisionsEnabled) {
                        continue;
                    }
                    for (int i = 0; i < 10; i++) {
                        if (Pattern.matches(rct.regex.replace("#ID#", "" + i), part)) {
                            ignore = true;
                            break;
                        }
                    }
                    if (ignore) {
                        break;
                    }
                }
                if (!ignore) {
                    heightParts.add(part);
                }
            }

            model.getHitbox(heightParts).onSet(box -> {
                heightBounds = box.maxY - box.minY;
            });
        });

        weight = (int) Math.ceil(
                data.get("properties").getAsJsonObject().get("weight_kg").getAsInt() * internal_inv_scale);

        wheel_sound = default_wheel_sound;
        clackFront = default_clackFront;
        clackRear = default_clackRear;

        JsonObject sounds = data.has("sounds") ? data.get("sounds").getAsJsonObject() : null;
        if (sounds != null) {
            if (sounds.has("wheels")) {
                wheel_sound = new Identifier(
                        ImmersiveRailroading.MODID,
                        sounds.get("wheels").getAsString()
                ).getOrDefault(default_wheel_sound);
            }

            if (sounds.has("clack")) {
                clackFront = new Identifier(ImmersiveRailroading.MODID, sounds.get("clack").getAsString()).getOrDefault(
                        default_clackFront);
                clackRear = new Identifier(ImmersiveRailroading.MODID, sounds.get("clack").getAsString()).getOrDefault(
                        default_clackRear);
            }
            if (sounds.has("clack_front")) {
                clackFront = new Identifier(
                        ImmersiveRailroading.MODID,
                        sounds.get("clack_front").getAsString()
                ).getOrDefault(default_clackFront);
            }
            if (sounds.has("clack_rear")) {
                clackRear = new Identifier(
                        ImmersiveRailroading.MODID,
                        sounds.get("clack_rear").getAsString()
                ).getOrDefault(default_clackRear);
            }
        }
    }

    protected void addComponentIfExists(RenderComponent renderComponent, boolean itemComponent) {
        if (renderComponent != null) {
            if (!renderComponents.containsKey(renderComponent.type)) {
                renderComponents.put(renderComponent.type, new ArrayList<>());
            }
            renderComponents.get(renderComponent.type).add(0, renderComponent);

            if (itemComponent && renderComponent.type != RenderComponentType.REMAINING) {
                itemComponents.add(0, ItemComponentType.from(renderComponent.type));
            }
        }
    }

    protected boolean unifiedBogies() {
        return true;
    }


    protected Set<String> parseComponents(ObjectSet<String> parts) {
        renderComponents = new HashMap<>();
        itemComponents = new ArrayList<>();

        Set<String> groups = new HashSet<>(parts);

        for (int i = 100; i >= 0; i--) {
            if (unifiedBogies()) {
                addComponentIfExists(
                        RenderComponent.parsePosID(RenderComponentType.BOGEY_POS_WHEEL_X, this, groups, "FRONT", i),
                        true
                );
                addComponentIfExists(
                        RenderComponent.parsePosID(RenderComponentType.BOGEY_POS_WHEEL_X, this, groups, "REAR", i),
                        true
                );
            } else {
                addComponentIfExists(
                        RenderComponent.parseID(RenderComponentType.BOGEY_FRONT_WHEEL_X, this, groups, i),
                        true
                );
                addComponentIfExists(
                        RenderComponent.parseID(RenderComponentType.BOGEY_REAR_WHEEL_X, this, groups, i),
                        true
                );
            }
            addComponentIfExists(RenderComponent.parseID(RenderComponentType.FRAME_WHEEL_X, this, groups, i), true);
        }
        if (unifiedBogies()) {
            addComponentIfExists(RenderComponent.parsePos(RenderComponentType.BOGEY_POS, this, groups, "FRONT"), true);
            addComponentIfExists(RenderComponent.parsePos(RenderComponentType.BOGEY_POS, this, groups, "REAR"), true);
        } else {
            addComponentIfExists(RenderComponent.parse(RenderComponentType.BOGEY_FRONT, this, groups), true);
            addComponentIfExists(RenderComponent.parse(RenderComponentType.BOGEY_REAR, this, groups), true);
        }

        addComponentIfExists(RenderComponent.parse(RenderComponentType.FRAME, this, groups), true);
        addComponentIfExists(RenderComponent.parse(RenderComponentType.SHELL, this, groups), true);
        for (int i = 100; i >= 1; i--) {
            addComponentIfExists(RenderComponent.parseID(RenderComponentType.CARGO_FILL_X, this, groups, i), false);
        }

        return groups;
    }

    public RenderComponent getComponent(RenderComponentType name, Gauge gauge) {
        if (!renderComponents.containsKey(name)) {
            return null;
        }
        return renderComponents.get(name).get(0).scale(gauge);
    }

    public RenderComponent getComponent(RenderComponentType name, String pos, Gauge gauge) {
        if (!renderComponents.containsKey(name)) {
            return null;
        }
        for (RenderComponent c : renderComponents.get(name)) {
            if (c.pos.equals(pos)) {
                return c.scale(gauge);
            }
            if (c.side.equals(pos)) {
                return c.scale(gauge);
            }
        }
        return null;
    }

    public List<RenderComponent> getComponents(RenderComponentType name, Gauge gauge) {
        if (!renderComponents.containsKey(name)) {
            return null;
        }
        List<RenderComponent> components = new ArrayList<>();
        for (RenderComponent c : renderComponents.get(name)) {
            components.add(c.scale(gauge));
        }
        return components;
    }

    public List<RenderComponent> getComponents(RenderComponentType name, String pos, Gauge gauge) {
        if (!renderComponents.containsKey(name)) {
            return null;
        }
        List<RenderComponent> components = new ArrayList<>();
        for (RenderComponent c : renderComponents.get(name)) {
            if (c.pos.equals(pos)) {
                components.add(c.scale(gauge));
            }
        }
        return components;
    }

    public Vec3d correctPassengerBounds(Gauge gauge, Vec3d pos, boolean shouldSit) {
        double gs = gauge.scale();
        Vec3d passengerCenter = this.passengerCenter.scale(gs);
        pos = pos.subtract(passengerCenter);
        if (pos.z > this.passengerCompartmentLength * gs) {
            pos = new Vec3d(pos.x, pos.y, this.passengerCompartmentLength * gs);
        }

        if (pos.z < -this.passengerCompartmentLength * gs) {
            pos = new Vec3d(pos.x, pos.y, -this.passengerCompartmentLength * gs);
        }

        if (Math.abs(pos.x) > this.passengerCompartmentWidth / 2 * gs) {
            pos = new Vec3d(Math.copySign(this.passengerCompartmentWidth / 2 * gs, pos.x), pos.y, pos.z);
        }

        pos = new Vec3d(pos.x, passengerCenter.y - (shouldSit ? 0.75 : 0), pos.z + passengerCenter.z);

        return pos;
    }

    public boolean isAtFront(Gauge gauge, Vec3d pos) {
        pos = pos.subtract(passengerCenter.scale(gauge.scale()));
        return pos.z >= this.passengerCompartmentLength * gauge.scale();
    }

    public boolean isAtRear(Gauge gauge, Vec3d pos) {
        pos = pos.subtract(passengerCenter.scale(gauge.scale()));
        return pos.z <= -this.passengerCompartmentLength * gauge.scale();
    }

    public List<ItemComponentType> getItemComponents() {
        if (itemComponents == null) {
            // wait until the model is loaded so that the components
            // are properly initialized.
            model.awaitLoad();
        }

        return itemComponents;
    }

    public float getBogeyFront(Gauge gauge) {
        return (float) gauge.scale() * this.bogeyFront;
    }

    public float getBogeyRear(Gauge gauge) {
        return (float) gauge.scale() * this.bogeyRear;
    }

    public double getCouplerPosition(CouplerType coupler, Gauge gauge) {
        switch (coupler) {
            case FRONT:
                return gauge.scale() * (this.frontBounds);
            case BACK:
                return gauge.scale() * (this.rearBounds);
            default:
                return 0;
        }
    }

    public float[][] createHeightMap(EntityBuildableRollingStock stock) {
        float[][] heightMap = new float[cache.xRes][cache.zRes];

        List<RenderComponentType> availComponents = new ArrayList<>();
        for (ItemComponentType item : stock.getItemComponents()) {
            availComponents.addAll(item.render);
        }

        for (List<RenderComponent> rcl : this.renderComponents.values()) {
            for (RenderComponent rc : rcl) {
                if (!rc.type.collisionsEnabled) {
                    continue;
                }

                if (availComponents.contains(rc.type)) {
                    availComponents.remove(rc.type);
                } else if (rc.type == RenderComponentType.REMAINING && stock.isBuilt()) {
                    //pass
                } else {
                    continue;
                }

                float[][] pm = cache.partMapCache.get(rc);
                for (int x = 0; x < cache.xRes; x++) {
                    for (int z = 0; z < cache.zRes; z++) {
                        heightMap[x][z] = Math.max(heightMap[x][z], pm[x][z]);
                    }
                }
            }
        }

        return heightMap;
    }

    public RealBB getBounds(EntityMoveableRollingStock stock, Gauge gauge) {
        return new RealBB(gauge.scale() * frontBounds, gauge.scale() * -rearBounds, gauge.scale() * widthBounds,
                          gauge.scale() * heightBounds, stock.getRotationYaw()
        ).offset(stock.getPosition());
    }

    public String name() {
        String[] sp = this.defID.replaceAll(".json", "").split("/");
        String localStr = String.format("%s:entity.%s.%s", ImmersiveRailroading.MODID, sp[sp.length - 2], sp[sp.length - 1]);
        String transStr = TextUtil.translate(localStr);
        return !localStr.equals(transStr) ? transStr : name;
    }

    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = new ArrayList<>();
        tips.add(GuiText.WEIGHT_TOOLTIP.toString(this.getWeight(gauge)));
        tips.add(GuiText.MODELER_TOOLTIP.toString(modelerName));
        tips.add(GuiText.PACK_TOOLTIP.toString(packName));
        return tips;
    }

    public ModelInfo getModel() {
        return model;
    }

    /**
     * @return Stock Weight in Kg
     */
    public int getWeight(Gauge gauge) {
        return (int) Math.ceil(gauge.scale() * this.weight);
    }

    public double getHeight(Gauge gauge) {
        return gauge.scale() * this.heightBounds;
    }

    public double getWidth(Gauge gauge) {
        return gauge.scale() * this.widthBounds;
    }

    public double getLength(Gauge gauge) {
        return gauge.scale() * this.frontBounds + this.rearBounds;
    }

    public int getMaxPassengers() {
        return this.maxPassengers;
    }

    public boolean acceptsPassengers() {
        return false;
    }

    public boolean acceptsLivestock() {
        return false;
    }

    public void clearModel() {
        this.model = null;
    }

    static class TagMapper implements cam72cam.mod.serialization.TagMapper<EntityRollingStockDefinition> {
        @Override
        public TagAccessor<EntityRollingStockDefinition> apply(Class<EntityRollingStockDefinition> type, String fieldName, TagField tag) {
            return new TagAccessor<>(
                    (d, o) -> d.setString(fieldName, o == null ? null : o.defID),
                    d -> DefinitionManager.getDefinition(d.getString(fieldName))
            );
        }
    }
}
