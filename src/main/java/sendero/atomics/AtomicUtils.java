package sendero.atomics;

import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class AtomicUtils {
    public static final class WitnessAtomicReference<T> implements Supplier<T> {
        private final AtomicReference<T> ref;

        public WitnessAtomicReference(T value) {
            this.ref = new AtomicReference<>(value);
        }

        public WitnessAtomicReference() {
            this.ref = new AtomicReference<>();
        }

        @Override
        public T get() {
            return ref.get();
        }

        public T getAndSet(T newVal) {
            return ref.getAndSet(newVal);
        }

        public T getAndUpdate(UnaryOperator<T> operator) {
            return ref.getAndUpdate(operator);
        }

        public boolean expectAndSet(Predicate<T> expect, T next) {
            T prev = ref.get();
            for (;;) {
                if (expect.test(prev) && ref.compareAndSet(prev, next)) return true;
                if (prev == (prev = ref.get())) return false;
            }
        }

        public boolean trySet(Predicate<T> setIf, T next, Predicate<T> retryIf) {
            T prev = ref.get();
            boolean first = true;
            while (first || retryIf.test(prev = ref.get())) {
                if (setIf.test(prev) && ref.compareAndSet(prev, next)) return true;
                first = false;
            }
            return false;
        }

        /**Will retry if:
         *  <li> Contention met && retryIf test == true
         * <br>
         * <br>
         *@return next == null if:
         * <li> A) updateIf == false && no contention met
         * <li> B) Contention met && retryIf == false
         * */
        public Witness<T> compliantCAS(
                Predicate<T> updateIf,
                UnaryOperator<T> next,
                Predicate<T> retryIf) {
            T prev = ref.get(), nextT;
            boolean update, retry = true;
            for (;retry;retry = retryIf.test(prev)) {
                update = updateIf.test(prev);
                if (update) {
                    nextT = next.apply(prev);
                    if (ref.compareAndSet(prev, nextT)) return new Witness<>(prev, nextT);
                }
                if (prev == (prev = ref.get())) return new Witness<>(prev, null); // if true, update was false, should return
            }
            return new Witness<>(prev, null);
        }

        public T updateAndGet(UnaryOperator<T> next) {
            return ref.updateAndGet(next);
        }

        public static class Witness<T> {
            public final T prev, next;

            Witness(T prev, T next) {
                this.prev = prev;
                this.next = next;
            }
        }

        /**
         * Will retry until match.
         * <br>
         * @return next == null if:
         * <li> A) update == false;
         *
         *
         * */
        public<S> Witness<T> contentiousCAS(
              Predicate<T> updateIf,
              UnaryOperator<T> nextMap
        ) {
            T prev = ref.get(), next;
            for (boolean update;;) {
                update = updateIf.test(prev);
                if (update) {
                    next = nextMap.apply(prev);
                    if (ref.compareAndSet(prev, next)) return new Witness<>(prev, next);
                }
                if (prev == (prev = ref.get()) //If true, update was false
                ) return new Witness<>(prev, null);
            }
        }
    }

    /**Delays the first execution, and then blocks contention and reuses Thread.*/
    public static class SwapScheduler {
        private static final int QUEUE = -2, CLOSE = -1, OPEN = 0, BUSY = 1, FINISH = 2, INTERRUPT = 3;

        private final AtomicInteger semaphore = new AtomicInteger();
        private volatile Runnable runnable;
        private final Consumer<Runnable> runnableConsumer;
        private final Runnable localRunnable = new Runnable() {
            @Override
            public void run() {
                int prev;
                do {
                    prev = semaphore.get();
                    while (prev < OPEN) {
                        if (semaphore.compareAndSet(prev, BUSY)) {
                            if (semaphore.get() == BUSY) { // Last check, it may have changed to QUEUE
                                runnable.run();
                                semaphore.set(FINISH);
                            }
                        }
                        prev = semaphore.get(); //re-check FINISH (may change to QUEUE)
                        // || failure if compareAndSet failed (May have been a CLOSE but now is a QUEUE).
                    }
                    //Only QUEUE || INTERRUPT can come after finish, redo If NOT FINISH || INTERRUPT
                    //last finish check, redo If false.
                } while (prev < FINISH || !semaphore.compareAndSet(prev, OPEN));
            }
        };

        public SwapScheduler(Consumer<Runnable> runnableConsumer) {
            this.runnableConsumer = runnableConsumer;
        }

        public boolean interrupt() {
            int prev = semaphore.get();
            return prev != OPEN && semaphore.compareAndSet(prev, INTERRUPT);
        }

        public void delay(Runnable runnable) {
            this.runnable = runnable;
            if (semaphore.compareAndSet(OPEN, CLOSE)) {
                runnableConsumer.accept(localRunnable
                );
            } else semaphore.set(QUEUE);
        }


        public static class Long {
            private static final Pair.Immutables.Int<SwapScheduler.Long.SleeperThread> OPEN_THREAD = new Pair.Immutables.Int<>(OPEN, null);
            private final AtomicReference<Pair.Immutables.Int<SwapScheduler.Long.SleeperThread>> semaphore = new AtomicReference<>(OPEN_THREAD);
            private volatile Runnable runnable;
            private final long millis;
            public Long(long millis) {
                this.millis = millis;
            }

            public boolean interrupt() {
                final Pair.Immutables.Int<SwapScheduler.Long.SleeperThread> prev = semaphore.get();
                return prev != OPEN_THREAD && semaphore.compareAndSet(prev, new Pair.Immutables.Int<>(INTERRUPT, prev.getValue()));
            }

            private Pair.Immutables.Int<SwapScheduler.Long.SleeperThread> getClosed() {
                return new Pair.Immutables.Int<>(CLOSE, new SwapScheduler.Long.SleeperThread());
            }

            private class SleeperThread extends Thread {
                private static final int  AWAKEN = 0, RETRY = 1, SHOULD_SLEEP = 2, SLEEPING = 3;
                private final AtomicInteger sleeper = new AtomicInteger(SHOULD_SLEEP);

                private void inferSleep() {
                    int prev = sleeper.get();
                    //Can never be sleeping while inferSleep() is active
                    if (prev > AWAKEN) {
                        do {
                            try {
                                sleeper.set(SLEEPING);
                                Thread.sleep(millis);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } while (!sleeper.compareAndSet(SLEEPING, AWAKEN)); //Retries if RETRY || SHOULD_SLEEP
                    }
                }

                public void trySleep() {
                    int prev = sleeper.get();
                    if (prev == AWAKEN) sleeper.compareAndSet(prev, SHOULD_SLEEP);
                    else sleeper.compareAndSet(prev, RETRY);
                }

                public SleeperThread() {
                }

                @Override
                public void run() {
                    Pair.Immutables.Int<SwapScheduler.Long.SleeperThread> prev, busy;
                    do {
                        prev = semaphore.get();
                        while (prev.getInt() < OPEN) {
                            busy = new Pair.Immutables.Int<>(BUSY, prev.getValue());
                            if (semaphore.compareAndSet(prev, busy)) {
                                if (semaphore.get().getInt() == BUSY) {
                                    inferSleep();
                                    if (semaphore.get().getInt() == BUSY) { // Last check, it may have changed to QUEUE
                                        runnable.run();
                                        semaphore.set(new Pair.Immutables.Int<>(FINISH, prev.getValue()));
                                    }
                                }
                            }
                            prev = semaphore.get(); //re-check FINISH (may change to QUEUE)
                            // || failure if compareAndSet failed (May have been a CLOSE but now is a QUEUE).
                        }
                        //Only QUEUE || INTERRUPT can come after finish, redo If NOT FINISH || INTERRUPT
                        //last finish check, redo If false.
                    } while (prev.getInt() < FINISH || !semaphore.compareAndSet(prev, OPEN_THREAD));
                }

                void queue() {
                    Pair.Immutables.Int<SwapScheduler.Long.SleeperThread> queued = new Pair.Immutables.Int<>(QUEUE, this);
                    semaphore.set(queued);
                    if (semaphore.get() == queued && isAlive()) {
                        trySleep();
                    }
                }
            }


            public void scheduleOrSwap(Runnable with) {
                this.runnable = with;
                final Pair.Immutables.Int<SwapScheduler.Long.SleeperThread> prev = semaphore.get();
                final boolean open = prev == OPEN_THREAD;
                final Pair.Immutables.Int<SwapScheduler.Long.SleeperThread> closed = open ? getClosed() : null;

                if (open && semaphore.compareAndSet(prev, closed)) {
                    closed.getValue().start();
                } else semaphore.get().getValue().queue();
            }

        }
    }

}
