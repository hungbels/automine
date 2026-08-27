package com.example.automine.command;

import com.example.automine.AutoMineManager;
import com.example.automine.SchematicData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class AutoMineCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, AutoMineManager manager) {
        dispatcher.register(literal("automine")
                .then(literal("start")
                        .then(argument("schemPath", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String path = StringArgumentType.getString(ctx, "schemPath");
                                    var source = ctx.getSource();
                                    try {
                                        SchematicData data = SchematicData.loadFromFile(path);
                                        var player = source.getPlayer();
                                        manager.start(data, player.getBlockPos(), player.getHorizontalFacing());
                                        source.sendFeedback(Text.literal("AutoMine: bat dau voi schem " + path));
                                    } catch (Exception e) {
                                        source.sendFeedback(Text.literal("Loi doc schem: " + e.getMessage()));
                                    }
                                    return 1;
                                })))
                .then(literal("stop")
                        .executes(ctx -> {
                            manager.stop();
                            ctx.getSource().sendFeedback(Text.literal("AutoMine: da dung"));
                            return 1;
                        })));
    }
}
