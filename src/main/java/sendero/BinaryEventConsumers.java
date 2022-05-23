package sendero;

import sendero.functions.Functions;
import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class BinaryEventConsumers {
    @SuppressWarnings("unchecked")
    private static<T, S> Consumer<Pair.Immutables.Int<S>> toAppointCreator(Consumer<Pair.Immutables.Int<T>> holder, Function<S, T> map) {
        if (Functions.isIdentity(map)) return sInt -> holder.accept((Pair.Immutables.Int<T>) sInt);
        else return sInt -> holder.accept(new Pair.Immutables.Int<>(sInt.getInt(), map.apply(sInt.getValue())));
    }

    /*This update operation will happen ONCE as opposed to holder's update which will retry until set.
    * This update operation follows the same rule established by all Sendero's updates which is to ignore updates if instance is the same as prev.
    * Version checks will be performed at the holder level (.acceptVersionValue) where old versions (newVersion < currentVersion) will be ignored
    *
    * Forkable's update function may not be required to use an INITIAL value,since a single source is required anyway, so a null check will be required on first set*/
    private static<T, S> Consumer<Pair.Immutables.Int<S>> updaterAppointCreator(Holders.ColdHolder<T> holder, BiFunction<T ,S , T> updateFun) {
        return sInt -> {
            T prev = holder.get(), next = updateFun.apply(prev, sInt.getValue());
            if (prev != next) {
                holder.accept(
                        new Pair.Immutables.Int<>(
                                //Link.Bound begins with a versions value of 1 (initialValue) this is done to prevent null checks on first update,
                                //By incrementing the source's version by one, we allow for the first version to be allowed to pass.
                                sInt.getInt() + 1,
                        next
                        )
                );
            }
        };
    }

    private static<T, S> Consumer<Pair.Immutables.Int<S>> simpleAppointer(Consumer<T> holder, Function<S , T> map) {
        return sInt -> holder.accept(map.apply(sInt.getValue()));
    }

    static<T> Appointer<T> fixedAppointer(BasePath<T> producer, Consumer<Pair.Immutables.Int<T>> holder) {
        return new Appointer<>(producer, holder);
    }

    static<S, T> AtomicBinaryEventConsumer producerHolderConnector(
            BasePath<S> producer,
            Consumer<Pair.Immutables.Int<T>> holder,
            Function<S, T> map
    ) {
        return new Appointer<>(producer, toAppointCreator(holder, map));
    }

    public static<S, T> AtomicBinaryEventConsumer producerConnector(BasePath<S> producer, Function<S, T> map, Consumer<T> consumer) {
        return new Appointer<>(producer, simpleAppointer(consumer, map));
    }

    public static<T> AtomicBinaryEventConsumer producerConnector(BasePath<T> producer, Consumer<T> consumer) {
        return new Appointer<>(producer, simpleAppointer(consumer, UnaryOperator.identity()));
    }

    static<S, T> AtomicBinaryEventConsumer producerHolderConnector(BasePath<S> producer, Holders.ColdHolder<T> holder, BiFunction<T, S, T> updateFun) {
        return new Appointer<>(producer, updaterAppointCreator(holder, updateFun));
    }
}
