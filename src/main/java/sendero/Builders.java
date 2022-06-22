package sendero;

import sendero.functions.Functions;
import sendero.interfaces.BinaryConsumer;
import sendero.interfaces.BinaryPredicate;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static sendero.Holders.SynthEqual.hashCodeOf;

public final class Builders {

    public static<S> UnaryOperator<HolderBuilder<S>> excludeIn(BinaryPredicate<S> excludeInput) {
        return sHolderBuilder -> sHolderBuilder.excludeIn(excludeInput);
    }
    public static<S> UnaryOperator<HolderBuilder<S>> withInitial(S value) {
        return sHolderBuilder -> sHolderBuilder.withInitial(value);
    }
    public static <S> UnaryOperator<ManagerBuilder> withFixed(
            Function<Holders.StreamManager<S>, AtomicBinaryEvent> activationListenerFun
    ) {
        return managerBuilder -> managerBuilder.withFixedFun(activationListenerFun);
    }
    public static UnaryOperator<ManagerBuilder> mutabilityAllowed() {
        return managerBuilder -> managerBuilder.withMutable(true);
    }
    public static UnaryOperator<ManagerBuilder> mutabilityAllowed(boolean value) {
        return managerBuilder -> managerBuilder.withMutable(value);
    }

    static <T> HolderBuilder<T> getHolderBuild2() {
        return new HolderBuilder<>();
    }

    static ManagerBuilder getManagerBuild() {
        return new ManagerBuilder();
    }

    public static class HolderBuilder<T> {
        AtomicReference<Immutable<T>> value = new AtomicReference<>(Immutable.getNotSet());
        Predicate<T> expectOut;
        BinaryPredicate<T> expectInput;

        private HolderBuilder() {
            expectInput = BinaryPredicate.always(true);
            expectOut = Functions.always(true);
        }

        public HolderBuilder<T> withInitial(T value) {
            this.value = new AtomicReference<>(Immutable.forFirstValue(value));
            return this;
        }

        public HolderBuilder<T> withContainer(Container<T> container) {
            this.value = container.getRef();
            return this;
        }

        public HolderBuilder<T> excludeIn(Predicate<T> excludeInput) {
            this.expectInput = (next, prev) -> !excludeInput.test(next);
            return this;
        }

        public HolderBuilder<T> excludeOut(Predicate<T> excludeOutput) {
            this.expectOut = next -> !excludeOutput.test(next);
            return this;
        }

        public HolderBuilder<T> expectIn(Predicate<T> expectInput) {
            this.expectInput = (next, prev) -> expectInput.test(next);
            return this;
        }

        public HolderBuilder<T> expectIn(BinaryPredicate<T> expectInput) {
            this.expectInput = expectInput;
            return this;
        }

        public HolderBuilder<T> excludeIn(BinaryPredicate<T> excludeInput) {
            this.expectInput = excludeInput.negate();
            return this;
        }

        public HolderBuilder<T> expectOut(Predicate<T> expectOut) {
            this.expectOut = expectOut;
            return this;
        }

        Holders.Holder<T> buildHolder(Holders.SwapBroadcast<T> owner) {
            return new Holders.Holder<>(owner,
                    value
            );
        }
    }

    public static class ManagerBuilder {
        private Function<Holders.StreamManager<?>, AtomicBinaryEvent> activationListenerFun;
        private boolean mutableActivationListener;

        @SuppressWarnings("unchecked")
        public<S> ManagerBuilder withFixedFun(
                Function<Holders.StreamManager<S>, AtomicBinaryEvent> activationListenerFun
        ) {
            if (mutableActivationListener) throwException();
            this.activationListenerFun = coldHolder -> activationListenerFun.apply((Holders.StreamManager<S>) coldHolder);
            this.mutableActivationListener = false;
            return this;
        }

        void throwException() {
            throw new IllegalStateException("Only one at a time.");
        }

        public ManagerBuilder withMutable(boolean activationListener) {
            if (this.activationListenerFun != null) throwException();
            this.mutableActivationListener = activationListener;
            return this;
        }

