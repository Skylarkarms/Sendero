package sendero.interfaces;

import java.util.function.Consumer;

@FunctionalInterface
public interface Register<V> {
    void register(Consumer<? super V> valueConsumer);

    static<V> Register<V> nullCheck(V value) {
        return valueConsumer -> {
            if (value != null) valueConsumer.accept(value);
        };
    }
}
