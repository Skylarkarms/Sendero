import sendero.Gate;
import sendero.Path;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class INITIALIZE {
    private final Gate.Acceptor<Map<String, Main.MyObj>> acceptor = new Gate.Acceptor<>();

    private final Path<Integer> integerPath = acceptor.forkMap(
            stringIntegerMap -> {
                System.err.println("HELLO MFCKER!!! from thread: " + Thread.currentThread());
                return stringIntegerMap.get(0).getAnInt();
            }
    );

    private final Gate.Out.Single<Integer> out = integerPath.out(Gate.Out.Single.class);


    public void getRes(Consumer<Integer> integerConsumer) {
        out.register(
                integerConsumer
        );
    }

    public void unregister() {
        out.unregister();
    }

    public void set(Map<String, Main.MyObj> map) {
        acceptor.accept(map);
    }
}
