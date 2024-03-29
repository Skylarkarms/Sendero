package sendero.atomics;

import sendero.pairs.Pair;

import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.*;

public final class AtomicUtils {

    public static final class ContentionCheck {
        private final AtomicInteger ver = new AtomicInteger();

        public void checkThrow(Runnable heavyTask) {
            int curr  = ver.incrementAndGet();
            heavyTask.run();
            if (curr != ver.get()) throw new ConcurrentModificationException("Incoming version was: " + curr + " current version is: " + ver.get());
        }
    }

    /**
     * Drops the execution If a new version is added (x + 1).
     * The version should be built within the scope of it's Thread queue,
     * in order to properly signify it's version at the moment of execution.
     *
     * @param newVer: the version up to the point in which the Runnable is created.
     * @param volatileCheck: matched against a volatile read of the same version.
     * @return the Runnable to enter the executor.
     *
     * eg.: This method posts an execution to a background thread and then immediately posts it to the main thread.
     *
     * public static<T> void backAndForth(AtomicInteger versionCount, Callable<T> callable, Consumer<T> consumer) {
     *         int newVer = versionCount.incrementAndGet();
     *         Executor.onBack(
     *                 AtomicUtils.backPressureDropper(
     *                         newVer, versionCount::get,
     *                         () -> {
     *                             try {
     *                                 T t = callable.call();
     *                                 Executor.onMain(
     *                                         AtomicUtils.backPressureDropper(
     *                                                 newVer, versionCount::get,
     *                                                 () -> consumer.accept(t)
     *                                         )
     *                                 );
     *                             } catch (Exception e) {
     *                                 throw new RuntimeException(e);
     *                             }
     *                         }
     *                 )
     *         );
     *     }
     *
     *     Usage:
     *
     *     private final AtomicInteger countRef = new AtomicInteger();
     *
     *
     *     public void getLedgerCount(IntConsumer count) {
     *         LedgerDao dao = ledgerDaoP.get();
     *         Post.backAndForth(countRef, dao::getLedgerCount2, count::accept);
     *     }
     *
     *     Notice how the atomicInteger is kept outside the method, as it should serve <p>
     *     as a semaphore that inspects concurrency.
     *
     * */
    public static<T> Runnable backPressureDropper(
            int newVer,
            IntSupplier volatileCheck,
            Runnable action) {
        return () -> {
            if (!(newVer < volatileCheck.getAsInt())) {
                action.run();
            }
        };
    }

    public static void backPressureDrop(int newVer, IntSupplier volatileCheck, Runnable action) {
        if (!(newVer < volatileCheck.getAsInt())) {
            action.run();
        }
    }

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
        public static class IntObject {
            public final Integer prev, next;

            public IntObject(Integer prev, Integer next) {
                this.prev = prev;
                this.next = next;
            }
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

    /**If test == true, attempts to set: <p>
     *   Will never return (null, null)<p>
     *   if set succeeds returns (prev, next)<p>
     *if test == false, checks contention: <p>
     *     if contention met, retries.<p>
     *     if no contention met returns(prev, null)
     * */
    public static<T> Witness<T> contentiousSetIf(
            AtomicReference<T> ref,
            Predicate<T> test,
            UnaryOperator<T> nextMap
    ) {
        T prev = getVoid(), next;
        while (prev != (prev = ref.get())) {
            if (test.test(prev)) {
                next = nextMap.apply(prev);
                if (ref.compareAndSet(prev, next)) return new Witness<>(prev, next);
            }
        }
        return new Witness<>(prev, null);
    }

    /**
     * Attempts to atomically update an {@code AtomicReference} instance with a new value
     * generated by the specified {@code UnaryOperator}, using a spin loop to retry the update
     * if necessary. Returns {@code true} if the update was successful, or {@code false} if the
     * update failed or if the {@code UnaryOperator} generated the same value as the current value
     * of the {@code AtomicReference} instance.
     *
     * @param ref the {@code AtomicReference} instance to update
     * @param nextMap a function that generates the new value to set the {@code AtomicReference} instance to,
     *        based on the current value of the instance
     * @param <T> the type of the value stored in the {@code AtomicReference} instance
     * @return {@code t} if the update was successful, or {@code null} if the update failed or
     *         if the {@code UnaryOperator} generated the same value as the current value of the {@code AtomicReference}
     *         instance
     */
    public static<T> T contentiousSet(
            AtomicReference<T> ref,
            UnaryOperator<T> nextMap
    ) {
        T prev = getVoid(), next;
        while (prev != (prev = ref.get())) {
            next = nextMap.apply(prev);
            if (ref.compareAndSet(prev, next)) return next;
        }
        return null;
    }

