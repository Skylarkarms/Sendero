package sendero;

import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class BinaryEventConsumers {
    private static<T, S> Consumer<Pair.Immutables.Int<S>> toAppointCreator(Consumer<Pair.Immutables.Int<T>> holder, Function<S, T> map) {
        return sInt -> holder.accept(new Pair.Immutables.Int<>(sInt.getInt(), map.apply(sInt.getValue())));
    }

    private static<T, S> Consumer<Pair.Immutables.Int<S>> updaterAppointCreator(Holders.DispatcherHolder<T> holder, BiFunction<T ,S , T> updateFun) {
        //Link.Bound begins with a versions value of 1 (initialValue) in order to prevent null checks on first update,
        //By incrementing the source's version by one, we allow for the first version to be allowed to pass.
        return sInt -> holder.acceptVersionValue(new Pair.Immutables.Int<>(sInt.getInt() + 1, updateFun.apply(holder.get(), sInt.getValue())));
    }

    private static<T, S> Consumer<Pair.Immutables.Int<S>> simpleAppointer(Consumer<T> holder, Function<S , T> map) {
        return sInt -> holder.accept(map.apply(sInt.getValue()));
    }

    static<T> Appointer<T> fixedAppointer(BasePath<T> producer, Consumer<Pair.Immutables.Int<T>> holder) {
        return new Appointer<>(producer, holder);
    }

    static<S, T> AtomicBinaryEventConsumer producerHolderConnector(BasePath<S> producer, Consumer<Pair.Immutables.Int<T>> holder, Function<S, T> map) {
        return new Appointer<>(producer, toAppointCreator(holder, map));
    }

    public static<S, T> AtomicBinaryEventConsumer producerConnector(BasePath<S> producer, Function<S, T> map, Consumer<T> consumer) {
        return new Appointer<>(producer, simpleAppointer(consumer, map));
    }

    public static<T> AtomicBinaryEventConsumer producerConnector(BasePath<T> producer, Consumer<T> consumer) {
        return new Appointer<>(producer, simpleAppointer(consumer, UnaryOperator.identity()));
    }

    static<S, T> AtomicBinaryEventConsumer producerHolderConnector(BasePath<S> producer, Holders.DispatcherHolder<T> holder, BiFunction<T, S, T> updateFun) {
        return new Appointer<>(producer, updaterAppointCreator(holder, updateFun));
    }
}
