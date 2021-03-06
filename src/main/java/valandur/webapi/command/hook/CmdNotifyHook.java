package valandur.webapi.command.hook;

import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.util.Tuple;
import valandur.webapi.WebAPI;
import valandur.webapi.hook.CommandWebHook;
import valandur.webapi.hook.WebHookParam;

import java.util.*;

public class CmdNotifyHook implements CommandExecutor {

    private CommandWebHook cmdHook;

    public CmdNotifyHook(CommandWebHook cmdHook) {
        this.cmdHook = cmdHook;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        Map<String, Tuple<String, Object>> data = new LinkedHashMap<>();
        for (WebHookParam param : cmdHook.getParams()) {
            data.put(param.getName(), param.getValue(args).orElse(null));
        }


        WebAPI.getWebHookService().notifyHook(cmdHook, src.getIdentifier(), data);

        return CommandResult.success();
    }
}
