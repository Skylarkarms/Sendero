package sendero;

import sendero.functions.Functions;
import sendero.interfaces.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

public final class Builders {

    public static<S> UnaryOperator<HolderBuilder<S>> excludeIn(BinaryPredicate<S> excludeInput) {
        return sHolderBuilder -> sHolderBuilder.excludeIn(excludeInput);
    }
    public static<S> UnaryOperator<HolderBuilder<S>> excludeOut(Predicate<S> excludeOut) {
        return sHolderBuilder -> sHolderBuilder.excludeOut(excludeOut);
    }
    public static<S> UnaryOperator<HolderBuilder<S>> withInitial(S value) {
        return sHolderBuilder -> sHolderBuilder.withInitial(value);
    }

    static <T> HolderBuilder<T> getHolderBuild2() {
        return new HolderBuilder<>();
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

        public HolderBuilder<T> withContainer(Accessor<T> accessor) {
            this.value = accessor.getRef();
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
        private final Function<Holders.BaseBroadcaster<?>, AtomicBinaryEvent> activationListenerBroadcast;
        private final boolean mutableActivationListener;

        @SuppressWarnings("unchecked")
        public static<S> ManagerBuilder withFixed(
                Function<Holders.StreamManager<S>, AtomicBinaryEvent> activationListenerFun

        ) {
            return new ManagerBuilder(
                    coldHolder -> activationListenerFun.apply((Holders.StreamManager<S>) coldHolder.getManager()),
                    false);
        }

        public static ManagerBuilder withEvent(
                AtomicBinaryEvent event

        ) {
            return new ManagerBuilder(
                    baseBroadcaster -> event,
                    false);
        }

        public static ManagerBuilder isMutable(
                boolean isMutable

        ) {
            return new ManagerBuilder(
                    null,
                    isMutable);
        }

        public static ManagerBuilder mutable() {
            return new ManagerBuilder(
                    null,
                    true);
        }

        @SuppressWarnings("unchecked")
        public static<S> ManagerBuilder onActive(
                Function<ConsumerUpdater<S>, ActivationListener> activationListenerFun

        ) {
            return new ManagerBuilder(
                    broadcaster -> BinaryEventConsumers.producerListener(
                            activationListenerFun.apply(
                                    (ConsumerUpdater<S>) Inputs.getConsumerUpdater(broadcaster)
                            )
                    ),
                    false);
        }

        ManagerBuilder(Function<Holders.BaseBroadcaster<?>, AtomicBinaryEvent> activationListenerBroadcast, boolean mutableActivationListener) {
            this.activationListenerBroadcast = activationListenerBroadcast;
            this.mutableActivationListener = mutableActivationListener;
        }

        private boolean broadcastIsSet() {
            return this.activationListenerBroadcast != null;
        }

        private void inferThrow() {
            if (alreadySet()) throwException();
        }

        private boolean alreadySet() {
            return mutableActivationListener && broadcastIsSet();
        }

        void throwException() {
            throw new IllegalStateException("Only one at a time.");
        }

        protected ActivationManager build(Holders.BaseBroadcaster<?> broadcaster, BooleanSupplier deactivation) {
            final AtomicBinaryEvent finalConsumer = activationListenerBroadcast != null ? activationListenerBroadcast.apply(broadcaster) : null;
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

        public boolean equalTo(Object function) {
            return type.equalTo(0, function);
        }

        public static<S, T> InputMethods<S, T> map(Function<T, S> map) {
            return new InputMethods<>(InputMethod.Type.map(map));
        }

        public static <S, T> InputMethods<S, T> update(BiFunction<S, T, S> update) {
            return new InputMethods<>(InputMethod.Type.update(update));
        }

        private static final InputMethods<?, ?> identity = new InputMethods<>(InputMethod.Type.identity());

        boolean isIdentity() {
            return this == identity;
        }
        @SuppressWarnings("unchecked")
        public static <T> InputMethods<T, T> identity() {
            return (InputMethods<T, T>) identity;
        }

        InputMethods(InputMethod.Type<M, T> type) {
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

        public boolean equalTo(Object consumer, Object function) {
            if (consumer == null || function == null) return false;
            return broadcast.equalTo(0, consumer)
                    && inputMethods.equalTo(function);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReceptorBuilder<?, ?> that = (ReceptorBuilder<?, ?>) o;
            return that.broadcast.equalTo(this.broadcast) && Objects.equals(inputMethods, that.inputMethods);
        }

        @Override
        public int hashCode() {
            return SynthEqual.hashCodeOf(broadcast.hashAt(0), inputMethods.hashCode());
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

        public AtomicBinaryEvent asEvent(
                Consumer<Runnable> executor,
                BasePath<S> producer
        ) {
            return build(executor).toBinaryEvent(producer);
        }
        BasePath.Receptor<S> build(Consumer<Runnable> executor) {
            return build(executor, broadcast, inputMethods);
        }
        static<T, S> BasePath.Receptor<S> build(
                Consumer<Runnable> executor,
                Holders.SwapBroadcast<T> broadcast,
                InputMethods<T, S> inputMethods
        ) {
            return BasePath.Receptor.withManagerInput(
                    Holders.StreamManager.getManagerFor(executor, broadcast),
                    inputMethods.type
            );
        }

        static <S, T> BasePath.Receptor<S> getReceptor(
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                Accessor<T> target,
                BinaryConsumer<T> consumer
        ) {
            return BasePath.Receptor.withManagerInput(
                    BinaryEventConsumers.getManagerFor(expectIn, target, consumer),
                    inputMethod.type
            );
        }
        static <S, T> BasePath.Receptor<S> getReceptor(
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                BinaryConsumer<T> consumer
        ) {
            return BasePath.Receptor.withManagerInput(
                    BinaryEventConsumers.getManagerFor(expectIn, consumer),
                    inputMethod.type
            );
        }
        static <S, T> BasePath.Receptor<S> getReceptor(
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                Consumer<? super T> consumer
        ) {
            return BasePath.Receptor.withManagerInput(
                    BinaryEventConsumers.getManagerFor(expectIn, consumer),
                    inputMethod.type
            );
        }
        static <S, T> BasePath.Receptor<S> getReceptor(
                Consumer<Runnable> executor,
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                Consumer<? super T> consumer
        ) {
            return BasePath.Receptor.withManagerInput(
                    BinaryEventConsumers.getManagerFor(executor, expectIn, consumer),
                    inputMethod.type
            );
        }
        static <S, T> BasePath.Receptor<S> getReceptor(
                Consumer<Runnable> executor,
                InputMethods<T, S> inputMethod,
                Consumer<? super T> consumer
        ) {
            return BasePath.Receptor.withManagerInput(
                    BinaryEventConsumers.getManagerFor(executor, consumer),
                    inputMethod.type
            );
        }
    }

    public static class BinaryEventConsumers {

        public static<T> AtomicBinaryEvent producerListener(
                BasePath<T> producer,
                Accessor<T> target,
                BinaryConsumer<T> consumer
        ) {
            return producerListener(producer,
                    InputMethods.identity(),
                    target,
                    consumer
            );
        }

        static<T> AtomicBinaryEvent producerListener(
                ActivationListener listener
        ) {
            return AtomicBinaryEventConsumer.base(listener);
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                InputMethods<T, S> inputMethod,
                Accessor<T> target,
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
                InputMethods<T, S> inputMethod,
                Consumer<? super T> consumer
        ) {
            return producerListener(
                    producer,
                    inputMethod,
                    BinaryPredicate.always(true),
                    consumer
            );
        }

        static<S, T> AtomicBinaryEvent producerListener(
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
                Accessor<T> target,
                BinaryConsumer<T> consumer
        ) {
            return new Appointer<>(producer,
                    ReceptorBuilder.getReceptor(inputMethod, expectIn, target, consumer)
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                BinaryConsumer<T> consumer
        ) {
            return new Appointer<>(producer,
                    ReceptorBuilder.getReceptor(inputMethod, expectIn, consumer)
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                Consumer<Runnable> executor,
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                Consumer<? super T> consumer
        ) {
            return new Appointer<>(producer,
                    ReceptorBuilder.getReceptor(executor, inputMethod, expectIn, consumer)
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                Consumer<Runnable> executor,
                InputMethods<T, S> inputMethod,
                Consumer<? super T> consumer
        ) {
            return new Appointer<>(producer,
                    ReceptorBuilder.getReceptor(executor, inputMethod, consumer)
            );
        }

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                InputMethods<T, S> inputMethod,
                BinaryPredicate<T> expectIn,
                Consumer<? super T> consumer
        ) {
            return new Appointer<>(producer,
                    ReceptorBuilder.getReceptor(inputMethod, expectIn, consumer)
            );
        }

        static <T> Holders.StreamManager<T> getManagerFor(
                BinaryPredicate<T> expectIn,
                Accessor<T> target,
                BinaryConsumer<? super T> consumer
        ) {
            return Holders.StreamManager.getManagerFor(
                    new Holders.Holder<>(
                            Holders.SwapBroadcast.fromBinaryConsumer(consumer),
                            target.getRef()
                    ),
                    expectIn
            );
        }

        static <T> Holders.StreamManager<T> getManagerFor(
                BinaryPredicate<T> expectIn,
                BinaryConsumer<? super T> consumer
        ) {
            return Holders.StreamManager.getManagerFor(
                    new Holders.Holder<>(
                            Holders.SwapBroadcast.fromBinaryConsumer(consumer)
                    ),
                    expectIn
            );
        }

        static <T> Holders.StreamManager<T> getManagerFor(
                BinaryPredicate<T> expectIn,
                Consumer<? super T> consumer
        ) {
            return Holders.StreamManager.getManagerFor(
                    new Holders.Holder<>(
                            Holders.SwapBroadcast.fromConsumer(consumer)
                    ),
                    expectIn
            );
        }

        static <T> Holders.StreamManager<T> getManagerFor(
                Consumer<Runnable> executor,
                BinaryPredicate<T> expectIn,
                Consumer<? super T> consumer
        ) {
            return Holders.StreamManager.getManagerFor(
                    executor,
                    new Holders.Holder<>(
                            Holders.SwapBroadcast.fromConsumer(consumer)
                    ),
                    expectIn
            );
        }

        static <T> Holders.StreamManager<T> getManagerFor(
                Consumer<Runnable> executor,
                Consumer<? super T> consumer
        ) {
            return Holders.StreamManager.getManagerFor(
                    executor,
                    new Holders.Holder<>(
                            Holders.SwapBroadcast.fromConsumer(consumer)
                    )
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
                            )
                    )
            );
        }

        public static<T> AtomicBinaryEvent producerListener(
                BasePath<T> producer,
                Consumer<Runnable> executor,
                BinaryConsumer<? super T> consumer
        ) {
            return new Appointer<>(producer,
                    BasePath.Receptor.withManagerInput(
                            Holders.StreamManager.getManagerFor(
                                    executor,
                                    Holders.SwapBroadcast.fromBinaryConsumer(consumer)
                            )
                    )
            );
        }

        public static<T> AtomicBinaryEvent producerListener(
                BasePath<T> producer,
                Consumer<Runnable> executor,
                Consumer<? super T> consumer
        ) {
            return new Appointer<>(producer,
                    BasePath.Receptor.withManagerInput(
                            Holders.StreamManager.getManagerFor(
                                    executor,
                                    Holders.SwapBroadcast.fromConsumer(consumer)
                            )
                    )
            );
        }

        public static<T> AtomicBinaryEvent producerListener(
                BasePath<T> producer,
                Consumer<? super T> consumer
        ) {
            return new Appointer<>(producer,
                    BasePath.Receptor.withManagerInput(
                            Holders.StreamManager.baseManager(
                                    Holders.SwapBroadcast.fromConsumer(consumer)
                            )
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

        public static<S, T> AtomicBinaryEvent producerListener(
                BasePath<S> producer,
                Holders.BaseBroadcaster<T> consumer,
                InputMethods<T, S> inputMethod
        ) {
            return new Appointer<>(
                    producer,
                    BasePath.Receptor.withManagerInput(consumer.getManager(), inputMethod.type)
            );
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
