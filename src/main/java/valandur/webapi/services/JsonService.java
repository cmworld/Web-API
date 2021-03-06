package valandur.webapi.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import org.slf4j.Logger;
import org.spongepowered.api.CatalogType;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.data.manipulator.mutable.DyeableData;
import org.spongepowered.api.data.manipulator.mutable.PotionEffectData;
import org.spongepowered.api.data.manipulator.mutable.block.ConnectedDirectionData;
import org.spongepowered.api.data.manipulator.mutable.block.PoweredData;
import org.spongepowered.api.data.manipulator.mutable.block.RedstonePoweredData;
import org.spongepowered.api.data.manipulator.mutable.entity.*;
import org.spongepowered.api.data.manipulator.mutable.item.DurabilityData;
import org.spongepowered.api.data.manipulator.mutable.item.SpawnableData;
import org.spongepowered.api.data.manipulator.mutable.tileentity.SignData;
import org.spongepowered.api.effect.potion.PotionEffect;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.merchant.TradeOffer;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.statistic.achievement.Achievement;
import org.spongepowered.api.util.ban.Ban;
import org.spongepowered.api.world.*;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.api.world.extent.BlockVolume;
import valandur.webapi.WebAPI;
import valandur.webapi.api.cache.chat.CachedChatMessage;
import valandur.webapi.api.cache.command.CachedCommand;
import valandur.webapi.api.cache.command.CachedCommandCall;
import valandur.webapi.api.cache.command.CachedCommandResult;
import valandur.webapi.api.cache.entity.CachedEntity;
import valandur.webapi.api.cache.misc.CachedCatalogType;
import valandur.webapi.api.cache.misc.CachedInventory;
import valandur.webapi.api.cache.misc.CachedLocation;
import valandur.webapi.api.cache.player.CachedPlayer;
import valandur.webapi.api.cache.plugin.CachedPluginContainer;
import valandur.webapi.api.cache.tileentity.CachedTileEntity;
import valandur.webapi.api.cache.world.CachedGeneratorType;
import valandur.webapi.api.cache.world.CachedWorld;
import valandur.webapi.api.cache.world.CachedWorldBorder;
import valandur.webapi.api.json.WebAPISerializer;
import valandur.webapi.api.message.MessageResponse;
import valandur.webapi.api.service.IJsonService;
import valandur.webapi.api.util.TreeNode;
import valandur.webapi.block.BlockUpdate;
import valandur.webapi.command.CommandSource;
import valandur.webapi.json.AnnotationIntrospector;
import valandur.webapi.json.serializer.block.BlockSnapshotSerializer;
import valandur.webapi.json.serializer.block.BlockStateSerializer;
import valandur.webapi.json.serializer.block.BlockUpdateSerializer;
import valandur.webapi.json.serializer.block.BlockVolumeSerializer;
import valandur.webapi.json.serializer.chat.CachedChatMessageSerializer;
import valandur.webapi.json.serializer.command.CachedCommandCallSerializer;
import valandur.webapi.json.serializer.command.CachedCommandResultSerializer;
import valandur.webapi.json.serializer.command.CachedCommandSerializer;
import valandur.webapi.json.serializer.entity.*;
import valandur.webapi.json.serializer.event.CauseSerializer;
import valandur.webapi.json.serializer.event.EventSerializer;
import valandur.webapi.json.serializer.item.*;
import valandur.webapi.json.serializer.message.MessageResponseSerializer;
import valandur.webapi.json.serializer.misc.*;
import valandur.webapi.json.serializer.player.*;
import valandur.webapi.json.serializer.plugin.CachedPluginContainerSerializer;
import valandur.webapi.json.serializer.plugin.PluginContainerSerializer;
import valandur.webapi.json.serializer.tileentity.*;
import valandur.webapi.json.serializer.user.UserPermissionSerializer;
import valandur.webapi.json.serializer.world.*;
import valandur.webapi.user.UserPermission;
import valandur.webapi.util.Util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class JsonService implements IJsonService {

    private Map<Class, Class> registeredSerializers = new HashMap<>();
    private Map<Class, WebAPISerializer> serializers;
    private Map<String, Class> supportedData;


    public void init() {
        Logger logger = WebAPI.getLogger();

        logger.info("Loading serializers...");

        serializers = new HashMap<>();

        // Block
        serializers.put(BlockSnapshot.class, new BlockSnapshotSerializer());
        serializers.put(BlockState.class, new BlockStateSerializer());
        serializers.put(BlockUpdate.class, new BlockUpdateSerializer());
        serializers.put(BlockVolume.class, new BlockVolumeSerializer());

        // Chat
        serializers.put(CachedChatMessage.class, new CachedChatMessageSerializer());

        // Command
        serializers.put(CachedCommand.class, new CachedCommandSerializer());
        serializers.put(CachedCommandCall.class, new CachedCommandCallSerializer());
        serializers.put(CachedCommandResult.class, new CachedCommandResultSerializer());

        // Entity
        serializers.put(AgeableData.class, new AgeableDataSerializer());
        serializers.put(CachedEntity.class, new CachedEntitySerializer());
        serializers.put(CareerData.class, new CareerDataSerializer());
        serializers.put(DyeableData.class, new DyeableDataSerializer());
        serializers.put(Entity.class, new EntitySerializer());
        serializers.put(FoodData.class, new FoodDataSerializer());
        serializers.put(HealthData.class, new HealthDataSerializer());
        serializers.put(ShearedData.class, new ShearedDataSerializer());
        serializers.put(TameableData.class, new TameableDataSerializer());
        serializers.put(TradeOfferData.class, new TradeOfferDataSerializer());
        serializers.put(TradeOffer.class, new TradeOfferSerializer());

        // Event
        serializers.put(Cause.class, new CauseSerializer());
        serializers.put(Event.class, new EventSerializer());

        // Item
        serializers.put(CachedInventory.class, new CachedInventorySerializer());
        serializers.put(DurabilityData.class, new DurabilityDataSerializer());
        serializers.put(ItemStack.class, new ItemStackSerializer());
        serializers.put(ItemStackSnapshot.class, new ItemStackSnapshotSerializer());
        serializers.put(PotionEffectData.class, new PotionEffectDataSerializer());
        serializers.put(PotionEffect.class, new PotionEffectSerializer());
        serializers.put(SpawnableData.class, new SpawnableDataSerializer());

        // Message
        serializers.put(MessageResponse.class, new MessageResponseSerializer());

        // Misc.
        serializers.put(CachedCatalogType.class, new CachedCatalogTypeSerializer());
        serializers.put(CachedLocation.class, new CachedLocationSerializer());
        serializers.put(CatalogType.class, new CatalogTypeSerializer());
        serializers.put(CommandSource.class, new CommandSourceSerializer());
        serializers.put(Exception.class, new ExceptionSerializer());
        serializers.put(Location.class, new LocationSerializer());
        serializers.put(TreeNode.class, new TreeNodeSerializer());
        serializers.put(UUID.class, new UUIDSerializer());
        serializers.put(Vector3d.class, new Vector3dSerializer());
        serializers.put(Vector3i.class, new Vector3iSerializer());

        // Player
        serializers.put(AchievementData.class, new AchievementDataSerializer());
        serializers.put(Achievement.class, new AchievementSerializer());
        serializers.put(Ban.Profile.class, new BanSerializer());
        serializers.put(CachedPlayer.class, new CachedPlayerSerializer());
        serializers.put(ExperienceHolderData.class, new ExperienceHolderDataSerializer());
        serializers.put(GameModeData.class, new GameModeDataSerializer());
        serializers.put(GameProfile.class, new GameProfileSerializer());
        serializers.put(JoinData.class, new JoinDataSerializer());
        serializers.put(Player.class, new PlayerSerializer());
        serializers.put(StatisticData.class, new StatisticDataSerializer());

        // Plugin
        serializers.put(CachedPluginContainer.class, new CachedPluginContainerSerializer());
        serializers.put(PluginContainer.class, new PluginContainerSerializer());

        // Tile-Entity
        serializers.put(CachedTileEntity.class, new CachedTileEntitySerializer());
        serializers.put(ConnectedDirectionData.class, new ConnectedDirectionDataSerializer());
        serializers.put(PoweredData.class, new PoweredDataSerializer());
        serializers.put(RedstonePoweredData.class, new RedstonePoweredDataSerializer());
        serializers.put(SignData.class, new SignDataSerializer());
        serializers.put(TileEntity.class, new TileEntitySerializer());

        // User
        serializers.put(UserPermission.class, new UserPermissionSerializer());

        // World
        serializers.put(CachedGeneratorType.class, new CachedGeneratorTypeSerializer());
        serializers.put(CachedWorldBorder.class, new CachedWorldBorderSerializer());
        serializers.put(CachedWorld.class, new CachedWorldSerializer());
        serializers.put(Chunk.class, new ChunkSerializer());
        serializers.put(Dimension.class, new DimensionSerializer());
        serializers.put(Explosion.class, new ExplosionSerializer());
        serializers.put(GeneratorType.class, new GeneratorTypeSerializer());
        serializers.put(WorldBorder.class, new WorldBorderSerializer());
        serializers.put(World.class, new WorldSerializer());


        // Data
        supportedData = new HashMap<>();
        supportedData.put("achievements", AchievementData.class);
        supportedData.put("career", CareerData.class);
        supportedData.put("connectedDirection", ConnectedDirectionData.class);
        supportedData.put("durability", DurabilityData.class);
        supportedData.put("dye", DyeableData.class);
        supportedData.put("experience", ExperienceHolderData.class);
        supportedData.put("food", FoodData.class);
        supportedData.put("gameMode", GameModeData.class);
        supportedData.put("health", HealthData.class);
        supportedData.put("joined", JoinData.class);
        supportedData.put("potionEffects", PotionEffectData.class);
        supportedData.put("powered", PoweredData.class);
        supportedData.put("redstonePower", RedstonePoweredData.class);
        supportedData.put("sheared", ShearedData.class);
        supportedData.put("sign", SignData.class);
        supportedData.put("spawn", SpawnableData.class);
        supportedData.put("statistics", StatisticData.class);
        supportedData.put("tameable", TameableData.class);
        supportedData.put("trades", TradeOfferData.class);

        // Load registered serializers
        logger.info("Loading registered serializers...");
        for (Map.Entry<Class, Class> entry : registeredSerializers.entrySet()) {
            try {
                WebAPISerializer ser = (WebAPISerializer)entry.getValue().newInstance();
                serializers.put(entry.getKey(), ser);
            } catch (InstantiationException | IllegalAccessException e) {
                logger.warn("  Could not register serializer " + entry.getValue().getName() + ": " + e.getMessage());
            }
        }

        // Load extra serializers
        logger.info("Loading custom serializers...");

        WebAPI.getExtensionService().loadPlugins("serializers", WebAPISerializer.class, serializerClass -> {
            try {
                WebAPISerializer serializer = serializerClass.newInstance();

                // Check if we already have a serializer for that class
                WebAPISerializer prev = serializers.remove(serializer.getHandledClass());
                if (prev != null) {
                    logger.info("    Replacing existing serializer for '" + serializer.getHandledClass().getName() + "'");
                }

                serializers.put(serializer.getHandledClass(), serializer);
            } catch (IllegalAccessException | InstantiationException e) {
                logger.warn("   Could not instantiate serializer '" + serializerClass.getName() + "': " + e.getMessage());
            }
        });

        logger.info("Done loading serializers");
    }

    @Override
    public <T> void registerSerializer(Class<T> clazz, Class<WebAPISerializer<T>> serializer) {
        registeredSerializers.put(clazz, serializer);
    }

    public Map<String, Class> getSupportedData() {
        return supportedData;
    }

    public String toString(Object obj, boolean details, TreeNode<String, Boolean> perms) {
        ObjectMapper mapper = getDefaultObjectMapper(details, perms);

        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
    public JsonNode toJson(Object obj, boolean details, TreeNode<String, Boolean> perms) {
        ObjectMapper mapper = getDefaultObjectMapper(details, perms);
        return mapper.valueToTree(obj);
    }
    public JsonNode classToJson(Class c) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();

        json.put("name", c.getName());
        json.put("parent", c.getSuperclass() != null ? c.getSuperclass().getName() : null);

        ObjectNode jsonFields = JsonNodeFactory.instance.objectNode();
        Field[] fs = Util.getAllFields(c);
        for (Field f : fs) {
            ObjectNode jsonField = JsonNodeFactory.instance.objectNode();

            f.setAccessible(true);

            jsonField.put("type", f.getType().getName());

            ArrayNode arr = JsonNodeFactory.instance.arrayNode();
            int mod = f.getModifiers();
            if (Modifier.isAbstract(mod)) arr.add("abstract");
            if (Modifier.isFinal(mod)) arr.add("final");
            if (Modifier.isInterface(mod)) arr.add("interface");
            if (Modifier.isNative(mod)) arr.add("native");
            if (Modifier.isPrivate(mod)) arr.add("private");
            if (Modifier.isProtected(mod)) arr.add("protected");
            if (Modifier.isPublic(mod)) arr.add("public");
            if (Modifier.isStatic(mod)) arr.add("static");
            if (Modifier.isStrict(mod)) arr.add("strict");
            if (Modifier.isSynchronized(mod)) arr.add("synchronized");
            if (Modifier.isTransient(mod)) arr.add("transient");
            if (Modifier.isVolatile(mod)) arr.add("volatile");
            jsonField.set("modifiers", arr);

            if (f.getDeclaringClass() != c) {
                jsonField.put("from", f.getDeclaringClass().getName());
            }

            jsonFields.set(f.getName(), jsonField);
        }
        json.set("fields", jsonFields);

        ObjectNode jsonMethods = JsonNodeFactory.instance.objectNode();
        Method[] ms = Util.getAllMethods(c);
        for (Method m : ms) {
            ObjectNode jsonMethod = JsonNodeFactory.instance.objectNode();

            ArrayNode arr = JsonNodeFactory.instance.arrayNode();
            int mod = m.getModifiers();
            if (Modifier.isAbstract(mod)) arr.add("abstract");
            if (Modifier.isFinal(mod)) arr.add("final");
            if (Modifier.isInterface(mod)) arr.add("interface");
            if (Modifier.isNative(mod)) arr.add("native");
            if (Modifier.isPrivate(mod)) arr.add("private");
            if (Modifier.isProtected(mod)) arr.add("protected");
            if (Modifier.isPublic(mod)) arr.add("public");
            if (Modifier.isStatic(mod)) arr.add("static");
            if (Modifier.isStrict(mod)) arr.add("strict");
            if (Modifier.isSynchronized(mod)) arr.add("synchronized");
            if (Modifier.isTransient(mod)) arr.add("transient");
            if (Modifier.isVolatile(mod)) arr.add("volatile");
            jsonMethod.set("modifiers", arr);

            ArrayNode arr2 = JsonNodeFactory.instance.arrayNode();
            for (Parameter p : m.getParameters()) {
                arr2.add(p.getType().getName());
            }
            jsonMethod.set("params", arr2);

            jsonMethod.put("return", m.getReturnType().getName());

            if (m.getDeclaringClass() != c) {
                jsonMethod.put("from", m.getDeclaringClass().getName());
            }

            jsonMethods.set(m.getName(), jsonMethod);
        }
        json.set("methods", jsonMethods);

        return json;
    }

    private ObjectMapper getDefaultObjectMapper(boolean details, TreeNode<String, Boolean> perms) {
        if (perms == null) {
            throw new NullPointerException("Permissions may not be null");
        }

        ObjectMapper om = new ObjectMapper();
        om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        om.disable(MapperFeature.AUTO_DETECT_CREATORS, MapperFeature.AUTO_DETECT_FIELDS, MapperFeature.AUTO_DETECT_GETTERS, MapperFeature.AUTO_DETECT_IS_GETTERS);

        SimpleModule mod = new SimpleModule();
        for (Map.Entry<Class, WebAPISerializer> entry : serializers.entrySet()) {
            mod.addSerializer(entry.getKey(), entry.getValue());
        }
        om.registerModule(mod);

        om.setAnnotationIntrospector(new AnnotationIntrospector(!details));
        om.setConfig(om.getSerializationConfig()
                .withAttribute("includes", perms)
                .withAttribute("parents", new ArrayList<String>())
                .withAttribute("details", new AtomicBoolean(details))
        );

        return om;
    }
}
