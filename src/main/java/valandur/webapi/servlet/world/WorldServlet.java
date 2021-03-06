package valandur.webapi.servlet.world;

import com.fasterxml.jackson.databind.JsonNode;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.WorldArchetype;
import org.spongepowered.api.world.storage.WorldProperties;
import valandur.webapi.WebAPI;
import valandur.webapi.api.annotation.WebAPIRoute;
import valandur.webapi.api.annotation.WebAPIServlet;
import valandur.webapi.api.cache.world.CachedWorld;
import valandur.webapi.api.servlet.WebAPIBaseServlet;
import valandur.webapi.servlet.ServletData;
import valandur.webapi.util.Util;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@WebAPIServlet(basePath = "world")
public class WorldServlet extends WebAPIBaseServlet {

    @WebAPIRoute(method = "GET", path = "/", perm = "list")
    public void getWorlds(ServletData data) {
        data.addJson("ok", true, false);
        data.addJson("worlds", cacheService.getWorlds(), data.getQueryParam("details").isPresent());
    }

    @WebAPIRoute(method = "GET", path = "/:world", perm = "one")
    public void getWorld(ServletData data, CachedWorld world) {
        Optional<String> strFields = data.getQueryParam("fields");
        Optional<String> strMethods = data.getQueryParam("methods");
        if (strFields.isPresent() || strMethods.isPresent()) {
            String[] fields = strFields.map(s -> s.split(",")).orElse(new String[]{});
            String[] methods = strMethods.map(s -> s.split(",")).orElse(new String[]{});
            Tuple extra = cacheService.getExtraData(world, fields, methods);
            data.addJson("fields", extra.getFirst(), true);
            data.addJson("methods", extra.getSecond(), true);
        }

        data.addJson("ok", true, false);
        data.addJson("world", world, true);
    }

    @WebAPIRoute(method = "POST", path = "/", perm = "create")
    public void createWorld(ServletData data) {
        WorldArchetype.Builder builder = WorldArchetype.builder();

        Optional<CreateWorldRequest> optReq = data.getRequestBody(CreateWorldRequest.class);
        if (!optReq.isPresent()) {
            data.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid world data: " + data.getLastParseError().getMessage());
            return;
        }

        CreateWorldRequest req = optReq.get();

        if (req.getName().isEmpty()) {
            data.sendError(HttpServletResponse.SC_BAD_REQUEST, "No name provided");
            return;
        }

        req.getDimensionType().ifPresent(builder::dimension);
        req.getGeneratorType().ifPresent(builder::generator);
        req.getGameMode().ifPresent(builder::gameMode);
        req.getDifficulty().ifPresent(builder::difficulty);

        if (req.getSeed() != null) {
            builder.seed(req.getSeed());
        }
        if (req.doesLoadOnStartup() != null) {
            builder.loadsOnStartup(req.doesLoadOnStartup());
        }
        if (req.doesKeepSpawnLoaded() != null) {
            builder.keepsSpawnLoaded(req.doesKeepSpawnLoaded());
        }
        if (req.doesAllowCommands() != null) {
            builder.commandsAllowed(req.doesAllowCommands());
        }
        if (req.doesGenerateBonusChest() != null) {
            builder.generateBonusChest(req.doesGenerateBonusChest());
        }
        if (req.doesUseMapFeatures() != null) {
            builder.usesMapFeatures(req.doesUseMapFeatures());
        }

        String archTypeName = UUID.randomUUID().toString();
        WorldArchetype archType = builder.enabled(true).build(archTypeName, archTypeName);

        Optional<WorldProperties> resProps = WebAPI.runOnMain(() -> {
            try {
                return Sponge.getServer().createWorldProperties(req.getName(), archType);
            } catch (IOException e) {
                data.addJson("ok", false, false);
                data.addJson("error", e, false);
                return null;
            }
        });

        if (!resProps.isPresent())
            return;

        CachedWorld world = cacheService.updateWorld(resProps.get());

        data.setStatus(HttpServletResponse.SC_CREATED);
        data.addJson("ok", true, false);
        data.addJson("world", world, true);
        data.setHeader("Location", world.getLink());
    }

