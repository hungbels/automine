package com.example.automine;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import static com.example.automine.command.AutoMineCommand.register;

public class AutoMineMod implements ClientModInitializer {

    public static final AutoMineManager MANAGER = new AutoMineManager();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> MANAGER.tick(client));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                register(dispatcher, MANAGER));
    }
}
