package sendero;

import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class Builders {
    public static<S> UnaryOperator<HolderBuilder<S>> constraintIn(BinaryPredicate<S> constraintIn) {
        return sHolderBuilder -> sHolderBuilder.constraintIn(constraintIn);
    }
    static <T>HolderBuilder<T> getHolderBuild() {
        return new HolderBuilder<>();
    }
    static <T>HolderBuilder<T> getHolderBuild(UnaryOperator<Builders.HolderBuilder<T>> op) {
        return op.apply(getHolderBuild());
    }

    static ManagerBuilder getManagerBuild() {
        return new ManagerBuilder();
    }
    public static class HolderBuilder<T> {
        private AtomicReference<Pair.Immutables.Int<T>> reference;
        private Predicate<T> expectOut;
        private BinaryPredicate<T> expectInput;
        private UnaryOperator<T> map;

        private HolderBuilder() {
        }

        public HolderBuilder<T> withInitial(T value) {
            reference = new AtomicReference<>(new Pair.Immutables.Int<>(1, value));
            return this;
        }

        public HolderBuilder<T> constraintIn(Predicate<T> constraintInput) {
            this.expectInput = (next, prev) -> !constraintInput.test(next);
            return this;
        }

        public HolderBuilder<T> constraintOut(Predicate<T> constraintOutput) {
            this.expectOut = next -> !constraintOutput.test(next);
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

        public HolderBuilder<T> constraintIn(BinaryPredicate<T> constraintInput) {
            this.expectInput = constraintInput.negate();
            return this;
        }

        public HolderBuilder<T> expectOut(Predicate<T> expectOut) {
            this.expectOut = expectOut;
            return this;
        }

        public HolderBuilder<T> with(UnaryOperator<T> map) {
            this.map = map;
            return this;
        }

        Holders.DispatcherHolder<T>
        build(Dispatcher<T> dispatcher) {
            return new Holders.DispatcherHolder<T>(reference, map, expectInput, expectOut){
                @Override
                void coldDispatch(Pair.Immutables.Int<T> t) {
                    dispatcher.coldDispatch(t);
                }

                @Override
                void dispatch(long delay, Pair.Immutables.Int<T> t) {
                    dispatcher.dispatch(delay, t);
                }

                @Override
                protected void onSwapped(T prev, T next) {
                    dispatcher.onSwapped(prev, next);
                }
            };
        }
    }

    public static class ManagerBuilder {
        private AtomicBinaryEventConsumer activationListener;
        private Function<Holders.ColdHolder<?>, AtomicBinaryEventConsumer> activationListenerFun;
        private boolean mutableActivationListener;

        @SuppressWarnings("unchecked")
        public<S> ManagerBuilder withFixedFun(Function<Holders.ColdHolder<S>, AtomicBinaryEventConsumer> activationListenerFun) {
            if (mutableActivationListener) throwException();
            this.activationListenerFun = coldHolder -> activationListenerFun.apply((Holders.ColdHolder<S>) coldHolder);
            this.mutableActivationListener = false;
            return this;
        }

        void throwException() {
            throw new IllegalStateException("Only one at a time.");
        }

        public ManagerBuilder withMutable(boolean activationListener) {
            if (this.activationListener != null) throwException();
            this.mutableActivationListener = activationListener;
            return this;
        }

        protected ActivationManager build(BooleanSupplier deactivation) {
            return new ActivationManager(activationListener, mutableActivationListener) {
                @Override
                protected boolean deactivationRequirements() {
                    return deactivation.getAsBoolean();
                }
            };
        }

        protected ActivationManager build(Holders.ColdHolder<?> coldHolder, BooleanSupplier deactivation) {
            return activationListenerFun != null ? new ActivationManager(activationListenerFun.apply(coldHolder), mutableActivationListener) {
                @Override
                protected boolean deactivationRequirements() {
                    return deactivation.getAsBoolean();
                }
            } : build(deactivation);
        }
    }
}
