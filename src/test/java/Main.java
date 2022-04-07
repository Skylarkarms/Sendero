import sendero.Gate;
import sendero.Merge;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class Main {
    public static void main(String[] args) {
        Gate.In<String> hello = new Gate.In<>();
        Gate.In<String> world = new Gate.In<>("World");
        String[] og =  new String[2];
        System.err.println("Original is: " + og);
        Predicate<String[]> expectOut = strings -> {
            for (String s:strings
            ) {
                System.err.println(".............>>>String results are: " + s);
                if (s == null) {
                    return false;
                }
            }
            return true;
        };
        System.err.println("Expect out is: " + expectOut);
        Merge<String[]> helloWorld = new Merge<>(og, expectOut);
        System.err.println("Hello is: " + hello);
        System.err.println("World is: " + world);
        System.err.println("HelloWorld is: " + helloWorld);
        helloWorld.from(hello, updater -> helloString -> {
            System.err.println("updating... for string: " + helloString);
            updater.update(strings -> {
                String[] newRes = strings.clone();
                newRes[0] = helloString;
                System.err.println("Hello string is: " + helloString);
                return newRes;
            });
        });
        helloWorld.from(world, updater -> worldString -> {
            System.err.println("updating... for string: " + worldString);
            updater.update(strings -> {
                String[] newRes = strings.clone();
                newRes[1] = worldString;
                System.err.println("World string is: " + worldString);
                return newRes;
            });
        });

        Gate.Out.Single<String> result = helloWorld.forkMap(
                strings -> {
                    System.err.println("FROM FORKED MAP IS: " + strings);
                    return strings[0] + " " + strings[1];
                }
        ).out(Gate.Out.Single.class);

        result.register(
                new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        System.err.println("result s is: " + s + ", from observer: " + this);
                        System.out.println(s);
                        System.err.println("Unregistering.....");
                        result.unregister();
                    }
                }
        );

        System.err.println("setting hello...");
        hello.accept("Hello");

        ComplexHelloWorld test2 = new ComplexHelloWorld();
        test2.commence();

        ForkedMultiplication multiplication = new ForkedMultiplication();
        multiplication.commence();

        System.err.println("Test Hard");

        TestHard testHard = new TestHard();
        testHard.commence();



    }
}
