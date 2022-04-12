package sendero.atomics;

import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

public final class AtomicUtils {

    public static class Witness<T> {
        public final T prev, next;

        Witness(T prev, T next) {
            this.prev = prev;
            this.next = next;
        }

        public static class Int {
            public final int prev, next;

            public Int(int prev, int next) {
                this.prev = prev;
                this.next = next;
            }
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
    public static<T> Witness<T> contentiousCAS(
            AtomicReference<T> ref,
            Predicate<T> updateIf,
            UnaryOperator<T> nextMap
    ) {
        T prev = null, next;
        while (prev != (prev = ref.get())) {
            if (updateIf.test(prev)) {
                next = nextMap.apply(prev);
                if (ref.compareAndSet(prev, next)) return new Witness<>(prev, next);
                else return new Witness<>(null, null);
            }
        }
        return new Witness<>(prev, null);
    }

    /**retryIf on miss*/
    public static<T> Witness<T> complyWithCAS(
            AtomicReference<T> ref,
            Predicate<T> updateIf,
            UnaryOperator<T> nextOperator,
            Predicate<T> retryIf
    ) {
        T prev = ref.get(), next = null;
        boolean update;
        do {
            update = updateIf.test(prev);
            if (update) {
                next = nextOperator.apply(prev);
                if (ref.compareAndSet(prev, next)) return new Witness<>(prev, next);
            }
        } while (retryIf.test(prev = ref.get()));
        return new Witness<>(prev, next);
    }

    public static Witness.Int complyWithCAS(
            AtomicInteger ref,
            IntPredicate updateIf,
            IntUnaryOperator nextOperator,
            IntPredicate retryIf
    ) {
        int prev = ref.get(), next = -1;
        boolean update;
        do {
            update = updateIf.test(prev);
            if (update) {
                next = nextOperator.applyAsInt(prev);
                if (ref.compareAndSet(prev, next)) return new Witness.Int(prev, next);
            }
        } while (retryIf.test(prev = ref.get()));
        return new Witness.Int(prev, next);
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
