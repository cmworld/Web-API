package valandur.webapi.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
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
import org.spongepowered.api.data.manipulator.mutable.entity.*;
import org.spongepowered.api.data.manipulator.mutable.item.DurabilityData;
import org.spongepowered.api.data.manipulator.mutable.item.SpawnableData;
import org.spongepowered.api.data.manipulator.mutable.tileentity.SignData;
import org.spongepowered.api.effect.potion.PotionEffect;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.item.inventory.Inventory;
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
import valandur.webapi.blocks.BlockUpdate;
import valandur.webapi.cache.chat.CachedChatMessage;
import valandur.webapi.cache.command.CachedCommandCall;
import valandur.webapi.cache.command.CachedCommandResult;
import valandur.webapi.cache.entity.CachedEntity;
import valandur.webapi.cache.misc.CachedCatalogType;
import valandur.webapi.cache.misc.CachedLocation;
import valandur.webapi.cache.plugin.CachedPluginContainer;
import valandur.webapi.cache.tileentity.CachedTileEntity;
import valandur.webapi.cache.world.CachedDimension;
import valandur.webapi.cache.world.CachedGeneratorType;
import valandur.webapi.cache.world.CachedWorld;
import valandur.webapi.cache.world.CachedWorldBorder;
import valandur.webapi.json.serializers.WebAPISerializer;
import valandur.webapi.json.serializers.block.*;
import valandur.webapi.json.serializers.chat.CachedChatMessageSerializer;
import valandur.webapi.json.serializers.command.CachedCommandCallSerializer;
import valandur.webapi.json.serializers.command.CachedCommandResultSerializer;
import valandur.webapi.json.serializers.plugin.CachedPluginContainerSerializer;
import valandur.webapi.json.serializers.tileentity.CachedTileEntitySerializer;
import valandur.webapi.json.serializers.tileentity.ConnectedDirectionDataSerializer;
import valandur.webapi.json.serializers.world.CachedWorldSerializer;
import valandur.webapi.json.serializers.entity.*;
import valandur.webapi.json.serializers.entity.TradeOfferSerializer;
import valandur.webapi.json.serializers.event.CauseSerializer;
import valandur.webapi.json.serializers.event.EventSerializer;
import valandur.webapi.json.serializers.misc.*;
import valandur.webapi.json.serializers.item.*;
import valandur.webapi.json.serializers.player.*;
import valandur.webapi.json.serializers.plugin.PluginContainerSerializer;
import valandur.webapi.json.serializers.tileentity.SignDataSerializer;
import valandur.webapi.json.serializers.tileentity.TileEntitySerializer;
import valandur.webapi.json.serializers.world.*;
import valandur.webapi.misc.TreeNode;
import valandur.webapi.misc.Util;
import valandur.webapi.misc.WebAPIDiagnosticListener;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class JsonConverter {

    private static Map<Class, WebAPISerializer> serializers;
    private static Map<String, String> relocatedPackages;
    private static Map<String, Class> supportedData;
    public static Map<String, Class> getSupportedData() {
        return supportedData;
    }

    public static void initSerializers() {
        serializers = new HashMap<>();

        // Block
        serializers.put(BlockSnapshot.class, new BlockSnapshotSerializer());
        serializers.put(BlockState.class, new BlockStateSerializer());
        serializers.put(BlockUpdate.class, new BlockUpdateSerializer());
        serializers.put(BlockVolume.class, new BlockVolumeSerializer());

        // Chat
        serializers.put(CachedChatMessage.class, new CachedChatMessageSerializer());

        // Command
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
        serializers.put(DurabilityData.class, new DurabilityDataSerializer());
        serializers.put(Inventory.class, new InventorySerializer());
        serializers.put(ItemStack.class, new ItemStackSerializer());
        serializers.put(ItemStackSnapshot.class, new ItemStackSnapshotSerializer());
        serializers.put(PotionEffectData.class, new PotionEffectDataSerializer());
        serializers.put(PotionEffect.class, new PotionEffectSerializer());
        serializers.put(SpawnableData.class, new SpawnableDataSerializer());

        // Misc.
        serializers.put(CachedCatalogType.class, new CachedCatalogTypeSerializer());
        serializers.put(CachedLocation.class, new CachedLocationSerializer());
        serializers.put(CatalogType.class, new CatalogTypeSerializer());
        serializers.put(Location.class, new LocationSerializer());
        serializers.put(UUID.class, new UUIDSerializer());
        serializers.put(Vector3d.class, new Vector3dSerializer());
        serializers.put(Vector3i.class, new Vector3iSerializer());

        // Player
        serializers.put(AchievementData.class, new AchievementDataSerializer());
        serializers.put(Achievement.class, new AchievementSerializer());
        serializers.put(Ban.Profile.class, new BanSerializer());
        serializers.put(CachedPlayerSerializer.class, new CachedPlayerSerializer());
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
        serializers.put(SignData.class, new SignDataSerializer());
        serializers.put(TileEntity.class, new TileEntitySerializer());

        // World
        serializers.put(CachedDimension.class, new CachedDimensionSerializer());
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
        supportedData.put("sheared", ShearedData.class);
        supportedData.put("sign", SignData.class);
        supportedData.put("spawn", SpawnableData.class);
        supportedData.put("statistics", StatisticData.class);
        supportedData.put("tameable", TameableData.class);
        supportedData.put("trades", TradeOfferData.class);



        // Relocated packages
        relocatedPackages = new HashMap<>();
        relocatedPackages.put("import org.eclipse.jetty",     "import valandur.webapi.shadow.org.eclipse.jetty");
        relocatedPackages.put("import com.fasterxml.jackson", "import valandur.webapi.shadow.fasterxml.jackson");
        relocatedPackages.put("import javax.servlet",         "import valandur.webapi.shadow.javax.servlet");
        relocatedPackages.put("import org.reflections",       "import valandur.webapi.shadow.org.reflections");
        relocatedPackages.put("import net.jodah",             "import valandur.webapi.shadow.net.jodah");
    }
    public static void loadExtraSerializers() {
        Logger logger = WebAPI.getInstance().getLogger();
        logger.info("Loading additional serializers...");

        // Get root directory
        File root = new File("webapi");
        File folder = new File("webapi/serializers");
        if (!folder.exists() && !folder.mkdirs()) {
            logger.warn("Could not create folder for additional serializers");
            return;
        }

        List<File> files = null;
        try {
            files = Files.walk(Paths.get(root.toURI()))
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        logger.info("Found " + files.size() + " serializer files in " + root.getAbsolutePath());
        if (files.size() == 0) {
            return;
        }

        // Setup java compiler
        ClassLoader currentCl = Thread.currentThread().getContextClassLoader();
        URL[] urls = ((URLClassLoader) currentCl).getURLs();
        String classpath = Arrays.stream(urls).map(URL::getPath).filter(Objects::nonNull).collect(Collectors.joining(File.pathSeparator));

        WebAPIDiagnosticListener diag = new WebAPIDiagnosticListener();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            logger.warn("You need to install & use a JDK to support custom serializers. ");
            return;
        }

        StandardJavaFileManager fm = compiler.getStandardFileManager(diag, null, null);
        List<String> optionList = Arrays.asList("-classpath", classpath);

        // Compile, load and instantiate compiled class.
        for (File file : files) {
            String logFile = file.getAbsolutePath().replace(".java", ".log");
            diag.startLog(logFile);

            try {
                logger.info("  - " + file.getName());

                // Read file to check for some basic things like package and shadowed references
                String fileContent = new String(Files.readAllBytes(file.toPath()));

                if (!fileContent.contains("package serializers;")) {
                    logger.error("   The class must be in the 'serializers' package.");
                    continue;
                }

                int start = fileContent.indexOf("class ") + 6;
                int end = fileContent.indexOf(" ", start);
                String cName = fileContent.substring(start, end);
                if (!cName.equalsIgnoreCase(file.getName().substring(0, file.getName().length() - 5))) {
                    logger.error("   File name '" + file.getName().substring(0, file.getName().length() - 5) + "' must match class name '" + cName + "'");
                    continue;
                }

                if (!fileContent.contains("extends WebAPISerializer<")) {
                    logger.error("   Class must extend WebAPISerializer, and must provide the event class as a " +
                            "generic parameter, e.g: WebAPISerializer<InteractBlockEvent>");
                    continue;
                }

                // Replace shadowed references
                for (Map.Entry<String, String> entry : relocatedPackages.entrySet()) {
                    if (WebAPI.getInstance().isDevMode())
                        fileContent = fileContent.replace(entry.getValue(), entry.getKey());
                    else
                        fileContent = fileContent.replace(entry.getKey(), entry.getValue());
                }

                // Write back to file
                Files.write(file.toPath(), fileContent.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // Compile the file
                Iterable<? extends JavaFileObject> compilationUnits = fm.getJavaFileObjectsFromFiles(Collections.singletonList(file));
                JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diag, optionList, null, compilationUnits);

                boolean res = task.call();

                if (!res) {
                    logger.error("   Compilation failed. See the log file at " + logFile + " for details");
                    continue;
                }

                String className = file.getName().substring(0, file.getName().length() - 5);

                // Load the class
                URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{root.toURI().toURL()}, currentCl);
                Class<?> cls = Class.forName("serializers." + className, true, classLoader);
                if (!WebAPISerializer.class.isAssignableFrom(cls)) {
                    logger.error("   Must extend " + WebAPISerializer.class.getName());
                    continue;
                }

                // Instantiate
                WebAPISerializer instance = (WebAPISerializer)cls.newInstance();

                // Get handled class
                Class forClass = instance.getHandledClass();
                try {
                    Field f = cls.getField("forClass");
                    forClass = (Class) f.get(null);
                } catch (NoSuchFieldException ignored) {}

                // Check if we already have a serializer for that class
                WebAPISerializer prev = serializers.remove(forClass);
                if (prev != null) {
                    logger.info("    Replacing existing serializer...");
                }

                // Add to serializers
                serializers.put(forClass, instance);
                logger.info("    -> " + forClass.getName());
            } catch (IOException | IllegalAccessException | InstantiationException | ClassNotFoundException e) {
                logger.error("   Error. See the log file at " + logFile + " for details");
                diag.writeException(e);
            }

            diag.stopLog();
        }

        diag.stopLog();

        logger.info("Done loading additional serializers");
    }

    /**
     * Converts an object directly to a json string. Includes details if specified.
     * @param obj The object to convert to json.
     * @param details False if only marked properties/methods should be included, true otherwise.
     * @return The json string representation of the object.
     */
    public static String toString(Object obj, boolean details, TreeNode<String, Boolean> perms) {
        ObjectMapper mapper = getDefaultObjectMapper(details, perms);

        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Converts an object to json using the default object mapper. Includes details if specified.
     * @param obj The object to convert to json
     * @param details False if only marked properties/methods should be included, true otherwise.
     * @param perms The permissions defining which properties of the object are returned.
     * @return The json representation of the object.
     */
    public static JsonNode toJson(Object obj, boolean details, TreeNode<String, Boolean> perms) {
        ObjectMapper mapper = getDefaultObjectMapper(details, perms);
        return mapper.valueToTree(obj);
    }

    /**
     * Get the default object mapper which contains some custom serializers and doesn't fail on empty beans.
     * @param details True if the object mapper should serialize details of cached objects, false otherwise
     * @param perms The permissions tree that defines what properties get serialized.
     * @return The object mapper used to turn objects into json.
     */
    private static ObjectMapper getDefaultObjectMapper(boolean details, TreeNode<String, Boolean> perms) {
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

        om.setAnnotationIntrospector(new WebAPIAnnotationIntrospector(!details));
        om.setConfig(om.getSerializationConfig()
                .withAttribute("includes", perms)
                .withAttribute("parents", new ArrayList<String>())
                .withAttribute("details", new AtomicBoolean(details))
        );

        return om;
    }

    /**
     * Converts a class structure to json. This includes all the fields and methods of the class
     * @param c The class for which to get the json representation.
     * @return A JsonNode representing the class.
     */
    public static JsonNode classToJson(Class c) {
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
}
