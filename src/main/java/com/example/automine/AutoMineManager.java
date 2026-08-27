package com.example.automine;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Dieu khien auto mine theo mat cat 3x3 ("cup 3x3"), luon doi chieu voi
 * SchematicData truoc khi tien sang o/lop tiep theo. Nguyen tac cot loi:
 * KHONG BAO GIO quyet dinh dua tren vi tri vat ly cua player, chi dua
 * vao index (layerIndex, cellIndex) va toa do tuong doi trong schem.
 */
public class AutoMineManager {

    public enum State {
        IDLE,
        MINING,
        FILLING_HOLE,
        CLIMBING_OUT,
        EATING
    }

    // Thu tu 9 o co dinh trong mat cat 3x3 (right, up), KHONG duoc doi thu tu
    // giua cac lop vi day la chot chinh de khong lech schem.
    private static final int[][] CELL_ORDER = {
            {-1, 1}, {0, 1}, {1, 1},   // hang tren
            {-1, 0}, {0, 0}, {1, 0},   // hang giua
            {-1, -1}, {0, -1}, {1, -1} // hang duoi
    };

    private State state = State.IDLE;
    private SchematicData schematic;
    private BlockPos origin;
    private Direction facing;
    private Direction rightDir;
    private Direction upDir = Direction.UP;

    private int layerIndex = 0;
    private int cellIndex = 0;

    // nguong hunger de kich hoat auto eat (0-20)
    private static final int EAT_THRESHOLD = 14;

    public void start(SchematicData schematic, BlockPos origin, Direction facing) {
        this.schematic = schematic;
        this.origin = origin;
        this.facing = facing;
        this.rightDir = facing.rotateYClockwise();
        this.layerIndex = 0;
        this.cellIndex = 0;
        this.state = State.MINING;
    }

    public void stop() {
        this.state = State.IDLE;
    }

    public State getState() {
        return state;
    }

    /** Goi moi tick tu client tick event. */
    public void tick(MinecraftClient client) {
        if (state == State.IDLE || client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;
        World world = client.world;

        // Uu tien tuyet doi: an neu doi, tam dung dao
        if (player.getHungerManager().getFoodLevel() <= EAT_THRESHOLD) {
            handleEating(player);
            return;
        }

        // Kiem tra ho bat thuong duoi chan (khong co trong schem) -> leo len truoc
        if (isUnexpectedHoleBelow(player, world)) {
            handleClimbOut(player, world);
            return;
        }

        // Xu ly o hien tai theo dung schem
        processCurrentCell(player, world);
    }

    private void processCurrentCell(ClientPlayerEntity player, World world) {
        if (cellIndex >= CELL_ORDER.length) {
            // Het 9 o cua lop nay va tat ca da khop schem -> sang lop moi
            layerIndex++;
            cellIndex = 0;
            return;
        }

        int[] cell = CELL_ORDER[cellIndex];
        BlockPos target = computeTargetPos(layerIndex, cell[0], cell[1]);

        SchematicData.Entry expected = findSchemEntry(layerIndex, cell[0], cell[1]);
        if (expected == null) {
            // Khong co du lieu schem cho o nay -> coi nhu da xong, bo qua an toan
            cellIndex++;
            return;
        }

        boolean isAirExpected = expected.block.equals("minecraft:air");
        boolean worldIsAir = world.getBlockState(target).isAir();

        if (isAirExpected && !worldIsAir) {
            // Can dao o nay
            breakBlock(player, world, target);
            // KHONG tang cellIndex ngay: se xac nhan lai o tick sau khi block that su bien mat
            return;
        }

        if (!isAirExpected && worldIsAir) {
            // Schem yeu cau co khoi nhung thuc te la ho -> fill lai dung schem
            fillHole(player, world, target, expected.block);
            return;
        }

        // O nay da khop schem (dung la air can dao va da dao xong, hoac dung la
        // khoi va khoi van con). Xac nhan xong, chuyen sang o tiep theo.
        cellIndex++;
    }

    private BlockPos computeTargetPos(int layer, int right, int up) {
        BlockPos forwardOffset = origin.offset(facing, layer);
        BlockPos rightOffset = forwardOffset.offset(rightDir, right);
        return rightOffset.offset(upDir, up);
    }

    private SchematicData.Entry findSchemEntry(int layer, int right, int up) {
        // Chuyen (layer, right, up) ve toa do tuong doi (x,y,z) dung quy uoc
        // luc tao schem: forward = facing, nen can anh xa nguoc lai truc x/z that.
        int fx = facing.getOffsetX() * layer + rightDir.getOffsetX() * right;
        int fz = facing.getOffsetZ() * layer + rightDir.getOffsetZ() * right;
        int fy = up;

        for (SchematicData.Entry e : schematic.getEntries()) {
            if (e.x == fx && e.y == fy && e.z == fz) {
                return e;
            }
        }
        return null;
    }

    private void breakBlock(ClientPlayerEntity player, World world, BlockPos pos) {
        // Goi qua interaction manager cua client de pha block dung cach
        // (khong teleport/khong dung packet gia mao, chi mo phong thao tac binh thuong)
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            client.interactionManager.attackBlock(pos, facing.getOpposite());
        }
    }

    private void fillHole(ClientPlayerEntity player, World world, BlockPos pos, String blockId) {
        // Yeu cau player dang cam dung item trong hotbar khop voi blockId.
        // Don gian hoa: giat sang slot co item phu hop neu co, roi place.
        int slot = findMatchingHotbarSlot(player, blockId);
        if (slot < 0) return; // khong co block phu hop trong hotbar, bo qua an toan

        player.getInventory().selectedSlot = slot;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            // dat block: can blockHitResult that, day la ban rut gon de minh hoa logic
            client.interactionManager.interactBlock(player, Hand.MAIN_HAND, null);
        }
    }

    private int findMatchingHotbarSlot(ClientPlayerEntity player, String blockId) {
        for (int i = 0; i < 9; i++) {
            var stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem().toString().contains(blockId.replace("minecraft:", ""))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isUnexpectedHoleBelow(ClientPlayerEntity player, World world) {
        BlockPos below = player.getBlockPos().down();
        if (!world.getBlockState(below).isAir()) return false;

        SchematicData.Entry expectedBelow = findSchemEntry(layerIndex, 0, -1);
        // Neu schem cung noi la air thi day la ho DU KIEN, khong phai bat thuong
        return expectedBelow == null || !expectedBelow.block.equals("minecraft:air");
    }

    private void handleClimbOut(ClientPlayerEntity player, World world) {
        state = State.CLIMBING_OUT;
        int slot = findAnyBlockSlot(player);
        if (slot < 0) {
            // khong co block de pillar-up, danh phai dung dao va canh bao
            state = State.MINING;
            return;
        }
        player.getInventory().selectedSlot = slot;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            client.interactionManager.interactBlock(player, Hand.MAIN_HAND, null);
        }
        player.jump();
        state = State.MINING;
    }

    private int findAnyBlockSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            var stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem().toString().contains("Block")) {
                return i;
            }
        }
        return -1;
    }

    private void handleEating(ClientPlayerEntity player) {
        state = State.EATING;
        for (int i = 0; i < 9; i++) {
            var stack = player.getInventory().getStack(i);
            if (stack.get(net.minecraft.component.DataComponentTypes.FOOD) != null) {
                player.getInventory().selectedSlot = i;
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.interactionManager != null) {
                    client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                }
                break;
            }
        }
        state = State.MINING;
    }
          }
