package sendero;

import sendero.functions.Functions;
import sendero.interfaces.BinaryConsumer;
import sendero.interfaces.BinaryPredicate;

import java.util.function.*;

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
        Immutable<T> value = Immutable.getNotSet();
        Predicate<T> expectOut;
        BinaryPredicate<T> expectInput;

        private HolderBuilder() {
            expectInput = BinaryPredicate.always(true);
            expectOut = Functions.always(true);
        }

        public HolderBuilder<T> withInitial(T value) {
            this.value = Immutable.forFirstValue(value);
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
            return new Holders.Holder<>(owner, value);
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
    }

    public static class BinaryEventConsumers {

        public static<T> AtomicBinaryEventConsumer producerListener(
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

        public static<S, T> AtomicBinaryEventConsumer producerListener(
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

        public static<S, T> AtomicBinaryEventConsumer producerListener(
                BasePath<S> producer,
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                Container<T> target,
                BinaryConsumer<T> consumer
        ) {
            return new Appointer<>(producer,
                    BasePath.Receptor.withManagerInput(
                            Holders.StreamManager.getManagerFor(
                                    new Holders.Holder<>(
                                            (prev, next, delay) -> consumer.accept(prev, next.get()),
                                            target.getRef()
                                    ),
                                    expectIn
                            ),
                            inputMethod.type
                    )
            );
        }

        public static<T> AtomicBinaryEventConsumer producerListener(
                BasePath<T> producer,
                BinaryConsumer<T> consumer
        ) {
            return new Appointer<>(producer,
                    BasePath.Receptor.withManagerInput(
                            Holders.StreamManager.baseManager(
                                    consumer
                            ),
                            InputMethod.Type.identity()
                    )
            );
        }

        public static<S, T> AtomicBinaryEventConsumer producerListener(
                BasePath<S> producer,
                BinaryConsumer<T> consumer,
                BiFunction<T, S, T> update
        ) {
            return producerListener(producer,
                                    consumer,
                            InputMethods.update(update)
            );
        }

        public static<S, T> AtomicBinaryEventConsumer producerListener(
                BasePath<S> producer,
                BinaryConsumer<T> consumer,
                Function<S, T> map
        ) {
            return producerListener(producer,
                                    consumer,
                            InputMethods.map(map)
            );
        }

        public static<S, T> AtomicBinaryEventConsumer producerListener(
                BasePath<S> producer,
                BinaryConsumer<T> onSwapped,
                Builders.InputMethods<T, S> inputMethod
        ) {
            return new Appointer<>(producer,
                    BasePath.Receptor.withManagerInput(
                            Holders.StreamManager.baseManager(
                                    onSwapped
                            ),
                            inputMethod.type
                    )
            );
        }

        static<S, T> AtomicBinaryEvent producerConnector(
                BasePath<S> producer,
                Holders.StreamManager<T> consumer,
                InputMethod.Type<T, S> inputMethod
        ) {
            return new Appointer<>(producer, BasePath.Receptor.withManagerInput(
                    consumer,
                    inputMethod
            ));
        }

        static<S, T> AtomicBinaryEvent producerConnector(
                BasePath<S> producer,
                Holders.StreamManager<T> consumer,
                Function<S, T> map) {
            return producerConnector(producer,
                    consumer,
                    InputMethod.Type.map(map)
            );
        }

        static<T> AtomicBinaryEvent producerConnector(
                BasePath<T> producer,
                Holders.StreamManager<T> consumer
        ) {
            return producerConnector(producer,
                            consumer,
                            InputMethod.Type.identity()

            );
        }

        static<S, T> AtomicBinaryEvent producerHolderConnector(
                BasePath<S> producer,
                Holders.StreamManager<T> holder,
                BiFunction<T, S, T> update) {
            return producerConnector(producer,
                            holder,
                            InputMethod.Type.update(update)

            );
        }
    }
}