        protected ActivationManager build(Holders.StreamManager<?> coldHolder, BooleanSupplier deactivation) {
            final AtomicBinaryEvent finalConsumer = activationListenerFun != null ? activationListenerFun.apply(coldHolder) : null;
            return new ActivationManager(finalConsumer, mutableActivationListener) {
                @Override
                protected boolean deactivationRequirements() {
                    return deactivation.getAsBoolean();
                }
            };
        }
    }

    public static class InputMethods<M, T> {
        final InputMethod.Type<M, T> type;

        public static<S, T> InputMethods<S, T> map(Function<T, S> map) {
            return new InputMethods<>(InputMethod.Type.map(map));
        }

        public static <S, T> InputMethods<S, T> update(BiFunction<S, T, S> update) {
            return new InputMethods<>(InputMethod.Type.update(update));
        }

        public static <T> InputMethods<T, T> identity() {
            return new InputMethods<>(InputMethod.Type.identity());
        }

        private InputMethods(InputMethod.Type<M, T> type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InputMethods<?, ?> that = (InputMethods<?, ?>) o;
            return that.type.equalTo(0, type);
        }

        @Override
        public int hashCode() {
            return type.hashAt(0);
        }
    }

    public static final class ReceptorBuilder<S, T> {
        final Holders.SwapBroadcast<T> broadcast;
        final InputMethods<T, S> inputMethods;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReceptorBuilder<?, ?> that = (ReceptorBuilder<?, ?>) o;
            return that.broadcast.equalTo(this.broadcast) && Objects.equals(inputMethods, that.inputMethods);
        }

        @Override
        public int hashCode() {
            return hashCodeOf(broadcast.hashAt(0), inputMethods.hashCode());
        }

        public static<T> ReceptorBuilder<T, T> exit(Consumer<? super T> consumer) {
            return new ReceptorBuilder<>(consumer, InputMethods.identity());
        }

        public static<S, T> ReceptorBuilder<S, T> exit(Consumer<? super T> consumer, Function<S, T> map) {
            return new ReceptorBuilder<>(consumer, InputMethods.map(map));
        }

        public static<S, T> ReceptorBuilder<S, T> exit(Consumer<? super T> consumer, BiFunction<T, S, T> update) {
            return new ReceptorBuilder<>(consumer, InputMethods.update(update));
        }

        public static<T> ReceptorBuilder<T, T> exit(BinaryConsumer<? super T> consumer) {
            return new ReceptorBuilder<>(consumer, InputMethods.identity());
        }

        public static<S, T> ReceptorBuilder<S, T> exit(BinaryConsumer<? super T> consumer, Function<S, T> map) {
            return new ReceptorBuilder<>(consumer, InputMethods.map(map));
        }

        public static<S, T> ReceptorBuilder<S, T> exit(BinaryConsumer<? super T> consumer, BiFunction<T, S, T> update) {
            return new ReceptorBuilder<>(consumer, InputMethods.update(update));
        }

        ReceptorBuilder(
                Consumer<? super T> consumer,
                InputMethods<T, S> inputMethods
        ) {
            this(Holders.SwapBroadcast.fromConsumer(consumer), inputMethods);
        }

        ReceptorBuilder(
                BinaryConsumer<? super T> consumer,
                InputMethods<T, S> inputMethods
        ) {
            this(Holders.SwapBroadcast.fromBinaryConsumer(consumer), inputMethods);
        }

        ReceptorBuilder(Holders.SwapBroadcast<T> broadcast, InputMethods<T, S> inputMethods) {
            this.broadcast = broadcast;
            this.inputMethods = inputMethods;
        }

