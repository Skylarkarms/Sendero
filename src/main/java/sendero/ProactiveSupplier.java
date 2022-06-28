package sendero;

import sendero.switchers.Switchers;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ProactiveSupplier<T> extends Supplier<T>, Switchers.Switch {
    void get(long delay, Consumer<? super T> tConsumer);
    void get(Consumer<? super T> tConsumer);
}
