import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

public class LockFreeChronometer {

    private static final int start = 0, last = 1;
    private final AtomicLongArray core = new AtomicLongArray(2);
    private static final LongSupplier provide = System::nanoTime;
    private LongPredicate expectLapse = lapse -> true;
    private long expected;

    private final LongUnaryOperator convertFun;

    public LockFreeChronometer() {
        this(null);
    }

    public LockFreeChronometer(TimeUnit unit) {
        TimeUnit unit1 = unit == null ? TimeUnit.NANOSECONDS : unit;
        convertFun = og -> unit1.convert(og, TimeUnit.NANOSECONDS);
    }

    public void lapseLesserThan(long expectLesserThan, TimeUnit unit) {
        expected = TimeUnit.NANOSECONDS.convert(expectLesserThan, unit);
        this.expectLapse = l -> l < expected;
    }

    public boolean hasStarted() {
        return core.get(start) != 0;
    }

    public void start() {
        if (core.getAndSet(last, core.updateAndGet(start, prev -> provide.getAsLong())) != 0) throw new IllegalStateException("Already started");
    }

    public long stop() {
        long lastVal = core.getAndSet(start, core.getAndSet(last, 0));
        return provide.getAsLong() - lastVal;
    }

    public long elapsed() {
        return provide.getAsLong() - core.get(start);
    }

    public long lapse() {
        long now = provide.getAsLong();
        long res = now - core.getAndSet(last, now);
        return convertFun.applyAsLong(res);
    }

    public static class Witness {
        public final boolean passed;
        public final long at;

        Witness(boolean passed, long at) {
            this.passed = passed;
            this.at = at;
        }
    }

    public Witness testLapseOrStop() {
        boolean passed;
        long at = -1;
        if (!(passed = expectLapse.test(lapse()))) at = stop();
        return new Witness(passed, at);
    }


}
