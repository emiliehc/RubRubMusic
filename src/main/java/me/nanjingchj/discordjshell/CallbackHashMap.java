package me.nanjingchj.discordjshell;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class CallbackHashMap<K, V> extends HashMap<K, V> implements Serializable {
    private transient Runnable callback;

    public CallbackHashMap(Runnable callback) {
        super();
        this.callback = callback;
    }

    public Runnable getCallback() {
        return callback;
    }

    public void setCallback(Runnable callback) {
        this.callback = callback;
    }

    @Override
    public V put(K key, V value) {
        try {
            return super.put(key, value);
        } finally {
            callback.run();
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        super.putAll(m);
        callback.run();
    }

    @Override
    public void clear() {
        super.clear();
        callback.run();
    }

    @Override
    public boolean remove(Object key, Object value) {
        try {
            return super.remove(key, value);
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        try {
            return super.replace(key, oldValue, newValue);
        } finally {
            callback.run();
        }
    }

    @Override
    public V replace(K key, V value) {
        try {
            return super.replace(key, value);
        } finally {
            callback.run();
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        super.forEach(action);
        callback.run();
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        super.replaceAll(function);
        callback.run();
    }
}
