package sendero.executor;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class ThrowableExecutor extends ScheduledThreadPoolExecutor {
    public ThrowableExecutor(int corePoolSize) {
        super(corePoolSize);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t == null && r instanceof Future<?>) {
            Future<?> future = (Future<?>) r;
            boolean terminate = false;
            try {
                future.get();
            } catch (ExecutionException e) {
                terminate = true;
                e.printStackTrace();
            } catch (InterruptedException | CancellationException ie) {           // ignore/reset
                Thread.currentThread().interrupt();
            } finally {
                if (terminate) System.exit(0);
            }
        }
    }
}
