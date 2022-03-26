import sendero.Gates;
import sendero.Link;
import sendero.Path;

import java.util.HashMap;

public class TestHard {


    Gates.In<Integer> input = new Gates.In<>();
    Gates.In<Integer> input2 = new Gates.In<>(3);
    Link.Unbound.Switch<String> ALink = new Link.Unbound.Switch<>();

    Gates.In<String> hello = new Gates.In<>("Hello");
    Path<String> hey = hello.forkMap(
            s -> s + " World!"

    );

    HashMap<Integer, Path<String>> firstMap = new HashMap<>();

    {
        firstMap.put(1, ALink);
        firstMap.put(3, hey);
    }

    Path<String> BLink = input.forkSwitch(
            integer -> firstMap.get(integer)
    );

    Gates.Out.Single<String> BLinkOut = BLink.out(Gates.Out.Single.class);

    void commence() {
        BLinkOut.register(
                System.out::println
        );

        input.accept(1);

        ALink.switchMap(
                input2,
                integer -> firstMap.get(integer)
        );
        try {
            Thread.sleep(2000);
            BLinkOut.unregister();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
