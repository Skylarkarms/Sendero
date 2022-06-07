package sendero;

import sendero.functions.Functions;
import sendero.interfaces.BinaryPredicate;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class Builders {

    public static<S> UnaryOperator<HolderBuilder<S>> excludeIn(BinaryPredicate<S> excludeInput) {
        return sHolderBuilder -> sHolderBuilder.excludeIn(excludeInput);
    }
    public static<S> UnaryOperator<HolderBuilder<S>> withInitial(S value) {
        return sHolderBuilder -> sHolderBuilder.withInitial(value);
    }
    public static <S> UnaryOperator<ManagerBuilder> withFixed(
            Function<Holders.StreamManager<S>, AtomicBinaryEventConsumer> activationListenerFun
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
        private AtomicReference<Immutable<T>> reference;
        Predicate<T> expectOut;
        BinaryPredicate<T> expectInput;

        private HolderBuilder() {
            expectInput = BinaryPredicate.always(true);
            expectOut = Functions.always(true);
            reference = new AtomicReference<>(Immutable.getNotSet());
        }

        public HolderBuilder<T> withInitial(T value) {
            reference = new AtomicReference<>(Immutable.forFirstValue(value));
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
            return new Holders.Holder<>(owner, reference);
        }
    }

    public static class ManagerBuilder {
        private Function<Holders.StreamManager<?>, AtomicBinaryEventConsumer> activationListenerFun;
        private boolean mutableActivationListener;

        @SuppressWarnings("unchecked")
        public<S> ManagerBuilder withFixedFun(
                Function<Holders.StreamManager<S>, AtomicBinaryEventConsumer> activationListenerFun
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
            final AtomicBinaryEventConsumer finalConsumer = activationListenerFun != null ? activationListenerFun.apply(coldHolder) : null;
            return new ActivationManager(finalConsumer, mutableActivationListener) {
                @Override
                protected boolean deactivationRequirements() {
                    return deactivation.getAsBoolean();
                }
            };
        }
    }
}
