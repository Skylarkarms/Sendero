package sendero.atomics;

import sendero.abstract_containers.Pair;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class AtomicUtils {

    public static class Witness<T> {
        private static final Witness<?> DEFAULT = new Witness<>(null, null);
        @SuppressWarnings("unchecked")
        public static<S> Witness<S> getDefault() {
            return (Witness<S>) DEFAULT;
        }
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

        public boolean isDefault() {
            return this == DEFAULT;
        }
    }

    private static final Object VOID = new Object();
    @SuppressWarnings("unchecked")
    private static<S> S getVoid() {
        return (S) VOID;
    }

    /**If test == true, attempts to set: <p>
     *   If set fails returns (null, null)<p>
     *   if set succeeds returns (prev, next)<p>
     *if test == false, checks contention: <p>
     *     if contention met, retries.<p>
     *     if no contention met returns(prev, null)
     * */
    public static<T> Witness<T> setIf(
            AtomicReference<T> ref,
            Predicate<T> test,
            UnaryOperator<T> nextMap
    ) {
        T prev = getVoid(), next;
        while (prev != (prev = ref.get())) {
            if (test.test(prev)) {
                next = nextMap.apply(prev);
                if (ref.compareAndSet(prev, next)) return new Witness<>(prev, next);
                else return new Witness<>(null, null);
            }
        }
        return new Witness<>(prev, null);
    }

    /**Delays the first execution, and then blocks contention and reuses Thread.*/
    public static class OverlapDropExecutor {
        private static final int QUEUE = -2, CLOSE = -1, SLEEPING = 0, BUSY = 1, OPEN = 2;

        private static final class RunnableRef extends Pair.Immutables.Int<Runnable> implements Runnable {
            public static final RunnableRef OPENED = new RunnableRef(OPEN, null);

            private RunnableRef(int anInt, Runnable value) {
                super(anInt, value);
            }

            boolean shouldCompute() {
                return anInt < SLEEPING;
            }

            boolean atLeastSleeping() {
                return anInt < BUSY;
            }

            RunnableRef sleeping() {
                return new RunnableRef(SLEEPING, value);
            }

            RunnableRef computing() {
                return new RunnableRef(BUSY, value);
            }

            @Override
            public void run() {
                value.run();
            }
        }

        private final AtomicReference<RunnableRef> semaphore = new AtomicReference<>(RunnableRef.OPENED);
        private final Executor runnableConsumer;
        private final Runnable localRunnable = new Runnable() {
            @Override
            public void run() {
                RunnableRef prev;
                while ((prev = semaphore.get()).shouldCompute()) {
                    RunnableRef computing = prev.computing();
                    if (semaphore.compareAndSet(prev, computing)) {
                        runnableConsumer.execute(computing);
                        semaphore.compareAndSet(computing, RunnableRef.OPENED);
                    }
                }
            }
        };

        public OverlapDropExecutor(
                Executor runnableConsumer
        ) {
            this.runnableConsumer = runnableConsumer;
        }

        public boolean interrupt() {
            RunnableRef prev = semaphore.get();
            return prev.atLeastSleeping() && semaphore.compareAndSet(prev, RunnableRef.OPENED);
        }

        public void swap(Runnable runnable) {
            RunnableRef newRef = new RunnableRef(CLOSE, runnable);
            CAS(newRef, runnable);
        }

        private void CAS(RunnableRef newRef, Runnable runnable) {
            if (semaphore.compareAndSet(RunnableRef.OPENED, newRef)) {
                localRunnable.run();
            } else {

                RunnableRef queued = new RunnableRef(QUEUE, runnable);
                if (!queued(queued)) CAS(newRef, runnable);
            }
        }

        private boolean queued(RunnableRef queued) {
            RunnableRef prev;
            while ((prev = semaphore.get()) != RunnableRef.OPENED) {
                if (semaphore.compareAndSet(prev, queued)) return true;
            }
            return false;
        }

        public static class Long {
            private final AtomicReference<SleeperThreadState> semaphore = new AtomicReference<>(SleeperThreadState.OPEN_THREAD);
            private static final class SleeperThreadState {
                public static final SleeperThreadState OPEN_THREAD = new SleeperThreadState(new RunnableRef(OPEN, null), null);

                private final SleeperThread sleeperThread;
                private final RunnableRef reference;
                SleeperThreadState(RunnableRef top, SleeperThread sleeperThread) {
                    reference = top;
                    this.sleeperThread = sleeperThread;
                }
                boolean shouldCompute() {
                    return reference.shouldCompute();
                }
                SleeperThreadState computing(){
                    return new SleeperThreadState(reference.computing(), sleeperThread);
                }
                SleeperThreadState sleeping(){
                    return new SleeperThreadState(reference.sleeping(), sleeperThread);
                }
                boolean atLeastSleeping() {
                    return reference.atLeastSleeping();
                }
            }

            private final long millis;
            public Long(long millis) {
                this.millis = millis;
            }

            public boolean interrupt() {
                final SleeperThreadState prev = semaphore.get();
                boolean interrupted = prev.atLeastSleeping() && semaphore.compareAndSet(prev, SleeperThreadState.OPEN_THREAD);
                if (interrupted) prev.sleeperThread.interrupt();
                return interrupted;
            }

            private SleeperThreadState getNewClosed(Runnable newRun) {
                return new SleeperThreadState(new RunnableRef(CLOSE, newRun), new OverlapDropExecutor.Long.SleeperThread());
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
                    SleeperThreadState prev, sleeping, busy;
                    while ((prev = semaphore.get()).shouldCompute()) {
                        sleeping = prev.sleeping();
                        if (semaphore.compareAndSet(prev, sleeping)) {
                            inferSleep();
                            busy = sleeping.computing();
                            if (semaphore.compareAndSet(sleeping, busy)) { // Last check, it may have changed to QUEUE
                                busy.reference.run();
                                semaphore.compareAndSet(busy, SleeperThreadState.OPEN_THREAD);
                            }
                        }
                    }
                }

                /**@return true when successful, false when failed*/
                boolean queue(Runnable newRun) {
                    SleeperThreadState prev, queued;
                    RunnableRef next = new RunnableRef(QUEUE, newRun);
                    queued = new SleeperThreadState(next, this);
                    while ((prev = semaphore.get()) != SleeperThreadState.OPEN_THREAD) {
                        if (semaphore.compareAndSet(prev, queued)) {
                            trySleep();
                            return true;
                        }
                    }
                    return false;
                }
            }

            public void scheduleOrSwap(Runnable with) {
                final SleeperThreadState closed = getNewClosed(with);
                CAS(with, closed);
            }

            private void CAS(Runnable with, SleeperThreadState closed) {
                if (semaphore.compareAndSet(SleeperThreadState.OPEN_THREAD, closed)) closed.sleeperThread.start();
                else if (!semaphore.get().sleeperThread.queue(with)) CAS(with, closed);
            }
        }
    }
}
