package valandur.webapi.api.cache;

import org.spongepowered.api.data.DataHolder;
import valandur.webapi.api.WebAPIAPI;

import java.util.Optional;

public abstract class CachedObject {
    protected long cachedAt;
    protected long cacheDuration;

    protected Class clazz;
    public Class getObjectClass() {
        return clazz;
    }

    protected DataHolder data;
    public DataHolder getData() {
        return data;
    }


    public CachedObject(Object obj) {
        this.cachedAt = System.nanoTime();
        WebAPIAPI.getCacheService().ifPresent(srv -> this.cacheDuration = srv.getCacheDurationFor(this.getClass()));

        if (obj != null) this.clazz = obj.getClass();

        if (obj instanceof DataHolder) {
            this.data = ((DataHolder)obj).copy();
        }
    }

    public String getLink() {
        return null;
    }

    public Optional<?> getLive() {
        return Optional.empty();
    }
    public final boolean isExpired() {
        return (System.nanoTime() - cachedAt) / 1000000000 > cacheDuration;
    }
}
