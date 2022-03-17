package sendero;

import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class Builders {
    public static <T>HolderBuilder<T> get(UnaryOperator<Builders.HolderBuilder<T>> op) {
        return op.apply(new HolderBuilder<>());
    }
    public static class HolderBuilder<T> {
        private AtomicReference<Pair.Immutables.Int<T>> reference;
        private Predicate<T> expectOutput;
        private UnaryOperator<T> map;

        private HolderBuilder() {
        }

        public HolderBuilder<T> withInitial(T value) {
            reference = new AtomicReference<>(new Pair.Immutables.Int<>(1, value));
            return this;
        }

        public HolderBuilder<T> expectOut(Predicate<T> expectOutput) {
            this.expectOutput = expectOutput;
            return this;
        }

        public HolderBuilder<T> with(UnaryOperator<T> map) {
            this.map = map;
            return this;
        }

        Holders.DispatcherHolder<T> build(Dispatcher<T> dispatcher) {
            return new Holders.DispatcherHolder<T>(reference, map, expectOutput){
                @Override
                protected void coldDispatch(Pair.Immutables.Int<T> t) {
                    dispatcher.coldDispatch(t);
                }

                @Override
                protected void dispatch(Pair.Immutables.Int<T> t) {
                    dispatcher.dispatch(t);
                }
            };
        }

    }

}
