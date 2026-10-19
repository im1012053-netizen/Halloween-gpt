package com.halloweenhunt.client;

import com.halloweenhunt.HalloweenHunt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Original glTF skin baked at 60 fps, with per-render interpolation and smooth clip transitions. */
public final class MedusaSnakeModel {
    private static final ResourceLocation DATA = ResourceLocation.fromNamespaceAndPath(HalloweenHunt.MODID, "models/animated/snake_frames.gz");
    private static volatile MedusaSnakeModel loaded;
    private final float[] uv;
    private final int[] indices;
    private final Clip idle, skill;
    private record Clip(float duration, float[][] frames) {}
    private record Sample(float[] a, float[] b, float blend) {
        float value(int i) { return a[i] + (b[i] - a[i]) * blend; }
    }

    private MedusaSnakeModel(DataInputStream in) throws IOException {
        if (in.readInt() != 0x48534e31) throw new IOException("Unknown snake animation format");
        int vertices = in.readInt(), count = in.readInt();
        if (vertices < 1 || vertices > 10000 || count < 3 || count > 60000 || count % 3 != 0)
            throw new IOException("Invalid snake mesh size");
        uv = floats(in, vertices * 2);
        indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = in.readInt();
            if (indices[i] < 0 || indices[i] >= vertices) throw new IOException("Invalid snake index");
        }
        idle = clip(in, vertices); skill = clip(in, vertices);
        if (in.read() != -1) throw new IOException("Unexpected snake animation data");
    }
    private static float[] floats(DataInputStream in, int count) throws IOException {
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = in.readFloat();
            if (!Float.isFinite(values[i])) throw new IOException("Invalid snake float");
        }
        return values;
    }
    private static Clip clip(DataInputStream in, int vertices) throws IOException {
        float seconds = in.readFloat(); int count = in.readInt();
        if (!Float.isFinite(seconds) || seconds <= 0 || seconds > 30 || count < 2 || count > 2000)
            throw new IOException("Invalid snake clip");
        float[][] frames = new float[count][];
        for (int i = 0; i < count; i++) frames[i] = floats(in, vertices * 6);
        return new Clip(seconds, frames);
    }
    public static void reload(ResourceManager resources) {
        try (var input = resources.getResourceOrThrow(DATA).open();
             var in = new DataInputStream(new GZIPInputStream(input))) {
            loaded = new MedusaSnakeModel(in);
        } catch (IOException | RuntimeException ex) {
            loaded = null;
            LogUtils.getLogger().error("Could not load Medusa snake animations", ex);
        }
    }
    public static MedusaSnakeModel loaded() { return loaded; }

    private static Sample sample(Clip clip, double seconds, boolean loop) {
        double t = loop ? seconds % clip.duration() : Math.max(0, Math.min(seconds, clip.duration()));
        float frame = (float) (t / clip.duration() * (clip.frames().length - 1));
        int a = Math.min((int) frame, clip.frames().length - 1), b = Math.min(a + 1, clip.frames().length - 1);
        return new Sample(clip.frames()[a], clip.frames()[b], frame - a);
    }
    public void render(PoseStack.Pose pose, VertexConsumer consumer, int light, double idleTime, double castTime) {
        Sample resting = sample(idle, idleTime, true);
        Sample casting = sample(skill, Math.max(0, castTime), false);
        float mix = 0;
        if (castTime >= 0 && castTime <= skill.duration() + 0.15) {
            mix = (float) Math.min(1, Math.min(castTime / 0.06, (skill.duration() + 0.15 - castTime) / 0.15));
        }
        for (int triangle = 0; triangle < indices.length; triangle += 3) {
            // Vanilla entity render types use quads; repeat the last triangle vertex as a degenerate quad.
            for (int corner = 0; corner < 4; corner++) {
                int vertex = indices[triangle + Math.min(corner, 2)], base = vertex * 6;
                float x = value(resting, casting, base, mix), y = value(resting, casting, base + 1, mix), z = value(resting, casting, base + 2, mix);
                float nx = value(resting, casting, base + 3, mix), ny = value(resting, casting, base + 4, mix), nz = value(resting, casting, base + 5, mix);
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length < 1e-6F) { nx = 0; ny = 1; nz = 0; length = 1; }
                consumer.addVertex(pose.pose(), x, y, z).setColor(-1).setUv(uv[vertex * 2], uv[vertex * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx / length, ny / length, nz / length);
            }
        }
    }
    private static float value(Sample idle, Sample skill, int index, float mix) {
        float a = idle.value(index); return a + (skill.value(index) - a) * mix;
    }
}
