package valandur.webapi.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.World;
import valandur.webapi.Permission;
import valandur.webapi.misc.JsonConverter;
import valandur.webapi.misc.Util;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class WorldServlet extends APIServlet {
    @Override
    @Permission(perm = "world")
    protected void handleGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JsonObject json = new JsonObject();
        resp.setContentType("application/json; charset=utf-8");
        resp.setStatus(HttpServletResponse.SC_OK);

        String[] paths = Util.getPathParts(req);

        if (paths.length == 0 || paths[0].isEmpty()) {
            JsonArray arr = new JsonArray();
            Collection<World> worlds = Sponge.getServer().getWorlds();
            for (World world : worlds) {
                arr.add(JsonConverter.toJson(world));
            }
            json.add("worlds", arr);
        } else {
            String wName = paths[0];
            Optional<World> res = Sponge.getServer().getWorld(wName);
            if (!res.isPresent()) {
                res = Sponge.getServer().getWorld(UUID.fromString(wName));
            }

            if (res.isPresent()) {
                if (paths.length == 1 || paths[1].isEmpty()) {
                    json.add("world", JsonConverter.toJson(res.get(), true));
                } else {
                    String detName = paths[1];
                    switch (detName) {
                        case "entity":
                            break;

                        case "chunks":
                            break;

                        case "rules":
                            break;

                        case "tileEntity":
                            break;
                    }
                }
            } else {
                json.addProperty("error", "World with name/uuid " + wName + " not found");
            }
        }

        PrintWriter out = resp.getWriter();
        out.print(json);
    }
}
