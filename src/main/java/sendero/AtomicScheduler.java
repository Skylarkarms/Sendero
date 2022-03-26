package sendero;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class AtomicScheduler {
    private final AtomicReference<AtomicSchedulerService> reference;
    private static class AtomicSchedulerService {
        final Supplier<ScheduledExecutorService> service;
        final boolean set;
        ScheduledFuture<?> future;

        static AtomicSchedulerService init(Supplier<ScheduledExecutorService> service) {
            return new AtomicSchedulerService(service, false, null);
        }

        private AtomicSchedulerService(Supplier<ScheduledExecutorService> service, boolean set, ScheduledFuture<?> future) {
            this.service = service;
            this.set = set;
            this.future = future;
        }

        static AtomicSchedulerService inheritServiceAndCancelPrev(AtomicSchedulerService service) {
            if (service.set) service.future.cancel(true);
            return new AtomicSchedulerService(service.service, false, null);
        }

        AtomicSchedulerService setFuture(Runnable command, long delay, TimeUnit unit) {
            ScheduledFuture<?> localFuture = service.get().schedule(command, delay, unit);
            return new AtomicSchedulerService(service, true, localFuture);
        }

        void swap(AtomicReference<AtomicSchedulerService> reference, Runnable command, long delay, TimeUnit unit) {
            final AtomicSchedulerService next = inheritServiceAndCancelPrev(this);
            reference.set(next); //loser sets
            final AtomicSchedulerService set = next.setFuture(command, delay, unit);
            if (!reference.compareAndSet(next, set)) { //loser sets.
                //Is not referenced anywhere so we cancel and forget.
                set.future.cancel(true);//winners cancel.
            }
        }

        public boolean cancel() {
            return set && future.cancel(true);
        }
    }
    private final long tolerance;
    private final TimeUnit baseUnit;


    public AtomicScheduler(Supplier<ScheduledExecutorService> service, long tolerance, TimeUnit baseUnit) {
        this.reference = new AtomicReference<>(AtomicSchedulerService.init(service));
        this.tolerance = tolerance;
        this.baseUnit = baseUnit;
    }

    public void scheduleOrReplace(Runnable runnable) {
        reference.get().swap(reference, runnable, tolerance, baseUnit);
    }

    public boolean cancel() {
        return reference.get().cancel();
    }
}
