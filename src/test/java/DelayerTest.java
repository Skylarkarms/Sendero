import sendero.atomics.AtomicScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DelayerTest {
    static class IntegerClass {
        volatile int integer;

        IntegerClass(int integer) {
            this.integer = integer;
        }

        public void setInteger(int integer) {
            this.integer = integer;
        }
    }
    public static void main(String[] args) {
        final IntegerClass integerClass = new IntegerClass(0);
        ScheduledExecutorService service = Executors.newScheduledThreadPool(4);
        AtomicScheduler scheduler = new AtomicScheduler(() -> service, 10, TimeUnit.MILLISECONDS);
        for (int i = 0; i < 20; i++) {
            System.err.println("iter: " + i);
            integerClass.setInteger(i);
            scheduler.scheduleOrReplace(
                    () -> System.err.println("RESULT: " + integerClass.integer)
            );
        }
        service.shutdown();
    }
}
