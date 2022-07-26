import sendero.AtomicBinaryEvent;
import sendero.Builders;
import sendero.Gate;
import sendero.Path;

import java.util.function.Consumer;

public class FastSubscriptionTest {
    private static final String FIRST = "FIRST", SECOND = "SECOND", THIRD = "THIRD", FOURTH = "FOURTH", CHANGED = "CHANGED";
    Gate.Acceptor<String> first = new Gate.Acceptor<>(FIRST);

    private final Path<String> second = first.forkMap(prev -> prev + ",\n " + SECOND);
    private final Path<String> third = second.forkMap(prev -> prev + ",\n " + THIRD);
    private final Path<String> fourth = third.forkMap(prev -> prev + ",\n " + FOURTH);

    public AtomicBinaryEvent watch(Consumer<String> consumer) {
        return Builders.ReceptorBuilder.exit(consumer).asEvent(Runnable::run, fourth);
    }

    public void changeState() {
        first.accept(CHANGED);
    }

}
