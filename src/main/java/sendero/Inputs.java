package sendero;

import sendero.interfaces.BinaryPredicate;
import sendero.interfaces.ConsumerUpdater;
import sendero.interfaces.Updater;

import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static sendero.Holders.SwapBroadcast.HOT;

public class Inputs<T> {
    private final Holders.Holder<T> baseColdHolderBroadcaster;

    @Override
    public String toString() {
        return "Inputs{" +
                "\n baseColdHolderBroadcaster=" + baseColdHolderBroadcaster +
                "\n }";
    }

    private final BinaryPredicate<T> testIn;
    private static final Object INVALID = new Object();

    Inputs(Holders.BaseBroadcaster<T> broadcaster) {
        this(broadcaster.holder, broadcaster.expectInput);
//        broadcaster.updaterSet();
    }

    private Inputs(Holders.Holder<T> baseColdHolderBroadcaster, BinaryPredicate<T> testIn) {
        this.baseColdHolderBroadcaster = baseColdHolderBroadcaster;
        this.testIn = testIn;
    }

    public static<S> Consumer<S> getConsumer(Holders.BaseBroadcaster<S> broadcaster) {
        Consumer<S> res = new Inputs<S>(broadcaster).getConsumer();
        broadcaster.acceptorSet();
        return res;
    }

    public static<S> Updater<S> getUpdater(Holders.BaseBroadcaster<S> broadcaster) {
        Updater<S> res = new Inputs<S>(broadcaster).getUpdater();
        broadcaster.updaterSet();
        return res;
    }

    public static <S> ConsumerUpdater<S> getConsumerUpdater(Holders.BaseBroadcaster<S> broadcaster) {
        ConsumerUpdater<S> res = new Inputs<S>(broadcaster).getConsumerUpdater();
        broadcaster.acceptorSet();
        broadcaster.updaterSet();
        return res;
    }

    Consumer<T> getConsumer() {
        return new ConsumerImpl(baseColdHolderBroadcaster, testIn);
    }

    ConsumerUpdater<T> getConsumerUpdater() {
        return new ConsumerUpdaterImpl(baseColdHolderBroadcaster, testIn);
    }

    Updater<T> getUpdater() {
        return new UpdaterImpl(baseColdHolderBroadcaster, testIn);
    }

    private Immutable<T> refGet() {
        return baseColdHolderBroadcaster.getSnapshot();
    }

    private boolean hotCompareAndSet(Immutable<T> prev, Immutable<T> next) {
        return baseColdHolderBroadcaster.compareAndSet(prev, next, HOT);
    }

    private boolean compareAndSet(Immutable<T> prev, Immutable<T> next, long delay) {
        return baseColdHolderBroadcaster.compareAndSet(prev, next, delay);
    }

    class ConsumerUpdaterImpl extends Inputs<T> implements ConsumerUpdater<T> {

        private final Consumer<T> consumer;
        private final Updater<T> updater;

        private ConsumerUpdaterImpl(Holders.Holder<T> baseColdHolderBroadcaster, BinaryPredicate<T> testIn) {
            super(baseColdHolderBroadcaster, testIn);
            consumer = getConsumer();
            updater = getUpdater();
        }

        @Override
        public void accept(T t) {
            consumer.accept(t);
        }

        @Override
        public T updateAndGet(UnaryOperator<T> update) {
            return updater.updateAndGet(update);
        }

        @Override
        public T getAndUpdate(UnaryOperator<T> update) {
            return updater.getAndUpdate(update);
        }

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            updater.update(delay, update);
        }
    }

    class ConsumerImpl extends Inputs<T> implements Consumer<T> {

        private final Consumer<T> coreConsumer;

        private Consumer<T> build(BinaryPredicate<T> testIn) {
            return testIn.alwaysTrue() ?
                    t -> {
                        Immutable<T> prev = null, next;
                        while (prev != (prev = refGet())) {
                            next = prev.newValue(t);
                            if (hotCompareAndSet(prev, next)) {
                                break;
                            }
                        }

                    }
                    :
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
                    };
        }

        private ConsumerImpl(Holders.Holder<T> baseColdHolderBroadcaster, BinaryPredicate<T> testIn) {
            super(baseColdHolderBroadcaster, testIn);
            coreConsumer = build(testIn);
        }

        @Override
        public void accept(T t) {
            coreConsumer.accept(t);
        }
    }

    class UpdaterImpl extends Inputs<T> implements Updater<T> {

        private final UnaryOperator<UnaryOperator<T>> lazyProcess;

        private UpdaterImpl(Holders.Holder<T> baseColdHolderBroadcaster, BinaryPredicate<T> testIn) {
            super(baseColdHolderBroadcaster, testIn);
            lazyProcess = build(testIn);
        }

        @SuppressWarnings("unchecked")
        private UnaryOperator<UnaryOperator<T>> build(BinaryPredicate<T> testIn) {
            return testIn.alwaysTrue() ?
                    update -> currentValue -> {
                        //Not valid if same instance
                        try {
                            T updated = update.apply(currentValue);
                            return updated == currentValue ? (T) INVALID : updated;
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    }
                    :
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
                    };

        }

        private final BinaryOperator<T> getNext = (prev, next) -> next, getPrev = (prev, next) -> prev;

        @Override
        public T updateAndGet(UnaryOperator<T> update) {
            return lazyCASProcessAndThen(update, getNext);
        }

        @Override
        public T getAndUpdate(UnaryOperator<T> update) {
            return lazyCASProcessAndThen(update, getPrev);
        }

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            lazyCASProcess(delay, update);
        }

        private void lazyCASProcess(long delay, UnaryOperator<T> update) {
            lazyCASAccept(delay, lazyProcess.apply(update));
        }

        private T lazyCASProcessAndThen(UnaryOperator<T> update, BinaryOperator<T> prevNext) {
            return lazyCASAcceptAndThen(lazyProcess.apply(update), prevNext);
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
        /**
         * @param prevNext: the value to return:<p>
         *    * arg 1 = prev <p>
         *    * arg 2 = next
         *
         * */
        private T lazyCASAcceptAndThen(UnaryOperator<T> t, BinaryOperator<T> prevNext) {
            Immutable<T> prev = null, next;
            T prevVal, newVal;
            while (prev != (prev = refGet())) {
                prevVal = prev.get();
                newVal = t.apply(prevVal);
                if (newVal != INVALID) {
                    next = prev.newValue(newVal);
                    if (compareAndSet(prev, next, Holders.SwapBroadcast.HOT)) return prevNext.apply(prevVal, newVal);
                }
            }
            return null;
        }
    }
}
