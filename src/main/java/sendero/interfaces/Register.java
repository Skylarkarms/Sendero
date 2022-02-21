package sendero.interfaces;

import java.util.function.Consumer;

@FunctionalInterface
public interface Register<V> {
    void register(Consumer<V> valueConsumer);

    static<V> Register<V> cleared() {
        return valueConsumer -> {
        };
    }
}
