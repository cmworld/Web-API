package valandur.webapi.command;

import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandManager;
import org.spongepowered.api.command.CommandMapping;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;
import valandur.webapi.WebAPI;
import valandur.webapi.services.BlockService;
import valandur.webapi.command.auth.CmdAuthListAdd;
import valandur.webapi.command.auth.CmdAuthListDisable;
import valandur.webapi.command.auth.CmdAuthListEnable;
import valandur.webapi.command.auth.CmdAuthListRemove;
import valandur.webapi.command.block.CmdBlockUpdatesList;
import valandur.webapi.command.element.CmdIpElement;
import valandur.webapi.command.hook.CmdNotifyHook;
import valandur.webapi.command.user.CmdUserAdd;
import valandur.webapi.command.user.CmdUserChangePassword;
import valandur.webapi.command.user.CmdUserList;
import valandur.webapi.hook.CommandWebHook;
import valandur.webapi.hook.WebHookParam;
import valandur.webapi.util.Util;

import java.util.*;
import java.util.stream.Collectors;

public class CommandRegistry {
    private static List<CommandMapping> mappings =  new ArrayList<>();

    public static void init() {
        CommandManager manager = Sponge.getCommandManager();
        Logger logger = WebAPI.getLogger();

        // Register commands
        logger.info("Registering commands...");

        // Remove old commands
        for (CommandMapping mapping : mappings) {
            manager.removeMapping(mapping);
        }
        mappings.clear();

        // Whitelist
        CommandSpec specWhitelistAdd = CommandSpec.builder()
                .description(Text.of("Add an IP to the whitelist"))
                .permission("webapi.command.whitelist.add")
                .arguments(new CmdIpElement(Text.of("ip")))
                .executor(new CmdAuthListAdd(true))
                .build();
        CommandSpec specWhitelistRemove = CommandSpec.builder()
                .description(Text.of("Remove an IP from the whitelist"))
                .permission("webapi.command.whitelist.remove")
                .arguments(new CmdIpElement(Text.of("ip")))
                .executor(new CmdAuthListRemove(true))
                .build();
        CommandSpec specWhitelistEnable = CommandSpec.builder()
                .description(Text.of("Enable the whitelist"))
                .permission("webapi.command.whitelist.enable")
                .executor(new CmdAuthListEnable(true))
                .build();
        CommandSpec specWhitelistDisable = CommandSpec.builder()
                .description(Text.of("Disable the whitelist"))
                .permission("webapi.command.whitelist.disable")
                .executor(new CmdAuthListDisable(true))
                .build();
        CommandSpec specWhitelist = CommandSpec.builder()
                .description(Text.of("Manage the whitelist"))
                .permission("webapi.command.whitelist")
                .child(specWhitelistAdd, "add")
                .child(specWhitelistRemove, "remove")
                .child(specWhitelistEnable, "enable")
                .child(specWhitelistDisable, "disable")
                .build();

        // Blacklist
        CommandSpec specBlacklistAdd = CommandSpec.builder()
                .description(Text.of("Add an IP to the blacklist"))
                .permission("webapi.command.blacklist.add")
                .arguments(GenericArguments.string(Text.of("ip")))
                .executor(new CmdAuthListAdd(false))
                .build();
        CommandSpec specBlaclistRemove = CommandSpec.builder()
                .description(Text.of("Remove an IP from the blacklist"))
                .permission("webapi.command.blacklist.remove")
                .arguments(GenericArguments.string(Text.of("ip")))
                .executor(new CmdAuthListRemove(false))
                .build();
        CommandSpec specBlacklistEnable = CommandSpec.builder()
                .description(Text.of("Enable the blacklist"))
                .permission("webapi.command.blacklist.enable")
                .executor(new CmdAuthListEnable(false))
                .build();
        CommandSpec specBlacklistDisable = CommandSpec.builder()
                .description(Text.of("Disable the blacklist"))
                .permission("webapi.command.blacklist.disable")
                .executor(new CmdAuthListDisable(false))
                .build();
        CommandSpec specBlacklist = CommandSpec.builder()
                .description(Text.of("Manage the blacklist"))
                .permission("webapi.command.blacklist")
                .child(specBlacklistAdd, "add")
                .child(specBlaclistRemove, "remove")
                .child(specBlacklistEnable, "enable")
                .child(specBlacklistDisable, "disable")
                .build();

        final BlockService blockService = WebAPI.getBlockService();
        // Block updates
        CommandSpec specBlockUpdatesList = CommandSpec.builder()
                .description(Text.of("List all running block updates"))
                .permission("webapi.command.blocks.list")
                .executor(new CmdBlockUpdatesList())
                .build();
        CommandSpec specBlockUpdatesPause = CommandSpec.builder()
                .description(Text.of("Pause/Resume running block updates"))
                .permission("webapi.command.blocks.pause")
                .arguments(GenericArguments.choices(Text.of("uuid"),
                        () -> blockService.getBlockUpdates().stream().map(u -> u.getUUID().toString()).collect(Collectors.toList()),
                        uuid -> blockService.getBlockUpdate(UUID.fromString(uuid))))
                .executor(new CmdBlockUpdatesList())
                .build();
        CommandSpec specBlockUpdatesStop = CommandSpec.builder()
                .description(Text.of("Stop a running block update"))
                .permission("webapi.command.blocks.stop")
                .arguments(GenericArguments.choices(Text.of("uuid"),
                        () -> blockService.getBlockUpdates().stream().map(u -> u.getUUID().toString()).collect(Collectors.toList()),
                        uuid -> Util.isValidUUID(uuid) ? blockService.getBlockUpdate(UUID.fromString(uuid)) : null))
                .executor(new CmdBlockUpdatesList())
                .build();
        CommandSpec specBlockUpdates = CommandSpec.builder()
                .description(Text.of("Manage running block updates"))
                .permission("webapi.command.blocks")
                .child(specBlockUpdatesList, "list")
                .child(specBlockUpdatesPause, "pause")
                .child(specBlockUpdatesStop, "stop", "delete", "remove")
                .build();

        // Users
        CommandSpec specUserList = CommandSpec.builder()
                .description(Text.of("List all the users which have access to the admin panel"))
                .permission("webapi.user.list")
                .executor(new CmdUserList())
                .build();
        CommandSpec specUserAdd = CommandSpec.builder()
                .description(Text.of("Add a new user that can access the admin panel"))
                .permission("webapi.user.add")
                .arguments(
                        GenericArguments.onlyOne(GenericArguments.string(Text.of("username"))),
                        GenericArguments.optional(GenericArguments.string(Text.of("password")))
                )
                .executor(new CmdUserAdd())
                .build();
        CommandSpec specUserChangePw = CommandSpec.builder()
                .description(Text.of("Change the password of an existing user"))
                .permission("webapi.user.changepw")
                .arguments(
                        GenericArguments.onlyOne(GenericArguments.string(Text.of("username"))),
                        GenericArguments.onlyOne(GenericArguments.string(Text.of("password")))
                )
                .executor(new CmdUserChangePassword())
                .build();
        CommandSpec specUsers = CommandSpec.builder()
                .description(Text.of("Manage the users of the admin panel"))
                .permission("webapi.user")
                .child(specUserList, "list")
                .child(specUserAdd, "add")
                .child(specUserChangePw, "pw", "changepw", "password")
                .build();

        // Notify commands
        Map<List<String>, CommandSpec> hookSpecs = new HashMap<>();
        Map<List<String>, CommandSpec> hookAliases = new HashMap<>();
        for (Map.Entry<String, CommandWebHook> entry : WebAPI.getWebHookService().getCommandHooks().entrySet()) {
            List<CommandElement> args = new ArrayList<>();
            String name = entry.getKey();
            CommandWebHook hook = entry.getValue();

            if (!hook.isEnabled())
                continue;

            if (hook.getParams() != null) {
                for (WebHookParam param : hook.getParams()) {
                    Optional<CommandElement> e = param.getCommandElement();
                    e.ifPresent(args::add);
                }
            }

            CommandSpec hookCmd = CommandSpec.builder()
                    .description(Text.of("Notify the " + name + " hook"))
                    .permission("webapi.command.notify." + name)
                    .arguments(args.toArray(new CommandElement[args.size()]))
                    .executor(new CmdNotifyHook(hook))
                    .build();
            if (hook.getAliases() != null && hook.getAliases().size() > 0) hookAliases.put(hook.getAliases(), hookCmd);
            hookSpecs.put(Collections.singletonList(name), hookCmd);
        }

        // Notify parent
        CommandSpec specNotifyHook = CommandSpec.builder()
                .description(Text.of("Notify a hook"))
                .permission("webapi.command.notify")
                .children(hookSpecs)
                .build();

        // Register main command
        CommandSpec spec = CommandSpec.builder()
                .description(Text.of("Manage Web-API settings"))
                .permission("webapi.command")
                .child(specWhitelist, "whitelist")
                .child(specBlacklist, "blacklist")
                .child(specNotifyHook, "notify")
                .child(specBlockUpdates, "blocks")
                .child(specUsers, "users")
                .build();
        manager.register(WebAPI.getInstance(), spec, "webapi").map(m -> mappings.add(m));

        // Register aliases for notify commands
        for (Map.Entry<List<String>, CommandSpec> entry : hookAliases.entrySet()) {
            manager.register(WebAPI.getInstance(), entry.getValue(), entry.getKey()).map(m -> mappings.add(m));
        }
    }
}
