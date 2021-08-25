package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.model.ComponentRenderer;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.ModCore;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.model.obj.OBJGroup;
import cam72cam.mod.model.obj.OBJModel;
import cam72cam.mod.render.GlobalRender;
import cam72cam.mod.util.Axis;
import org.apache.commons.lang3.ArrayUtils;
import org.lwjgl.opengl.GL11;
import util.Matrix4;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Control {
    public final ModelComponent part;
    public final String controlGroup;
    private Vec3d rotationPoint = null;
    private int rotationDegrees = 0;
    private Axis rotationAxis = null;
    private Map<Axis, Float> translations = new HashMap<>();

    public static List<Control> get(OBJModel model, ComponentProvider provider, ModelComponentType type) {
        return provider.parseAll(type).stream().map(part -> {
            OBJGroup rot = model.groups.values().stream().filter(g -> Pattern.matches(type.regex.replaceAll("#ID#",  part.id + "_ROT"), g.name)).findFirst().orElse(null);
            return new Control(part, rot);
        }).collect(Collectors.toList());
    }

    public Control(ModelComponent part, OBJGroup rot) {
        this.part = part;
        this.controlGroup = part.modelIDs.stream().map(group -> {
            Matcher matcher = Pattern.compile("_CG_([^_]+)").matcher(group);
            return matcher.find() ? matcher.group(1) : null;
        }).filter(Objects::nonNull).findFirst().orElse(part.key);
        if (rot != null) {
            this.rotationPoint = rot.max.add(rot.min).scale(0.5);
            String[] split = rot.name.split("_");
            int idx = ArrayUtils.indexOf(split, "ROT");
            if (idx != ArrayUtils.INDEX_NOT_FOUND) {
                String degrees = split[idx + 1];
                try {
                    rotationDegrees = Integer.parseInt(degrees);
                } catch (NumberFormatException e) {
                    ModCore.error("Unable to parse rotation point '%s': %s", rot.name, e);
                }
            }
            Vec3d delta = rot.min.subtract(rot.max);
            if (Math.abs(delta.x) > Math.abs(delta.y) && Math.abs(delta.x) > Math.abs(delta.z)) {
                rotationAxis = Axis.X;
            } else {
                rotationAxis = Math.abs(delta.y) > Math.abs(delta.z) ? Axis.Y : Axis.Z;
            }
        }

        Pattern pattern = Pattern.compile("TL_([^_]*)_([^_]*)");
        for (String modelID : part.modelIDs) {
            Matcher matcher = pattern.matcher(modelID);
            while (matcher.find()) {
                translations.put(Axis.valueOf(matcher.group(2)), Float.parseFloat(matcher.group(1)));
            }
        }
    }

    public void render(EntityRollingStock stock, ComponentRenderer draw) {
        if (rotationPoint == null && translations.isEmpty()) {
            draw.render(part);
            return;
        }

        float valuePercent = getValue(stock);

        try (ComponentRenderer matrix = draw.push()) {
            translations.forEach((axis, val) -> {
                GL11.glTranslated(
                        axis == Axis.X ? val * valuePercent : 0,
                        axis == Axis.Y ? val * valuePercent : 0,
                        axis == Axis.Z ? val * valuePercent : 0
                );
            });
            if (rotationPoint != null) {
                GL11.glTranslated(rotationPoint.x, rotationPoint.y, rotationPoint.z);
                GL11.glRotated(
                        valuePercent * rotationDegrees,
                        rotationAxis == Axis.X ? 1 : 0,
                        rotationAxis == Axis.Y ? 1 : 0,
                        rotationAxis == Axis.Z ? 1 : 0
                );
                GL11.glTranslated(-rotationPoint.x, -rotationPoint.y, -rotationPoint.z);
            }
            matrix.render(part);
        }
    }

    public void postRender(EntityRollingStock stock) {
        if (!ConfigGraphics.interactiveComponentsOverlay) {
            return;
        }

        if (MinecraftClient.getPlayer().getPosition().distanceTo(stock.getPosition()) > stock.getDefinition().getLength(stock.gauge)) {
            return;
        }

        Vec3d pos = transform(part.center, stock);
        Vec3d playerPos = new Matrix4().rotate(Math.toRadians(stock.getRotationYaw() - 90), 0, 1, 0).apply(MinecraftClient.getPlayer().getPositionEyes().add(MinecraftClient.getPlayer().getLookVector()).subtract(stock.getPosition()));
        if (playerPos.distanceTo(pos) > 0.5) {
            return;
        }

        GlobalRender.drawText(part.type.name().replace("_X", ""), pos, 0.2f, 180 - stock.getRotationYaw() - 90);
    }

    public float getValue(EntityRollingStock stock) {
        return stock.getControlPosition(this) - (part.type == ModelComponentType.REVERSER_X ? 0.5f : 0);
    }

    public Vec3d transform(Vec3d point, EntityRollingStock stock) {
        return transform(point, getValue(stock), stock.gauge.scale());
    }

    protected Vec3d transform(Vec3d point, float valuePercent, double scale) {
        Matrix4 m = new Matrix4();
        m = m.scale(scale, scale, scale);
        for (Map.Entry<Axis, Float> entry : translations.entrySet()) {
            Axis axis = entry.getKey();
            Float val = entry.getValue();
            m = m.translate(
                    axis == Axis.X ? val * valuePercent : 0,
                    axis == Axis.Y ? val * valuePercent : 0,
                    axis == Axis.Z ? val * valuePercent : 0
            );
        }

        if (rotationPoint != null) {
            m = m.translate(rotationPoint.x, rotationPoint.y, rotationPoint.z);
            m = m.rotate(
                    Math.toRadians(valuePercent * rotationDegrees),
                    rotationAxis == Axis.X ? 1 : 0,
                    rotationAxis == Axis.Y ? 1 : 0,
                    rotationAxis == Axis.Z ? 1 : 0
            );
            m = m.translate(-rotationPoint.x, -rotationPoint.y, -rotationPoint.z);
        }
        return m.apply(point);
    }

    public IBoundingBox getBoundingBox(EntityRollingStock stock) {
        return IBoundingBox.from(
                transform(part.min, stock),
                transform(part.max, stock)
        );
    }

    /** Client only! */
    private Vec3d lastClientLook = null;
    public float clientMovementDelta(double x, double y, EntityRollingStock stock) {
        /*
          -X
        -Z * +Z
          +X
         */

        Player player = MinecraftClient.getPlayer();
        float delta = 0;

        Vec3d current = VecUtil.rotateWrongYaw(player.getPositionEyes().subtract(stock.getPosition()), -stock.getRotationYaw());
        Vec3d look = VecUtil.rotateWrongYaw(player.getLookVector(), -stock.getRotationYaw());
        // Rescale along look vector
        double len = 1 + current.add(look).distanceTo(part.center);
        current = current.add(look.scale(len));

        if (lastClientLook != null) {
            Vec3d movement = current.subtract(lastClientLook);
            Vec3d partPos = part.center;
            float applied = (float) (movement.length());
            float value = getValue(stock);
            Vec3d grabComponent = transform(partPos, value, stock.gauge.scale()).add(movement);
            Vec3d grabComponentNext = transform(partPos, value + applied, stock.gauge.scale());
            Vec3d grabComponentPrev = transform(partPos, value - applied, stock.gauge.scale());
            if (grabComponent.distanceTo(grabComponentNext) < grabComponent.distanceTo(grabComponentPrev)) {
                delta += applied;
            } else {
                delta -= applied;
            }
        }
        lastClientLook = current;

        return delta;
    }

    public void stopClientDragging() {
        lastClientLook = null;
    }
}
