package dev.testbuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class FabricCompatInstaller {
    static List<Path> install(Path gameJava) throws IOException {
        List<Path> out = new ArrayList<>();
        out.add(write(gameJava, "net/fabricmc/fabric/api/event/Event.java", eventSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/event/EventFactory.java", eventFactorySource()));
        out.add(write(gameJava, "net/fabricmc/fabric/impl/base/event/WebArrayBackedEvent.java", arrayBackedEventSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java", clientTickEventsSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/entity/event/v1/ServerPlayerEvents.java", serverPlayerEventsSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry.java", payloadTypeRegistrySource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking.java", serverPlayNetworkingSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking.java", clientPlayNetworkingSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/recipe/v1/sync/RecipeSynchronization.java", recipeSynchronizationSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/item/v1/DefaultItemComponentEvents.java", defaultItemComponentsSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/client/item/v1/ItemTooltipCallback.java", itemTooltipSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/client/rendering/v1/ClientTooltipComponentCallback.java", clientTooltipSource()));
        out.add(write(gameJava, "net/fabricmc/fabric/api/event/client/player/ClientPreAttackCallback.java", clientPreAttackSource()));
        return List.copyOf(out);
    }

    private static Path write(Path root, String relative, String content) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static String eventSource() {
        return """
                package net.fabricmc.fabric.api.event;

                import net.minecraft.resources.Identifier;

                public abstract class Event<T> {
                    protected volatile T invoker;
                    public static final Identifier DEFAULT_PHASE = Identifier.fromNamespaceAndPath("fabric", "default");

                    public final T invoker() {
                        return invoker;
                    }

                    public abstract void register(T listener);

                    public void register(Identifier phase, T listener) {
                        register(listener);
                    }

                    public void addPhaseOrdering(Identifier firstPhase, Identifier secondPhase) {
                    }
                }
                """;
    }

    private static String eventFactorySource() {
        return """
                package net.fabricmc.fabric.api.event;

                import java.util.function.Function;
                import net.minecraft.resources.Identifier;
                import net.fabricmc.fabric.impl.base.event.WebArrayBackedEvent;

                public final class EventFactory {
                    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> factory) {
                        return new WebArrayBackedEvent<>(type, factory);
                    }

                    public static <T> Event<T> createArrayBacked(Class<T> type, T emptyInvoker, Function<T[], T> factory) {
                        return createArrayBacked(type, listeners -> {
                            if (listeners.length == 0) return emptyInvoker;
                            if (listeners.length == 1) return listeners[0];
                            return factory.apply(listeners);
                        });
                    }

                    public static <T> Event<T> createWithPhases(Class<? super T> type, Function<T[], T> factory, Identifier... phases) {
                        return createArrayBacked(type, factory);
                    }

                    private EventFactory() {}
                }
                """;
    }

    private static String arrayBackedEventSource() {
        return """
                package net.fabricmc.fabric.impl.base.event;

                import java.lang.reflect.Array;
                import java.util.ArrayList;
                import java.util.List;
                import java.util.Objects;
                import java.util.function.Function;
                import net.fabricmc.fabric.api.event.Event;

                public final class WebArrayBackedEvent<T> extends Event<T> {
                    private final Class<?> type;
                    private final Function<T[], T> factory;
                    private final List<T> listeners = new ArrayList<>();

                    public WebArrayBackedEvent(Class<?> type, Function<T[], T> factory) {
                        this.type = Objects.requireNonNull(type);
                        this.factory = Objects.requireNonNull(factory);
                        rebuild();
                    }

                    @Override
                    public void register(T listener) {
                        listeners.add(Objects.requireNonNull(listener));
                        rebuild();
                    }

                    @SuppressWarnings("unchecked")
                    private void rebuild() {
                        T[] array = (T[]) Array.newInstance(type, listeners.size());
                        for (int i = 0; i < listeners.size(); i++) array[i] = listeners.get(i);
                        invoker = factory.apply(array);
                    }
                }
                """;
    }

    private static String clientTickEventsSource() {
        return """
                package net.fabricmc.fabric.api.client.event.lifecycle.v1;

                import net.fabricmc.fabric.api.event.Event;
                import net.fabricmc.fabric.api.event.EventFactory;
                import net.minecraft.client.Minecraft;
                import net.minecraft.client.multiplayer.ClientLevel;

                public final class ClientTickEvents {
                    public static final Event<StartTick> START_CLIENT_TICK =
                            EventFactory.createArrayBacked(StartTick.class, callbacks -> client -> {
                                for (StartTick callback : callbacks) callback.onStartTick(client);
                            });
                    public static final Event<EndTick> END_CLIENT_TICK =
                            EventFactory.createArrayBacked(EndTick.class, callbacks -> client -> {
                                for (EndTick callback : callbacks) callback.onEndTick(client);
                            });
                    public static final Event<StartLevelTick> START_LEVEL_TICK =
                            EventFactory.createArrayBacked(StartLevelTick.class, callbacks -> level -> {
                                for (StartLevelTick callback : callbacks) callback.onStartTick(level);
                            });
                    public static final Event<EndLevelTick> END_LEVEL_TICK =
                            EventFactory.createArrayBacked(EndLevelTick.class, callbacks -> level -> {
                                for (EndLevelTick callback : callbacks) callback.onEndTick(level);
                            });

                    @FunctionalInterface public interface StartTick { void onStartTick(Minecraft client); }
                    @FunctionalInterface public interface EndTick { void onEndTick(Minecraft client); }
                    @FunctionalInterface public interface StartLevelTick { void onStartTick(ClientLevel level); }
                    @FunctionalInterface public interface EndLevelTick { void onEndTick(ClientLevel level); }

                    private ClientTickEvents() {}
                }
                """;
    }

    private static String serverPlayerEventsSource() {
        return """
                package net.fabricmc.fabric.api.entity.event.v1;

                import net.fabricmc.fabric.api.event.Event;
                import net.fabricmc.fabric.api.event.EventFactory;
                import net.minecraft.server.level.ServerPlayer;
                import net.minecraft.world.damagesource.DamageSource;

                public final class ServerPlayerEvents {
                    public static final Event<CopyFrom> COPY_FROM = EventFactory.createArrayBacked(CopyFrom.class, callbacks -> (oldPlayer, newPlayer, alive) -> {
                        for (CopyFrom callback : callbacks) callback.copyFromPlayer(oldPlayer, newPlayer, alive);
                    });
                    public static final Event<AfterRespawn> AFTER_RESPAWN = EventFactory.createArrayBacked(AfterRespawn.class, callbacks -> (oldPlayer, newPlayer, alive) -> {
                        for (AfterRespawn callback : callbacks) callback.afterRespawn(oldPlayer, newPlayer, alive);
                    });
                    public static final Event<Join> JOIN = EventFactory.createArrayBacked(Join.class, callbacks -> player -> {
                        for (Join callback : callbacks) callback.onJoin(player);
                    });
                    public static final Event<Leave> LEAVE = EventFactory.createArrayBacked(Leave.class, callbacks -> player -> {
                        for (Leave callback : callbacks) callback.onLeave(player);
                    });
                    public static final Event<AllowDeath> ALLOW_DEATH = EventFactory.createArrayBacked(AllowDeath.class, callbacks -> (player, source, amount) -> {
                        for (AllowDeath callback : callbacks) if (!callback.allowDeath(player, source, amount)) return false;
                        return true;
                    });

                    @FunctionalInterface public interface CopyFrom { void copyFromPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive); }
                    @FunctionalInterface public interface AfterRespawn { void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive); }
                    @FunctionalInterface public interface Join { void onJoin(ServerPlayer player); }
                    @FunctionalInterface public interface Leave { void onLeave(ServerPlayer player); }
                    @FunctionalInterface public interface AllowDeath { boolean allowDeath(ServerPlayer player, DamageSource source, float amount); }

                    private ServerPlayerEvents() {}
                }
                """;
    }

    private static String payloadTypeRegistrySource() {
        return """
                package net.fabricmc.fabric.api.networking.v1;

                import java.util.function.IntSupplier;
                import net.minecraft.network.FriendlyByteBuf;
                import net.minecraft.network.RegistryFriendlyByteBuf;
                import net.minecraft.network.codec.StreamCodec;
                import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

                public interface PayloadTypeRegistry<B extends FriendlyByteBuf> {
                    PayloadTypeRegistry<RegistryFriendlyByteBuf> SERVERBOUND_PLAY = new SimpleRegistry<>();
                    PayloadTypeRegistry<RegistryFriendlyByteBuf> CLIENTBOUND_PLAY = new SimpleRegistry<>();
                    PayloadTypeRegistry<FriendlyByteBuf> SERVERBOUND_CONFIGURATION = new SimpleRegistry<>();
                    PayloadTypeRegistry<FriendlyByteBuf> CLIENTBOUND_CONFIGURATION = new SimpleRegistry<>();

                    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
                            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec);

                    default <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(
                            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, int maxPacketSize) {
                        return register(type, codec);
                    }

                    default <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(
                            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, IntSupplier maxPacketSize) {
                        return register(type, codec);
                    }

                    static PayloadTypeRegistry<FriendlyByteBuf> serverboundConfiguration() { return SERVERBOUND_CONFIGURATION; }
                    static PayloadTypeRegistry<FriendlyByteBuf> clientboundConfiguration() { return CLIENTBOUND_CONFIGURATION; }
                    static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay() { return SERVERBOUND_PLAY; }
                    static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay() { return CLIENTBOUND_PLAY; }

                    final class SimpleRegistry<B extends FriendlyByteBuf> implements PayloadTypeRegistry<B> {
                        @Override
                        public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
                                CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec) {
                            return null;
                        }
                    }
                }
                """;
    }

    private static String serverPlayNetworkingSource() {
        return """
                package net.fabricmc.fabric.api.networking.v1;

                import java.util.LinkedHashMap;
                import java.util.Map;
                import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
                import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;

                public final class ServerPlayNetworking {
                    private static final Map<Object, PlayPayloadHandler<?>> HANDLERS = new LinkedHashMap<>();

                    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
                            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
                        if (HANDLERS.containsKey(type)) return false;
                        HANDLERS.put(type, handler);
                        return true;
                    }

                    public static void send(ServerPlayer player, CustomPacketPayload payload) {
                        player.connection.send(new ClientboundCustomPayloadPacket(payload));
                    }

                    @SuppressWarnings("unchecked")
                    public static <T extends CustomPacketPayload> void dispatch(T payload, Context context) {
                        PlayPayloadHandler<T> handler = (PlayPayloadHandler<T>) HANDLERS.get(payload.type());
                        if (handler != null) handler.receive(payload, context);
                    }

                    @FunctionalInterface
                    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
                        void receive(T payload, Context context);
                    }

                    public interface Context {
                        MinecraftServer server();
                        ServerPlayer player();
                    }

                    private ServerPlayNetworking() {}
                }
                """;
    }

    private static String clientPlayNetworkingSource() {
        return """
                package net.fabricmc.fabric.api.client.networking.v1;

                import java.util.LinkedHashMap;
                import java.util.Map;
                import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
                import net.minecraft.client.Minecraft;
                import net.minecraft.client.player.LocalPlayer;
                import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
                import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

                public final class ClientPlayNetworking {
                    private static final Map<Object, PlayPayloadHandler<?>> HANDLERS = new LinkedHashMap<>();

                    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
                            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
                        if (HANDLERS.containsKey(type)) return false;
                        HANDLERS.put(type, handler);
                        return true;
                    }

                    public static void send(CustomPacketPayload payload) {
                        Minecraft client = Minecraft.getInstance();
                        if (client.getConnection() == null) throw new IllegalStateException("Cannot send packets when not in game!");
                        client.getConnection().send(new ServerboundCustomPayloadPacket(payload));
                    }

                    @SuppressWarnings("unchecked")
                    public static <T extends CustomPacketPayload> void dispatch(T payload, Context context) {
                        PlayPayloadHandler<T> handler = (PlayPayloadHandler<T>) HANDLERS.get(payload.type());
                        if (handler != null) handler.receive(payload, context);
                    }

                    @FunctionalInterface
                    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
                        void receive(T payload, Context context);
                    }

                    public interface Context {
                        Minecraft client();
                        LocalPlayer player();
                    }

                    private ClientPlayNetworking() {}
                }
                """;
    }

    private static String recipeSynchronizationSource() {
        return """
                package net.fabricmc.fabric.api.recipe.v1.sync;

                import net.minecraft.world.item.crafting.RecipeSerializer;

                public final class RecipeSynchronization {
                    public static void synchronizeRecipeSerializer(RecipeSerializer<?> serializer) {
                        // The browser runtime currently uses the vanilla/integrated-server recipe channel.
                    }

                    private RecipeSynchronization() {}
                }
                """;
    }

    private static String defaultItemComponentsSource() {
        return """
                package net.fabricmc.fabric.api.item.v1;

                import java.util.Collection;
                import java.util.function.BiConsumer;
                import java.util.function.Consumer;
                import java.util.function.Predicate;
                import net.fabricmc.fabric.api.event.Event;
                import net.fabricmc.fabric.api.event.EventFactory;
                import net.minecraft.core.HolderLookup;
                import net.minecraft.core.component.DataComponentMap;
                import net.minecraft.world.item.Item;

                public final class DefaultItemComponentEvents {
                    public static final Event<ModifyCallback> MODIFY =
                            EventFactory.createArrayBacked(ModifyCallback.class, listeners -> context -> {
                                for (ModifyCallback listener : listeners) listener.modify(context);
                            });

                    public interface ModifyContext {
                        void modify(Predicate<Item> itemPredicate, ModifyConsumer builderConsumer);
                        default void modify(Item item, ModifyConsumer consumer) { modify(Predicate.isEqual(item), consumer); }
                        default void modify(Collection<Item> items, ModifyConsumer consumer) { modify(items::contains, consumer); }
                        default void modify(Predicate<Item> predicate, BiConsumer<DataComponentMap.Builder, Item> consumer) {
                            modify(predicate, (builder, lookup, item) -> consumer.accept(builder, item));
                        }
                        default void modify(Item item, Consumer<DataComponentMap.Builder> consumer) {
                            modify(Predicate.isEqual(item), (builder, ignored) -> consumer.accept(builder));
                        }
                        default void modify(Collection<Item> items, BiConsumer<DataComponentMap.Builder, Item> consumer) {
                            modify(items::contains, consumer);
                        }
                    }

                    @FunctionalInterface public interface ModifyCallback { void modify(ModifyContext context); }
                    @FunctionalInterface public interface ModifyConsumer {
                        void modify(DataComponentMap.Builder builder, HolderLookup.Provider lookupProvider, Item item);
                    }

                    private DefaultItemComponentEvents() {}
                }
                """;
    }

    private static String itemTooltipSource() {
        return """
                package net.fabricmc.fabric.api.client.item.v1;

                import java.util.List;
                import net.fabricmc.fabric.api.event.Event;
                import net.fabricmc.fabric.api.event.EventFactory;
                import net.minecraft.network.chat.Component;
                import net.minecraft.world.item.Item;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.world.item.TooltipFlag;

                @FunctionalInterface
                public interface ItemTooltipCallback {
                    Event<ItemTooltipCallback> EVENT = EventFactory.createArrayBacked(
                            ItemTooltipCallback.class, callbacks -> (stack, context, flag, lines) -> {
                                for (ItemTooltipCallback callback : callbacks) callback.getTooltip(stack, context, flag, lines);
                            });

                    void getTooltip(ItemStack stack, Item.TooltipContext tooltipContext, TooltipFlag tooltipFlag, List<Component> lines);
                }
                """;
    }

    private static String clientTooltipSource() {
        return """
                package net.fabricmc.fabric.api.client.rendering.v1;

                import net.fabricmc.fabric.api.event.Event;
                import net.fabricmc.fabric.api.event.EventFactory;
                import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
                import net.minecraft.world.inventory.tooltip.TooltipComponent;

                @FunctionalInterface
                public interface ClientTooltipComponentCallback {
                    Event<ClientTooltipComponentCallback> EVENT = EventFactory.createArrayBacked(
                            ClientTooltipComponentCallback.class, callbacks -> data -> {
                                for (ClientTooltipComponentCallback callback : callbacks) {
                                    ClientTooltipComponent component = callback.getClientComponent(data);
                                    if (component != null) return component;
                                }
                                return null;
                            });

                    ClientTooltipComponent getClientComponent(TooltipComponent component);
                }
                """;
    }

    private static String clientPreAttackSource() {
        return """
                package net.fabricmc.fabric.api.event.client.player;

                import net.fabricmc.fabric.api.event.Event;
                import net.fabricmc.fabric.api.event.EventFactory;
                import net.minecraft.client.Minecraft;
                import net.minecraft.client.player.LocalPlayer;

                @FunctionalInterface
                public interface ClientPreAttackCallback {
                    Event<ClientPreAttackCallback> EVENT = EventFactory.createArrayBacked(
                            ClientPreAttackCallback.class, callbacks -> (client, player, clickCount) -> {
                                for (ClientPreAttackCallback callback : callbacks) {
                                    if (callback.onClientPlayerPreAttack(client, player, clickCount)) return true;
                                }
                                return false;
                            });

                    boolean onClientPlayerPreAttack(Minecraft client, LocalPlayer player, int clickCount);
                }
                """;
    }

    private FabricCompatInstaller() {}
}
