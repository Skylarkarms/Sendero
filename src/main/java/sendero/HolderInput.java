package sendero;

import sendero.interfaces.ConsumerUpdater;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class HolderInput<T> {
    final Holders.BaseTestDispatcher<T> baseTestDispatcher;
    private AtomicReference<Pair.Immutables.Int<T>> reference() {
        return baseTestDispatcher.reference;
    }
    Pair.Immutables.Int<T> refGet() {
        return reference().get();
    }

    HolderInput(Holders.BaseTestDispatcher<T> baseTestDispatcher) {
        this.baseTestDispatcher = baseTestDispatcher;
    }

    boolean compareAndSet(Pair.Immutables.Int<T> expect, Pair.Immutables.Int<T> set) {
        return reference().compareAndSet(expect, set);
    }
    boolean testIn(T next, T prev) {
        return baseTestDispatcher.testIn(next, prev);
    }
    void hotSwapped(T prev, Pair.Immutables.Int<T> next, long delay) {
        baseTestDispatcher.hotSwapped(prev, next, delay);
    }
    Consumer<T> getAcceptor() {
        return new Acceptor<>(baseTestDispatcher);
    }
    sendero.interfaces.Updater<T> getUpdater() {
        return new Updater<>(baseTestDispatcher);
    }
    public static<T> ConsumerUpdater<T> getConsumerUpdater(Holders.BaseTestDispatcher<T> baseTestDispatcher) {
        return new ConsumerUpdaterImpl<>(baseTestDispatcher);
    }
    public static class Acceptor<T> extends HolderInput<T> implements Consumer<T> {

        public Acceptor(BasePath<T> basePath) {
            this(basePath.baseTestDispatcher);
        }

        Acceptor(Holders.BaseTestDispatcher<T> baseTestDispatcher) {
            super(baseTestDispatcher);
        }

        @Override
        public void accept(T t) {
            CASAccept(t);
        }

        private void CASAccept(T nextT) {
            Pair.Immutables.Int<T> prev = null, next;
            while (prev != (prev = refGet())) {
                if (testIn(nextT, prev.getValue())) {
                    next = new Pair.Immutables.Int<>(prev.getInt() + 1, nextT);
                    if (compareAndSet(prev, next)) {
                        hotSwapped(prev.getValue(), next, 0);
                        break;
                    }
                } else return;
            }
        }

    }
    public static class Updater<T> extends HolderInput<T> implements sendero.interfaces.Updater<T> {
        private final T invalid;

        public Updater(BasePath<T> basePath) {
            this(basePath.baseTestDispatcher);
        }

        Updater(Holders.BaseTestDispatcher<T> baseTestDispatcher) {
            super(baseTestDispatcher);
            invalid = baseTestDispatcher.INVALID;
        }

        @Override
        public void update(UnaryOperator<T> update) {
            lazyCASProcess(0 ,update);
        }

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            lazyCASProcess(delay, update);
        }

        private void lazyCASProcess(long delay, UnaryOperator<T> update) {
            lazyCASAccept(delay, lazyProcess(update));
        }

        private void lazyCASAccept(long delay, UnaryOperator<T> t) {
            Pair.Immutables.Int<T> prev = null, next;
            T prevVal, newVal;
            while (prev != (prev = refGet())) {
                prevVal = prev.getValue();
                newVal = t.apply(prevVal);
                if (newVal != invalid) {
                    next = new Pair.Immutables.Int<>(prev.getInt() + 1, newVal);
                    if (compareAndSet(prev, next)) {
                        hotSwapped(prevVal, next, delay);
                        break;
                    }
                }
            }
        }

        private UnaryOperator<T> lazyProcess(UnaryOperator<T> update) {
            return currentValue -> {
                //Not valid if same instance
                try {
                    T nulledInvalid = currentValue == invalid ? null : currentValue;
                    T updated;
                    updated = update.apply(nulledInvalid);
                    if (updated == nulledInvalid) {
                        return invalid;
                    }
                    return testIn(updated, nulledInvalid) ? updated : invalid;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            };
        }
    }

    static class ConsumerUpdaterImpl<T> extends HolderInput<T> implements ConsumerUpdater<T> {
        private final sendero.interfaces.Updater<T> updater;
        private final Consumer<T> acceptor;
        public ConsumerUpdaterImpl(BasePath<T> basePath) {
            this(basePath.baseTestDispatcher);
        }

        ConsumerUpdaterImpl(Holders.BaseTestDispatcher<T> baseTestDispatcher) {
            super(baseTestDispatcher);
            updater = getUpdater();
            acceptor = getAcceptor();
        }

        @Override
        public void accept(T t) {
            acceptor.accept(t);
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
}
