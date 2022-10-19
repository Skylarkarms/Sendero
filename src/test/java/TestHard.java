import sendero.Gate;
import sendero.Link;
import sendero.Path;

import java.util.HashMap;

public class TestHard {


    Gate.In<Integer> input = new Gate.In<>();
    Gate.In<Integer> input2 = new Gate.In<>(3);
    Link.Unbound<String> ALink = new Link.Unbound<>();
//    Link.Unbound.Switch<String> ALink = new Link.Unbound.Switch<>();

    Gate.In<String> hello = new Gate.In<>("Hello");
    Path<String> hey = hello.map(
            s -> s + " World!"

    );

    HashMap<Integer, Path<String>> firstMap = new HashMap<>();

    {
        firstMap.put(1, ALink);
        firstMap.put(3, hey);
    }

    Path<String> BLink = input.switchMap(
            integer -> {
                Path<String> res = firstMap.get(integer);
                System.err.println("input fork: " + res + "\n, int: " + integer);
                return res;
            }
    );

    Gate.Out.Single<String> BLinkOut = BLink.out(Gate.Out.Single.class);

    void commence() {
        BLinkOut.register(
                System.out::println
        );

        input.accept(1);
        System.err.println("switchMap: " + ALink);
        ALink.switchMap(
                input2,
                integer -> {
                    System.err.println("switchMap: " + ALink + ",\n int: " + integer);
                    return firstMap.get(integer);
                }
        );
        try {
            Thread.sleep(2000);
            BLinkOut.unregister();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
