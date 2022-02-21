package sendero.functions;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Consumers {
    public static <O, R> Transform<R> transform(
            Function<R, O> function, Consumer<? super O> exit
    ) {
        return new Transform<R>(function, exit);
    }
    public static <R> Predicative<R> expect(
            Predicate<R> expect, Consumer<? super R> exit
    ) {
        return new Predicative<>(expect, exit);
    }
    public static class BaseConsumer<R> implements Consumer<R> {
        private final Consumer<R> local;

        protected BaseConsumer(Consumer<R> local) {
            this.local = local;
        }

        @Override
        public void accept(R r) {
            local.accept(r);
        }
    }
    /**O = original, R = result*/
    public static class Transform<R> extends BaseConsumer<R> {
        private<O> Transform(Function<R, O> function, Consumer<? super O> exit) {
            super(
                    r -> exit.accept(function.apply(r))
            );
        }
    }

    public static class Predicative<R> extends BaseConsumer<R> {
        private Predicative(Predicate<R> expect, Consumer<? super R> exit) {
            super(r -> {
                if (expect.test(r)) exit.accept(r);
            });
        }
    }
}
