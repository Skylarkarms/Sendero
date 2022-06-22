package sendero.atomics;

import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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

    /**Delays the first execution, and then blocks contention and reuses Thread.*/
    public static class OverlapDropExecutor {
        private static final int QUEUE = -2, CLOSE = -1, OPEN = 0, BUSY = 1, FINISH = 2, INTERRUPT = 3;

        private static final class RunnableRef extends Pair.Immutables.Int<Runnable> {
            public static final RunnableRef OPENED = new RunnableRef(OPEN, null);
            public static final RunnableRef FINISHED = new RunnableRef(FINISH, null);
            public static final RunnableRef INTERRUPTED = new RunnableRef(INTERRUPT, null);

            private RunnableRef(int anInt, Runnable value) {
                super(anInt, value);
            }

            boolean shouldCompute() {
                return anInt < OPEN;
            }
            boolean notOpen() {
                return anInt != OPEN;
            }
            boolean isBusy() {
                return anInt == BUSY;
            }
            boolean hasQueue() {
                return anInt < CLOSE;
            }

            RunnableRef compute() {
                return new RunnableRef(BUSY, value);
            }

        }

        private final AtomicReference<RunnableRef> semaphore = new AtomicReference<>(RunnableRef.OPENED);
        private final Consumer<Runnable> runnableConsumer;
        private final Runnable localRunnable = new Runnable() {
            @Override
            public void run() {
                RunnableRef prev;
                do {
                    prev = semaphore.get();
                    while (prev.shouldCompute()) {
                        if (semaphore.compareAndSet(prev, prev.compute())) {
                            if (semaphore.get().isBusy()) { // Last check, it may have changed to QUEUE
                                runnableConsumer.accept(prev.value);
                                semaphore.set(RunnableRef.FINISHED);
                            }
                        }
                        prev = semaphore.get(); //re-check FINISH (may change to QUEUE)
                        // || failure if compareAndSet failed (May have been a CLOSE but now is a QUEUE).
                    }
                    //Only QUEUE || INTERRUPT can come after finish, redo If NOT FINISH || INTERRUPT
                    //last finish check, redo If false.
                } while (prev.hasQueue() || !semaphore.compareAndSet(prev, RunnableRef.OPENED));
            }
        };

        public OverlapDropExecutor(Consumer<Runnable> runnableConsumer) {
            this.runnableConsumer = runnableConsumer;
        }

        public boolean interrupt() {
            RunnableRef prev = semaphore.get();
            return prev.notOpen() && semaphore.compareAndSet(prev, RunnableRef.INTERRUPTED);
        }

        public void swap(Runnable runnable) {
            RunnableRef newRef = new RunnableRef(CLOSE, runnable);
            if (semaphore.compareAndSet(RunnableRef.OPENED, newRef)) {
                localRunnable.run();
            } else semaphore.set(new RunnableRef(QUEUE, runnable));
        }


        public static class Long {
            private static final Pair.Immutables.Int<OverlapDropExecutor.Long.SleeperThread> OPEN_THREAD = new Pair.Immutables.Int<>(OPEN, null);
            private final AtomicReference<Pair.Immutables.Int<OverlapDropExecutor.Long.SleeperThread>> semaphore = new AtomicReference<>(OPEN_THREAD);
            private volatile Runnable runnable;
            private final long millis;
            public Long(long millis) {
                this.millis = millis;
            }

            public boolean interrupt() {
                final Pair.Immutables.Int<OverlapDropExecutor.Long.SleeperThread> prev = semaphore.get();
                return prev != OPEN_THREAD && semaphore.compareAndSet(prev, new Pair.Immutables.Int<>(INTERRUPT, prev.value));
            }

            private Pair.Immutables.Int<OverlapDropExecutor.Long.SleeperThread> getClosed() {
                return new Pair.Immutables.Int<>(CLOSE, new OverlapDropExecutor.Long.SleeperThread());
            }

            private class SleeperThread extends Thread {
                private static final int  AWAKEN = 0, RETRY = 1, SHOULD_SLEEP = 2, SLEEPING = 3;
                private final AtomicInteger sleeper = new AtomicInteger(SHOULD_SLEEP);

                @SuppressWarnings("BusyWait")
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
                    Pair.Immutables.Int<OverlapDropExecutor.Long.SleeperThread> prev, busy;
                    do {
                        prev = semaphore.get();
                        while (prev.anInt < OPEN) {
                            busy = new Pair.Immutables.Int<>(BUSY, prev.value);
                            if (semaphore.compareAndSet(prev, busy)) {
                                if (semaphore.get().anInt == BUSY) {
                                    inferSleep();
                                    if (semaphore.get().anInt == BUSY) { // Last check, it may have changed to QUEUE
                                        runnable.run();
                                        semaphore.set(new Pair.Immutables.Int<>(FINISH, prev.value));
                                    }
                                }
                            }
                            prev = semaphore.get(); //re-check FINISH (may change to QUEUE)
                            // || failure if compareAndSet failed (May have been a CLOSE but now is a QUEUE).
                        }
                        //Only QUEUE || INTERRUPT can come after finish, redo If NOT FINISH || INTERRUPT
                        //last finish check, redo If false.
                    } while (prev.anInt < FINISH || !semaphore.compareAndSet(prev, OPEN_THREAD));
                }

                void queue() {
                    Pair.Immutables.Int<OverlapDropExecutor.Long.SleeperThread> queued = new Pair.Immutables.Int<>(QUEUE, this);
                    semaphore.set(queued);
                    if (semaphore.get() == queued && isAlive()) {
                        trySleep();
                    }
                }
            }


            public void scheduleOrSwap(Runnable with) {
                this.runnable = with;
                final Pair.Immutables.Int<OverlapDropExecutor.Long.SleeperThread> prev = semaphore.get();
                final boolean open = prev == OPEN_THREAD;
                final Pair.Immutables.Int<OverlapDropExecutor.Long.SleeperThread> closed = open ? getClosed() : null;

                if (open && semaphore.compareAndSet(prev, closed)) {
                    closed.value.start();
                } else semaphore.get().value.queue();
            }

        }
    }

}
