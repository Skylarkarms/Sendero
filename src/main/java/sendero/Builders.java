package sendero;

import sendero.interfaces.BooleanConsumer;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class Builders {
    public static <T>HolderBuilder<T> getHolderBuild(UnaryOperator<Builders.HolderBuilder<T>> op) {
        return op.apply(new HolderBuilder<>());
    }

    public static ManagerBuilder getManagerBuild() {
        return new ManagerBuilder();
    }
    public static class HolderBuilder<T> {
        private AtomicReference<Pair.Immutables.Int<T>> reference;
        private Predicate<T> expectInput, expectOut;
        private UnaryOperator<T> map;

        private HolderBuilder() {
        }

        public HolderBuilder<T> withInitial(T value) {
            reference = new AtomicReference<>(new Pair.Immutables.Int<>(1, value));
            return this;
        }

        public HolderBuilder<T> expectIn(Predicate<T> expectInput) {
            this.expectInput = expectInput;
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

        Holders.DispatcherHolder<T> build(Dispatcher<T> dispatcher) {
            return new Holders.DispatcherHolder<T>(reference, map, expectInput, expectOut){
                @Override
                protected void coldDispatch(Pair.Immutables.Int<T> t) {
                    dispatcher.coldDispatch(t);
                }

                @Override
                protected void dispatch(long delay, Pair.Immutables.Int<T> t) {
                    dispatcher.dispatch(delay, t);
                }
            };
        }

    }

    public static class ManagerBuilder {
        private BooleanConsumer activationListener;
        private boolean mutableActivationListener;

        public ManagerBuilder withFixed(BooleanConsumer activationListener) {
            if (mutableActivationListener) throwException();
            this.activationListener = activationListener;
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
    }


}
