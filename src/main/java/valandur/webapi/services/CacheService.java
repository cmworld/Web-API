package valandur.webapi.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.command.CommandMapping;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.command.SendCommandEvent;
import org.spongepowered.api.event.message.MessageEvent;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;
import valandur.webapi.WebAPI;
import valandur.webapi.api.cache.CachedObject;
import valandur.webapi.api.cache.chat.CachedChatMessage;
import valandur.webapi.api.cache.command.CachedCommand;
import valandur.webapi.api.cache.command.CachedCommandCall;
import valandur.webapi.api.cache.entity.CachedEntity;
import valandur.webapi.api.cache.player.CachedPlayer;
import valandur.webapi.api.cache.plugin.CachedPluginContainer;
import valandur.webapi.api.cache.tileentity.CachedTileEntity;
import valandur.webapi.api.cache.world.CachedWorld;
import valandur.webapi.api.permission.Permissions;
import valandur.webapi.api.service.ICacheService;
import valandur.webapi.command.CommandSource;
import valandur.webapi.util.Util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CacheService implements ICacheService {

    private String configFileName = "cache.conf";

    private JsonService json;

    private Map<String, Long> cacheDurations = new HashMap<>();
    private int numChatMessages;
    private int numCommandCalls;

    private ConcurrentLinkedQueue<CachedChatMessage> chatMessages = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<CachedCommandCall> commandCalls = new ConcurrentLinkedQueue<>();
    private Collection<CachedPluginContainer> plugins = new LinkedHashSet<>();
    private Collection<CachedCommand> commands = new LinkedHashSet<>();
    private Map<UUID, CachedWorld> worlds = new ConcurrentHashMap<>();
    private Map<UUID, CachedPlayer> players = new ConcurrentHashMap<>();
    private Map<UUID, CachedEntity> entities = new ConcurrentHashMap<>();
    private Map<Class, JsonNode> classes = new ConcurrentHashMap<>();

    public ConcurrentLinkedQueue<CachedChatMessage> getChatMessages() {
        return chatMessages;
    }
    public ConcurrentLinkedQueue<CachedCommandCall> getCommandCalls() { return commandCalls; }


    public void init() {
        this.json = WebAPI.getJsonService();

        Tuple<ConfigurationLoader, ConfigurationNode> tup = Util.loadWithDefaults(configFileName, "defaults/" + configFileName);
        ConfigurationNode config = tup.getSecond();

        ConfigurationNode amountNode = config.getNode("amount");
        numChatMessages = amountNode.getNode("chat").getInt();
        numCommandCalls = amountNode.getNode("command").getInt();

        cacheDurations.clear();
        ConfigurationNode durationNode = config.getNode("duration");
        for (ConfigurationNode node : durationNode.getChildrenMap().values()) {
            cacheDurations.put(node.getKey().toString(), node.getLong());
        }
    }

    @Override
    public Long getCacheDurationFor(Class clazz) {
        Long dur = cacheDurations.get(clazz.getSimpleName());
        return dur != null ? dur : Long.MAX_VALUE;
    }

    public Map<Class, JsonNode> getClasses() {
        return classes;
    }
    public JsonNode getClass(Class type) {
        if (classes.containsKey(type))
            return classes.get(type);

        JsonNode e = json.classToJson(type);
        classes.put(type, e);
        return e;
    }

    public CachedChatMessage addChatMessage(Player sender, MessageEvent event) {
        CachedChatMessage cache = new CachedChatMessage(sender, event);
        chatMessages.add(cache);

        while (chatMessages.size() > numChatMessages) {
            chatMessages.poll();
        }

        return cache;
    }
    public CachedCommandCall addCommandCall(SendCommandEvent event) {
        CachedCommandCall cache = new CachedCommandCall(event);
        commandCalls.add(cache);

        while (commandCalls.size() > numCommandCalls) {
            commandCalls.poll();
        }

        return cache;
    }

    public Optional<Object> executeMethod(CachedObject cache, String methodName, Class[] paramTypes, Object[] paramValues) {
        return WebAPI.runOnMain(() -> {
            Optional<?> obj = cache.getLive();

            if (!obj.isPresent())
                return null;

            Object o = obj.get();
            Method[] ms = Arrays.stream(Util.getAllMethods(o.getClass())).filter(m -> {
                if (!m.getName().equalsIgnoreCase(methodName))
                    return false;

                Class<?>[] reqTypes = m.getParameterTypes();
                if (reqTypes.length != paramTypes.length)
                    return false;

                for (int i = 0; i < reqTypes.length; i++) {
                    if (!reqTypes[i].isAssignableFrom(paramTypes[i])) {
                        return false;
                    }
                }

                return true;
            }).toArray(Method[]::new);

            if (ms.length == 0) {
                return new Exception("Method not found");
            }

            try {
                Method m = ms[0];
                m.setAccessible(true);
                Object res = m.invoke(o, paramValues);
                if (m.getReturnType() == Void.class || m.getReturnType() == void.class)
                    return true;
                return res;
            } catch (Exception e) {
                return e;
            }
        });
    }
    public Tuple<Map<String, JsonNode>, Map<String, JsonNode>> getExtraData(CachedObject cache, String[] reqFields, String[] reqMethods) {
        return WebAPI.runOnMain(() -> {
            Map<String, JsonNode> fields = new HashMap<>();
            Map<String, JsonNode> methods = new HashMap<>();

            Optional<?> opt = cache.getLive();
            if (!opt.isPresent()) return null;

            Object obj = opt.get();
            Class c = obj.getClass();

            List<Field> allFields = Arrays.asList(Util.getAllFields(c));
            List<Method> allMethods = Arrays.asList(Util.getAllMethods(c));
            for (String fieldName : reqFields) {
                Optional<Field> field = allFields.stream().filter(f -> f.getName().equalsIgnoreCase(fieldName)).findAny();
                if (!field.isPresent()) {
                    fields.put(fieldName, TextNode.valueOf("ERROR: Field not found"));
                    continue;
                }

                field.get().setAccessible(true);

                try {
                    Object res = field.get().get(obj);
                    fields.put(fieldName, json.toJson(res, true, Permissions.permitAllNode()));
                } catch (IllegalAccessException e) {
                    fields.put(fieldName, TextNode.valueOf("ERROR: " + e.toString()));
                }
            }
            for (String methodName : reqMethods) {
                Optional<Method> method = allMethods.stream().filter(f -> f.getName().equalsIgnoreCase(methodName)).findAny();
                if (!method.isPresent()) {
                    methods.put(methodName, TextNode.valueOf("ERROR: Method not found"));
                    continue;
                }

                if (method.get().getParameterCount() > 0) {
                    methods.put(methodName, TextNode.valueOf("ERROR: Method must not have parameters"));
                    continue;
                }

                if (method.get().getReturnType().equals(Void.TYPE) || method.get().getReturnType().equals(Void.class)) {
                    methods.put(methodName, TextNode.valueOf("ERROR: Method must not return void"));
                    continue;
                }

                method.get().setAccessible(true);

                try {
                    Object res = method.get().invoke(obj);
                    methods.put(methodName, json.toJson(res, true, Permissions.permitAllNode()));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    methods.put(methodName, TextNode.valueOf("ERROR: " + e.toString()));
                }
            }

            return new Tuple<>(fields, methods);
        }).orElse(null);
    }

    public void updateWorlds() {
        WebAPI.runOnMain(() -> {
            worlds.clear();

            // The worlds that are loaded on server start are overwritten by the world load event later
            Collection<WorldProperties> unloadedWorlds = Sponge.getServer().getAllWorldProperties();
            for (WorldProperties world : unloadedWorlds) {
                updateWorld(world);
            }
        });
    }
    public Collection<CachedWorld> getWorlds() {
        return worlds.values();
    }
    public Optional<CachedWorld> getWorld(String nameOrUuid) {
        if (Util.isValidUUID(nameOrUuid)) {
            return getWorld(UUID.fromString(nameOrUuid));
        }

        Optional<CachedWorld> world = worlds.values().stream().filter(w -> w.getName().equalsIgnoreCase(nameOrUuid)).findAny();
        return world.flatMap(cachedWorld -> getWorld(cachedWorld.getUUID()));

    }
    public Optional<CachedWorld> getWorld(UUID uuid) {
        if (!worlds.containsKey(uuid)) {
            return Optional.empty();
        }

        final CachedWorld res = worlds.get(uuid);
        if (res.isExpired()) {
            return WebAPI.runOnMain(() -> {
                Optional<World> world = Sponge.getServer().getWorld(uuid);
                if (world.isPresent())
                    return updateWorld(world.get());
                Optional<WorldProperties> props = Sponge.getServer().getWorldProperties(uuid);
                return props.map(this::updateWorld).orElse(null);
            });
        } else {
            return Optional.of(res);
        }
    }
    public CachedWorld getWorld(World world) {
        Optional<CachedWorld> w = getWorld(world.getUniqueId());
        return w.orElseGet(() -> updateWorld(world));
    }
    public CachedWorld updateWorld(World world) {
        CachedWorld w = new CachedWorld(world);
        worlds.put(world.getUniqueId(), w);
        return w;
    }
    public CachedWorld updateWorld(WorldProperties world) {
        CachedWorld w = new CachedWorld(world);
        worlds.put(world.getUniqueId(), w);
        return w;
    }
    public void removeWorld(UUID worldUuid) {
        worlds.remove(worldUuid);
    }

    public Collection<CachedPlayer> getPlayers() {
        return players.values();
    }
    public Optional<CachedPlayer> getPlayer(UUID uuid) {
        if (!players.containsKey(uuid)) {
            return Optional.empty();
        }

        final CachedPlayer res = players.get(uuid);
        if (res.isExpired()) {
            return WebAPI.runOnMain(() -> {
                Optional<Player> player = Sponge.getServer().getPlayer(uuid);
                return player.map(this::updatePlayer).orElse(null);

            });
        } else {
            return Optional.of(res);
        }
    }
    public CachedPlayer getPlayer(Player player) {
        Optional<CachedPlayer> p = getPlayer(player.getUniqueId());
        return p.orElseGet(() -> updatePlayer(player));
    }
    public CachedPlayer updatePlayer(Player player) {
        CachedPlayer p = new CachedPlayer(player);
        players.put(player.getUniqueId(), p);
        return p;
    }
    public CachedPlayer removePlayer(UUID uuid) {
        return players.remove(uuid);
    }

    public Collection<CachedEntity> getEntities() {
        return entities.values();
    }
    public Optional<CachedEntity> getEntity(UUID uuid) {
        if (!entities.containsKey(uuid)) {
            return Optional.empty();
        }

        final CachedEntity res = entities.get(uuid);
        if (res.isExpired()) {
            return WebAPI.runOnMain(() -> {
                Optional<Entity> entity = Optional.empty();
                for (World world : Sponge.getServer().getWorlds()) {
                    Optional<Entity> ent = world.getEntity(uuid);
                    if (ent.isPresent()) {
                        entity = ent;
                        break;
                    }
                }
                return entity.map(this::updateEntity).orElse(null);
            });
        } else {
            return Optional.of(res);
        }
    }
    public CachedEntity getEntity(Entity entity) {
        Optional<CachedEntity> e = getEntity(entity.getUniqueId());
        return e.orElseGet(() -> updateEntity(entity));
    }
    public CachedEntity updateEntity(Entity entity) {
        CachedEntity e = new CachedEntity(entity);
        entities.put(entity.getUniqueId(), e);
        return e;
    }
    public CachedEntity removeEntity(UUID uuid) {
        return entities.remove(uuid);
    }

    public void updatePlugins() {
        plugins.clear();

        Collection<PluginContainer> newPlugins = Sponge.getPluginManager().getPlugins();
        for (PluginContainer plugin : newPlugins) {
            plugins.add(new CachedPluginContainer(plugin));
        }
    }
    public Collection<CachedPluginContainer> getPlugins() {
        return plugins;
    }
    public Optional<CachedPluginContainer> getPlugin(String id) {
        for (CachedPluginContainer plugin : plugins) {
            if (plugin.getId().equalsIgnoreCase(id))
                return Optional.of(plugin);
        }
        return Optional.empty();
    }
    public CachedPluginContainer getPlugin(PluginContainer plugin) {
        Optional<CachedPluginContainer> e = getPlugin(plugin.getId());
        return e.orElseGet(() -> new CachedPluginContainer(plugin));
    }

    public void updateCommands() {
        commands.clear();

        Collection<CommandMapping> newCommands = Sponge.getCommandManager().getAll().values();
        for (CommandMapping cmd : newCommands) {
            if (commands.stream().anyMatch(c -> c.getName().equalsIgnoreCase(cmd.getPrimaryAlias())))
                continue;
            commands.add(new CachedCommand(cmd, CommandSource.instance));
        }
    }
    public Collection<CachedCommand> getCommands() {
        return commands;
    }
    public Optional<CachedCommand> getCommand(String name) {
        for (CachedCommand cmd : commands) {
            if (cmd.getName().equalsIgnoreCase(name))
                return Optional.of(cmd);
        }
        return Optional.empty();
    }

    public Optional<Collection<CachedTileEntity>> getTileEntities() {
        return WebAPI.runOnMain(() -> {
            Collection<CachedTileEntity> entities = new LinkedList<>();

            for (World world : Sponge.getServer().getWorlds()) {
                Collection<TileEntity> ents = world.getTileEntities();
                for (TileEntity te : ents) {
                    entities.add(new CachedTileEntity(te));
                }
            }

            return entities;
        });
    }
    public Optional<Collection<CachedTileEntity>> getTileEntities(CachedWorld world) {
        return WebAPI.runOnMain(() -> {
            Optional<?> w = world.getLive();
            if (!w.isPresent())
                return null;

            Collection<CachedTileEntity> entities = new LinkedList<>();
            Collection<TileEntity> ents = ((World)w.get()).getTileEntities();
            for (TileEntity te : ents) {
                if (!te.isValid()) continue;
                entities.add(new CachedTileEntity(te));
            }

            return entities;
        });
    }
    public Optional<CachedTileEntity> getTileEntity(Location<World> location) {
        Optional<CachedWorld> w = this.getWorld(location.getExtent().getUniqueId());
        if (!w.isPresent())
            return Optional.empty();

        return getTileEntity(w.get(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    public Optional<CachedTileEntity> getTileEntity(CachedWorld world, int x, int y, int z) {
        return WebAPI.runOnMain(() -> {
            Optional<?> w = world.getLive();
            if (!w.isPresent())
                return null;

            Optional<TileEntity> ent = ((World)w.get()).getTileEntity(x, y, z);
            if (!ent.isPresent() || !ent.get().isValid()) {
                return null;
            }

            return new CachedTileEntity(ent.get());
        });
    }
}
