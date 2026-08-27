package com.example.automine;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.math.BlockPos;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Dinh dang schematic don gian, tu tao bang tay hoac script:
 * [
 *   {"x":0,"y":0,"z":0,"block":"minecraft:air"},
 *   {"x":0,"y":0,"z":1,"block":"minecraft:stone"}
 * ]
 * Toa do la RELATIVE so voi diem bat dau (goc) khi bat auto mine.
 * block = "minecraft:air" nghia la vi tri nay CAN DUOC DAO (loai bo).
 * block = ten khoi khac nghia la vi tri nay CAN CO KHOI DO (neu la ho/trong thi se tu lap).
 */
public class SchematicData {

    public static class Entry {
        public int x, y, z;
        public String block;
    }

    private final List<Entry> entries;

    private SchematicData(List<Entry> entries) {
        this.entries = entries;
    }

    public static SchematicData loadFromFile(String path) throws Exception {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<Entry>>() {}.getType();
        try (FileReader reader = new FileReader(path)) {
            List<Entry> entries = gson.fromJson(reader, listType);
            return new SchematicData(entries);
        }
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public BlockPos toAbsolute(Entry e, BlockPos origin) {
        return origin.add(e.x, e.y, e.z);
    }
}
