package sendero;

import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

class Appointer<A> extends AtomicBinaryEventConsumer {
    public static final Appointer<?> CLEARED_APPOINTER = new Appointer<Object>(null, null) {

        @Override
        public boolean on() {
            return false;
        }

        @Override
        public boolean start() {
            return false;
        }

        @Override
        public boolean isCleared() {
            return true;
        }
    };

    final BasePath<A> producer;
    final Consumer<Pair.Immutables.Int<A>> toAppoint;

    private static<T, S> Consumer<Pair.Immutables.Int<S>> toAppointCreator(Consumer<Pair.Immutables.Int<T>> holder, Function<S, T> map) {
        return sInt -> holder.accept(new Pair.Immutables.Int<>(sInt.getInt(), map.apply(sInt.getValue())));
    }

    private static<T, S> Consumer<Pair.Immutables.Int<S>> updaterAppointCreator(Holders.DispatcherHolder<T> holder, BiFunction<T ,S , T> map) {
        return sInt -> holder.acceptVersionValue(new Pair.Immutables.Int<>(sInt.getInt(), map.apply(holder.get(), sInt.getValue())));
    }

    private static<S, T> Appointer<S> fixedAppointer(BasePath<S> producer, Consumer<Pair.Immutables.Int<T>> holder, Function<S, T> map) {
        return new Appointer<>(producer, toAppointCreator(holder, map));
    }

    private static<S, T> Appointer<S> fixedAppointer(BasePath<S> producer, Holders.DispatcherHolder<T> holder, BiFunction<T, S, T> map) {
        return new Appointer<>(producer, updaterAppointCreator(holder, map));
    }

    public static<T> Appointer<T> fixedAppointer(BasePath<T> producer, Consumer<Pair.Immutables.Int<T>> holder) {
        return new Appointer<>(producer, holder);
    }

    public static<S, T> AtomicBinaryEventConsumer booleanConsumerAppointer(BasePath<S> producer, Consumer<Pair.Immutables.Int<T>> holder, Function<S, T> map) {
        return fixedAppointer(producer, holder, map);
    }

    public static<S, T> AtomicBinaryEventConsumer booleanConsumerAppointer(BasePath<S> producer, Holders.DispatcherHolder<T> holder, BiFunction<T, S, T> map) {
        return fixedAppointer(producer, holder, map);
    }

    Appointer(BasePath<A> producer, Consumer<Pair.Immutables.Int<A>> toAppoint) {
        this.producer = producer;
        this.toAppoint = toAppoint;
    }

    @Override
    protected void onStateChange(boolean isActive) {
        if (isActive) appoint();
        else demote();
    }

    private void appoint() {
        producer.appoint(toAppoint);
    }
    private void demote() {
        producer.demotionOverride(toAppoint);
    }

    public<P> boolean equalTo(BasePath<P> basePath) {
        return basePath.equals(producer);
    }
}

