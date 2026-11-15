package dev.yimeng.schematictotem.mesh;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block models for tests without game resources: every box of a block's outline shape becomes six quads, faces on
 * the block boundary are culled like a cullface. Quad counts come close to the real models (cubes, slabs, stairs,
 * fences...); sprites are null, which the baker only passes through.
 */
final class StubBlockModels extends BlockModels {
    private final Map<BlockState, BlockStateModel> cache = new ConcurrentHashMap<>();

    StubBlockModels() {
        super(null);
    }

    @Override
    public BlockStateModel getModel(BlockState state) {
        return cache.computeIfAbsent(state, StubModel::new);
    }

    private static final class StubModel implements BlockStateModel {
        private final BlockModelPart part;

        StubModel(BlockState state) {
            Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
            for (Direction d : Direction.values()) culled.put(d, new ArrayList<>());
            List<BakedQuad> inner = new ArrayList<>();
            List<Box> boxes;
            try {
                boxes = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes();
            } catch (RuntimeException e) {
                boxes = List.of(new Box(0, 0, 0, 1, 1, 1));
            }
            for (Box box : boxes) {
                for (Direction d : Direction.values()) {
                    BakedQuad quad = face(box, d);
                    boolean onBoundary = switch (d) {
                        case DOWN -> box.minY <= 0.0;
                        case UP -> box.maxY >= 1.0;
                        case NORTH -> box.minZ <= 0.0;
                        case SOUTH -> box.maxZ >= 1.0;
                        case WEST -> box.minX <= 0.0;
                        case EAST -> box.maxX >= 1.0;
                    };
                    (onBoundary ? culled.get(d) : inner).add(quad);
                }
            }
            this.part = new BlockModelPart() {
                @Override
                public List<BakedQuad> getQuads(@Nullable Direction side) {
                    return side == null ? inner : culled.get(side);
                }

                @Override
                public boolean useAmbientOcclusion() {
                    return true;
                }

                @Override
                public Sprite particleSprite() {
                    return null;
                }
            };
        }

        private static BakedQuad face(Box b, Direction d) {
            float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
            float[][] v = switch (d) {
                case UP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                case DOWN -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
                case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
                case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
                case WEST -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
                case EAST -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
            };
            return new BakedQuad(vec(v[0]), vec(v[1]), vec(v[2]), vec(v[3]), 0L, 0L, 0L, 0L, -1, d, null, true, 0);
        }

        private static Vector3f vec(float[] p) {
            return new Vector3f(p[0], p[1], p[2]);
        }

        @Override
        public void addParts(Random random, List<BlockModelPart> parts) {
            parts.add(part);
        }

        @Override
        public Sprite particleSprite() {
            return null;
        }
    }
}