    @WebAPIRoute(method = "POST", path = "/:world/method", perm = "method")
    public void executeMethod(ServletData data, CachedWorld world) {
        JsonNode reqJson = data.getRequestBody();
        if (!reqJson.has("method")) {
            data.sendError(HttpServletResponse.SC_BAD_REQUEST, "Request must define the 'method' property");
            return;
        }

        String mName = reqJson.get("method").asText();
        Optional<Tuple<Class[], Object[]>> params = Util.parseParams(reqJson.get("params"));

        if (!params.isPresent()) {
            data.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid parameters");
            return;
        }

        Optional<Object> res = cacheService.executeMethod(world, mName, params.get().getFirst(), params.get().getSecond());
        if (!res.isPresent()) {
            data.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not get world");
            return;
        }

        data.addJson("ok", true, false);
        data.addJson("world", world, true);
        data.addJson("result", res.get(), true);
    }

    @WebAPIRoute(method = "PUT", path = "/:world", perm = "change")
    public void updateWorld(ServletData data, CachedWorld world) {
        Optional<UpdateWorldRequest> optReq = data.getRequestBody(UpdateWorldRequest.class);
        if (!optReq.isPresent()) {
            data.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid world data: " + data.getLastParseError().getMessage());
            return;
        }

        final UpdateWorldRequest req = optReq.get();

        Optional<CachedWorld> resWorld = WebAPI.runOnMain(() -> {
            Optional<?> optLive = world.getLive();
            if (!optLive.isPresent())
                return null;

            Object live = optLive.get();
            WorldProperties props = live instanceof World ? ((World) live).getProperties() : (WorldProperties) live;

            if (req.isLoaded() != null && req.isLoaded() != world.isLoaded()) {
                if (req.isLoaded()) {
                    Optional<World> newWorld = Sponge.getServer().loadWorld(props);
                    if (newWorld.isPresent()) {
                        live = newWorld.get();
                        props = newWorld.get().getProperties();
                    }
                } else {
                    Sponge.getServer().unloadWorld((World)live);
                    Optional<WorldProperties> optProps = Sponge.getServer().getUnloadedWorlds()
                            .stream().filter(w -> w.getUniqueId().equals(world.getUUID())).findAny();
                    if (optProps.isPresent()) {
                        live = optProps.get();
                        props = optProps.get();
                    }
                }
            }

            if (req.getGameRules() != null) {
                for (Map.Entry<String, String> entry : req.getGameRules().entrySet()) {
                    props.setGameRule(entry.getKey(), entry.getValue());
                }
            }

            req.getGeneratorType().ifPresent(props::setGeneratorType);
            req.getGameMode().ifPresent(props::setGameMode);
            req.getDifficulty().ifPresent(props::setDifficulty);

            if (req.getSeed() != null) {
                props.setSeed(req.getSeed());
            }
            if (req.doesLoadOnStartup() != null) {
                props.setLoadOnStartup(req.doesLoadOnStartup());
            }
            if (req.doesKeepSpawnLoaded() != null) {
                props.setKeepSpawnLoaded(req.doesKeepSpawnLoaded());
            }
            if (req.doesAllowCommands() != null) {
                props.setCommandsAllowed(req.doesAllowCommands());
            }
            if (req.doesUseMapFeatures() != null) {
                props.setMapFeaturesEnabled(req.doesUseMapFeatures());
            }

            if (live instanceof World)
                return cacheService.updateWorld((World) live);
            else
                return cacheService.updateWorld((WorldProperties) live);
        });

        data.addJson("ok", resWorld.isPresent(), false);
        data.addJson("world", resWorld.orElse(null), true);
    }

    @WebAPIRoute(method = "DELETE", path = "/:world", perm = "delete")
    public void deleteWorld(ServletData data, CachedWorld world) {
        Optional<Boolean> deleted = WebAPI.runOnMain(() -> {
            Optional<?> live = world.getLive();
            if (!live.isPresent())
                return false;

            WorldProperties w = (WorldProperties)live.get();
            try {
                return Sponge.getServer().deleteWorld(w).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            return false;
        });

        if (!deleted.isPresent() || !deleted.get()) {
            data.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not delete world " + world.getName());
            return;
        }

        cacheService.removeWorld(world.getUUID());

        data.addJson("ok", true, false);
        data.addJson("world", world, true);
    }
}
