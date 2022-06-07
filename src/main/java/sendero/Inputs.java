package sendero;

import sendero.interfaces.BinaryPredicate;
import sendero.interfaces.ConsumerUpdater;
import sendero.interfaces.Updater;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static sendero.Holders.SwapBroadcast.HOT;
import static sendero.functions.Functions.alwaysTrue;

public class Inputs<T> {
    private final Holders.Holder<T> baseColdHolder;
    private final BinaryPredicate<T> testIn;
    private static final Object INVALID = new Object();

    Inputs(Holders.BaseBroadcaster<T> broadcaster) {
        this(broadcaster.coldHolder, broadcaster.expectInput);
    }

    private Inputs(Holders.Holder<T> baseColdHolder, BinaryPredicate<T> testIn) {
        this.baseColdHolder = baseColdHolder;
        this.testIn = testIn;
    }

    public static<S> Consumer<S> getConsumer(Holders.BaseBroadcaster<S> broadcaster) {
        return new Inputs<S>(broadcaster).getConsumer();
    }

    public static<S> Updater<S> getUpdater(Holders.BaseBroadcaster<S> broadcaster) {
        return new Inputs<S>(broadcaster).getUpdater();
    }

    public static <S> ConsumerUpdater<S> getConsumerUpdater(Holders.BaseBroadcaster<S> broadcaster) {
        return new Inputs<S>(broadcaster).getConsumerUpdater();
    }

    Consumer<T> getConsumer() {
        return new ConsumerImpl(baseColdHolder, testIn);
    }

    ConsumerUpdater<T> getConsumerUpdater() {
        return new ConsumerUpdaterImpl(baseColdHolder, testIn);
    }

    Updater<T> getUpdater() {
        return new UpdaterImpl(baseColdHolder, testIn);
    }

    private Immutable<T> refGet() {
        return baseColdHolder.getSnapshot();
    }

    private boolean hotCompareAndSet(Immutable<T> prev, Immutable<T> next) {
        return baseColdHolder.compareAndSet(prev, next, HOT);
    }

    private boolean compareAndSet(Immutable<T> prev, Immutable<T> next, long delay) {
        return baseColdHolder.compareAndSet(prev, next, delay);
    }

    class ConsumerUpdaterImpl extends Inputs<T> implements ConsumerUpdater<T> {

        private final Consumer<T> consumer;
        private final Updater<T> updater;

        private ConsumerUpdaterImpl(Holders.Holder<T> baseColdHolder, BinaryPredicate<T> testIn) {
            super(baseColdHolder, testIn);
            consumer = getConsumer();
            updater = getUpdater();
        }

        @Override
        public void accept(T t) {
            consumer.accept(t);
        }

        @Override
        public void update(UnaryOperator<T> update) {
            updater.update(update);
        }

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            updater.update(delay, update);
        }
    }

    class ConsumerImpl extends Inputs<T> implements Consumer<T> {

        private final Consumer<T> coreConsumer;

        private Consumer<T> build(BinaryPredicate<T> testIn) {
            return testIn != alwaysTrue ?
                    t -> {
                        Immutable<T> prev = null, next;
                        while (prev != (prev = refGet())) {
                            if (testIn.test(t, prev.get())) {
                                next = prev.newValue(t);
                                if (hotCompareAndSet(prev, next)) {
                                    break;
                                }
                            } else return;
                        }
                    }
                    :
                    t -> {
                        Immutable<T> prev = null, next;
                        while (prev != (prev = refGet())) {
                                next = prev.newValue(t);
                                if (hotCompareAndSet(prev, next)) {
                                    break;
                                }
                        }

                    };
        }

        private ConsumerImpl(Holders.Holder<T> baseColdHolder, BinaryPredicate<T> testIn) {
            super(baseColdHolder, testIn);
            coreConsumer = build(testIn);
        }

        @Override
        public void accept(T t) {
            coreConsumer.accept(t);
        }
    }

    class UpdaterImpl extends Inputs<T> implements Updater<T> {

        private final UnaryOperator<UnaryOperator<T>> lazyProcess;

        private UpdaterImpl(Holders.Holder<T> baseColdHolder, BinaryPredicate<T> testIn) {
            super(baseColdHolder, testIn);
            lazyProcess = build(testIn);
        }

        @SuppressWarnings("unchecked")
        private UnaryOperator<UnaryOperator<T>> build(BinaryPredicate<T> testIn) {
            return testIn != alwaysTrue ?
                    update -> currentValue -> {
                        //Not valid if same instance
                        try {
                            T updated;
                            updated = update.apply(currentValue);
                            return updated == currentValue || !testIn.test(updated, currentValue) ? (T) INVALID : updated;
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    }
                    :
                    update -> currentValue -> {
                        //Not valid if same instance
                        try {
                            T updated = update.apply(currentValue);
                            return updated == currentValue ? (T) INVALID : updated;
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    };
        }

        @Override
        public void update(UnaryOperator<T> update) {
            lazyCASProcess(HOT ,update);
        }

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            lazyCASProcess(delay, update);
        }

        private void lazyCASProcess(long delay, UnaryOperator<T> update) {
            lazyCASAccept(delay, lazyProcess.apply(update));
        }

        private void lazyCASAccept(long delay, UnaryOperator<T> t) {
            Immutable<T> prev = null, next;
            T prevVal, newVal;
            while (prev != (prev = refGet())) {
                prevVal = prev.get();
                newVal = t.apply(prevVal);
                if (newVal != INVALID) {
                    next = prev.newValue(newVal);
                    if (compareAndSet(prev, next, delay)) break;
                }
            }
        }
    }
}
