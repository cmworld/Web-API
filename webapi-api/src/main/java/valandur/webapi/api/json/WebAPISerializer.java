package valandur.webapi.api.json;

import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import net.jodah.typetools.TypeResolver;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.util.Tristate;
import valandur.webapi.api.permission.Permissions;
import valandur.webapi.api.service.IJsonService;
import valandur.webapi.api.util.TreeNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class WebAPISerializer<T> extends StdSerializer<T> {

    protected IJsonService json;

    private Class clazz;
    public Class getHandledClass() {
        return clazz;
    }

    public WebAPISerializer() {
        this(null);
        this.clazz = TypeResolver.resolveRawArgument(WebAPISerializer.class, getClass());
        this.json = Sponge.getServiceManager().provide(IJsonService.class).orElse(null);
    }
    private WebAPISerializer(Class<T> t) {
        super(t);
    }


    protected boolean shouldWriteDetails(SerializerProvider provider) {
        return ((AtomicBoolean)provider.getAttribute("details")).get();
    }

    protected void writeData(SerializerProvider provider, DataHolder holder) throws IOException {
        for (Map.Entry<String, Class> entry : json.getSupportedData().entrySet()) {
            Optional<?> m = holder.get(entry.getValue());

            if (!m.isPresent())
                continue;

            writeField(provider, entry.getKey(), m.get());
        }
    }

    protected void writeField(SerializerProvider provider, String key, Object value) throws IOException {
        writeField(provider, key, value, Tristate.UNDEFINED);
    }
    protected void writeField(SerializerProvider provider, String key, Object value, Tristate details) throws IOException {
        TreeNode<String, Boolean> incs = (TreeNode<String, Boolean>)provider.getAttribute("includes");
        List<String> parents = (List<String>)provider.getAttribute("parents");

        parents.add(key);

        AtomicBoolean currDetails = (AtomicBoolean)provider.getAttribute("details");
        boolean prevDetails = currDetails.get();
        if (details == Tristate.TRUE) {
            currDetails.set(true);
        } else if (details == Tristate.FALSE) {
            currDetails.set(false);
        }

        if (!Permissions.permits(incs, parents)) {
            parents.remove(key);
            return;
        }

        provider.getGenerator().writeObjectField(key, value);

        currDetails.set(prevDetails);

        parents.remove(key);
    }

    protected void writeValue(SerializerProvider provider, Object value) throws IOException {
        writeValue(provider, value, Tristate.UNDEFINED);
    }
    protected void writeValue(SerializerProvider provider, Object value, Tristate details) throws IOException {
        TreeNode<String, Boolean> incs = (TreeNode<String, Boolean>)provider.getAttribute("includes");
        List<String> parents = (List<String>)provider.getAttribute("parents");

        AtomicBoolean currDetails = (AtomicBoolean)provider.getAttribute("details");
        boolean prevDetails = currDetails.get();
        if (details == Tristate.TRUE) {
            currDetails.set(true);
        } else if (details == Tristate.FALSE) {
            currDetails.set(false);
        }

        if (!Permissions.permits(incs, parents)) {
            currDetails.set(prevDetails);
            return;
        }

        provider.getGenerator().writeObject(value);

        currDetails.set(prevDetails);
    }
}
