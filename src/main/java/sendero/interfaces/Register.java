package sendero.interfaces;

import java.util.function.Consumer;

@FunctionalInterface
public interface Register<V> {
    void register(Consumer<? super V> valueConsumer);
}
