package sendero;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ProactiveSupplier<T> extends Supplier<T>, AtomicBinaryEvent {
    void get(long delay, Consumer<? super T> tConsumer);
    void get(Consumer<? super T> tConsumer);
}