        BasePath.Receptor<S> build(Consumer<Runnable> executor) {
            return build(executor, broadcast, inputMethods);
        }
        static<T, S> BasePath.Receptor<S> build(Consumer<Runnable> executor, Holders.SwapBroadcast<T> broadcast, InputMethods<T, S> inputMethods) {
            return BasePath.Receptor.withManagerInput(
                    Holders.StreamManager.getManagerFor(executor, broadcast),
                    inputMethods.type
            );
        }
        BasePath.Receptor<S> build() {
            return BasePath.Receptor.withManagerInput(
                    Holders.StreamManager.baseManager(broadcast),
                    inputMethods.type
            );
        }
        static <S, T> BasePath.Receptor<S> getReceptor(
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                Container<T> target,
                BinaryConsumer<T> consumer
        ) {
            return BasePath.Receptor.withManagerInput(
                    BinaryEventConsumers.getManagerFor(expectIn, target, consumer),
                    inputMethod.type
            );
        }
    }

    public static class BinaryEventConsumers {

        public static<T> AtomicBinaryEvent producerListener(
                BasePath<T> producer,
                Container<T> target,
                BinaryConsumer<T> consumer
        ) {
            return producerListener(producer,
                    InputMethods.identity(),
                    target,
                    consumer
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                InputMethods<T, S> inputMethod,
                Container<T> target,
                BinaryConsumer<T> consumer
        ) {
            return producerListener(producer,
                    inputMethod,
                    BinaryPredicate.always(true),
                    target,
                    consumer
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                BasePath.Receptor<S> receptor
        ) {
            return new Appointer<>(producer,
                    receptor
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                Container<T> target,
                BinaryConsumer<T> consumer
        ) {
            return new Appointer<>(producer,
                    ReceptorBuilder.getReceptor(inputMethod, expectIn, target, consumer)
            );
        }

        static <T> Holders.StreamManager<T> getManagerFor(BinaryPredicate<T> expectIn, Container<T> target, BinaryConsumer<? super T> consumer) {
            return Holders.StreamManager.getManagerFor(
                    new Holders.Holder<>(
                            Holders.SwapBroadcast.fromBinaryConsumer(consumer),
                            target.getRef()
                    ),
                    expectIn
            );
        }

        public static<T> AtomicBinaryEvent producerListener(
                BasePath<T> producer,
                BinaryConsumer<? super T> consumer
        ) {
            return new Appointer<>(producer,
                    BasePath.Receptor.withManagerInput(
                            Holders.StreamManager.baseManager(
                                    Holders.SwapBroadcast.fromBinaryConsumer(consumer)
                            ),
                            InputMethod.Type.identity()
                    )
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                BinaryConsumer<? super T> consumer,
                BiFunction<T, S, T> update
        ) {
            return producerListener(producer,
                                    consumer,
                            InputMethods.update(update)
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                BinaryConsumer<? super T> consumer,
                Function<S, T> map
        ) {
            return producerListener(producer,
                                    consumer,
                            InputMethods.map(map)
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                BinaryConsumer<? super T> onSwapped,
                InputMethods<T, S> inputMethod
        ) {
            return new Appointer<>(producer,
                    BasePath.Receptor.withManagerInput(
                            Holders.StreamManager.baseManager(
                                    Holders.SwapBroadcast.fromBinaryConsumer(onSwapped)
                            ),
                            inputMethod.type
                    )
            );
        }

        static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                Holders.StreamManager<T> consumer,
                InputMethod.Type<T, S> inputMethod
        ) {
            return new Appointer<>(producer, BasePath.Receptor.withManagerInput(
                    consumer,
                    inputMethod
            ));
        }

        static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                Holders.StreamManager<T> consumer,
                Function<S, T> map) {
            return producerListener(producer,
                    consumer,
                    InputMethod.Type.map(map)
            );
        }

        static<T> AtomicBinaryEvent producerListener(
                BasePath<T> producer,
                Holders.StreamManager<T> consumer
        ) {
            return producerListener(producer,
                            consumer,
                            InputMethod.Type.identity()

            );
        }

        static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                Holders.StreamManager<T> holder,
                BiFunction<T, S, T> update) {
            return producerListener(producer,
                            holder,
                            InputMethod.Type.update(update)

            );
        }
    }
}
