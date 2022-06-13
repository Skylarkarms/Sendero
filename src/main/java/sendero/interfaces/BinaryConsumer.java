package sendero.interfaces;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface BinaryConsumer<T> extends BiConsumer<T, T> {
    @Override
    void accept(T prev, T next);

    BinaryConsumer<?> empty = new BinaryConsumer<Object>() {
        @Override
        public void accept(Object prev, Object next) {

        }

        @Override
        public String toString() {
            return super.toString() + "[EMPTY CONSUMER]";
        }
    };

    default boolean isEmpty() {
        return this == empty;
    }

    @SuppressWarnings("unchecked")
    static<S> BinaryConsumer<S> getEmpty() {
        return (BinaryConsumer<S>) empty;
    }
}
