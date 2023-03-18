package sendero.functions;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Consumers {
    public static <O, R> Mapper<R> map(
            Function<R, O> function, Consumer<? super O> exit
    ) {
        return new Mapper<>(function, exit);
    }
    public static <R> Tester<R> test(
            Predicate<R> expect, Consumer<? super R> exit
    ) {
        return new Tester<>(expect, exit);
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
    public static class Mapper<R> extends BaseConsumer<R> {
        private<O> Mapper(Function<R, O> function, Consumer<? super O> exit) {
            super(
                    r -> exit.accept(function.apply(r))
            );
        }
    }

    public static class Tester<R> extends BaseConsumer<R> {
        private Tester(Predicate<R> expect, Consumer<? super R> exit) {
            super(r -> {
                if (expect.test(r)) exit.accept(r);
            });
        }
    }
}