    /**If test == true, attempts to set: <p>
     *   Will never return (null, null)<p>
     *   if set succeeds returns (prev, next)<p>
     *if test == false, checks contention: <p>
     *     if contention met, retries.<p>
     *     if no contention met returns(prev, null)
     * */
    public static<T> Witness.IntObject contentiousSetIf(
            AtomicInteger ref,
            IntPredicate test,
//            Predicate<T> test,
            IntUnaryOperator nextMap
    ) {
        Integer prev = null;
        int next;
        while (!Objects.equals(prev, prev = ref.get())) {
            if (test.test(prev)) {
                next = nextMap.applyAsInt(prev);
                if (ref.compareAndSet(prev, next)) return new Witness.IntObject(prev, next);
            }
        }
        return new Witness.IntObject(prev, null);
    }

    /**If test == true, attempts to set: <p>
     *   If set fails returns (null, null)<p>
     *   if set succeeds returns (prev, next)<p>
     *if test == false, checks contention: <p>
     *     if contention met, retries.<p>
     *     if no contention met returns(prev, null)
     * */
    public static<T> Witness.IntObject setIf(
            AtomicInteger ref,
            IntPredicate test,
            IntUnaryOperator nextMap
    ) {
        Integer prev = null;
        int next;
        while (!Objects.equals(prev, prev = ref.get())) {
            if (test.test(prev)) {
                next = nextMap.applyAsInt(prev);
                if (ref.compareAndSet(prev, next)) return new Witness.IntObject(prev, next);
                else return new Witness.IntObject(null, null);
            }
        }
        return new Witness.IntObject(prev, null);
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
        private final Runnable executorRunnable = () -> {
            RunnableRef prev;
            while ((prev = semaphore.get()).shouldCompute()) {
                RunnableRef computing = prev.computing();
                if (semaphore.compareAndSet(prev, computing)) {
                    computing.run();
                    semaphore.compareAndSet(computing, RunnableRef.OPENED);
                }
            }
        };
//        private final Runnable localRunnable = new Runnable() {
//            @Override
//            public void run() {
//                beginRun();
//
////                RunnableRef prev;
////                while ((prev = semaphore.get()).shouldCompute()) {
////                    RunnableRef computing = prev.computing();
////                    if (semaphore.compareAndSet(prev, computing)) {
////                        runnableConsumer.execute(computing);
////                        semaphore.compareAndSet(computing, RunnableRef.OPENED);
////                    }
////                }
//            }
//        };

        private void beginRun() {
            runnableConsumer.execute(executorRunnable);
        }

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
//                localRunnable.run();
                beginRun();
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
                return new SleeperThreadState(new RunnableRef(CLOSE, newRun), new SleeperThread());
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
//                                Interrupted!!
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

    public static final class AsyncConsumer<T> {
        private final boolean autoRestart;
        private volatile Pair<T> ref = Pair.getNotSet();
        private static final AtomicReferenceFieldUpdater<AsyncConsumer, Pair> REF_UPDATER = AtomicReferenceFieldUpdater.newUpdater(AsyncConsumer.class, Pair.class, "ref");

        public AsyncConsumer() {
            this(false);
        }

        public AsyncConsumer(boolean autoRestart) {
            this.autoRestart = autoRestart;
        }

        private static class Pair<T> {
            static final Pair<?> not_set = new AsyncConsumer.Pair<Object>(null, null) {
                @Override
                boolean isDone() {
                    return false;
                }
            };
            private static final String error = "Should call restart() to use again.";
            @SuppressWarnings("unchecked")
            static <T> Pair<T> getNotSet() {
                return (Pair<T>) not_set;
            }
            boolean isDone() {
                return t != null && tConsumer != null;
            }
            final T t;
            final Consumer<T> tConsumer;

            Pair<T> forValue(T value) {
                assert this.t == null : error;
                return new Pair<>(value, tConsumer);
            }

            Pair<T> forConsumer(Consumer<T> cons) {
                assert this.tConsumer == null : error;
                return new Pair<>(t, cons);
            }

            private Pair(T t, Consumer<T> tConsumer) {
                this.t = t;
                this.tConsumer = tConsumer;
            }

            boolean consumerSet() {
                if (t != null) {
                    tConsumer.accept(t);
                    return true;
                }
                return false;
            }

            boolean valueSet() {
                if (tConsumer != null) {
                    tConsumer.accept(t);
                    return true;
                }
                return false;
            }
        }

        /**@return true if the action was consummated*/
        public boolean consume(Consumer<T> tConsumer) {
            Pair<T> prev, next;
            do {
                prev = ref;
                next = prev.forConsumer(tConsumer);
            } while (!REF_UPDATER.compareAndSet(this, prev, next));
            return inferRestart(next.consumerSet());
        }

        public boolean isDone() {
            return ref.isDone();
        }

        public boolean restart() {
            return REF_UPDATER.getAndSet(this, Pair.getNotSet()) != Pair.getNotSet();
        }

        /**@return true if the action was consummated*/
        public boolean set(T t) {
            Pair<T> prev, next;
            do {
                prev = ref;
                next = prev.forValue(t);
            } while (!REF_UPDATER.compareAndSet(this, prev, next));
            return inferRestart(next.valueSet());
        }

        private boolean inferRestart(boolean wasSet) {
            if (autoRestart && wasSet) restart();
            return wasSet;
        }
    }
}
