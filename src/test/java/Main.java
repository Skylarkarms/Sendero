import sendero.Gate;
import sendero.Merge;
import sendero.interfaces.Updater;

import java.io.Console;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class Main {
    public static void main(String[] args) {

//        Gate.IO<String> stringIO = new Gate.IO<>("Hello");
//        Gate.IO<String> stringWorld = new Gate.IO<>("World");
//
//        String[] INIT = new String[]{"", "", "", ""};
//        Merge<String[]> merge = new Merge<>(
//                INIT,
//                strings -> strings != INIT
//        ).from(
//                stringIO,
//                updater -> s -> {
//                    updater.update(
//                            strings -> {
//                                String[] clone = strings.clone();
//                                clone[0] = s;
//                                clone[2] = "DONE GREETING";
//                                return clone;
//                            }
//                    );
//                }
//        ).from(
//                stringWorld,
//                updater -> worldString -> {
//                    updater.update(
//                            strings -> {
//                                String[] clone = strings.clone();
//                                clone[1] = worldString;
//                                clone[3] = "DONE WORLD";
//                                return clone;
//                            }
//                    );
//                }
//        );
//        Gate.Out.Many<String> resOut = merge.out(Gate.Out.Many.class, strings -> strings[0] + " " + strings[1] + ", " +strings[2] + ", " + strings[3]);
//
//
//        Consumer<String> obs1 = s -> {
//            System.err.println("From 1 : " + s);
//        };
//
//        Consumer<String> obs2 = s -> {
//            System.err.println("From 2 : " + s);
//        };
//
//        System.err.println("registering...1");
//        resOut.register(
//                obs1
//        );
//        System.err.println("registering...2");
//        resOut.register(obs2);
//
//        System.err.println("updating...");
//        stringIO.update(
//                s -> "Good bye!"
//        );
//
//        new Thread(
//                () -> {
//                    System.err.println("unregistering...1: " + obs1);
//                    resOut.unregister(obs1);
//                    System.err.println("unregistering...2: " + obs2);
//                    resOut.unregister(obs2);
//                    try {
//                        Thread.sleep(500);
//                        System.err.println("updating 2...");
//                        stringIO.update(
//                                s -> "Good bye FOREVER!"
//                        );
//                        System.err.println("Registering again...");
//                        resOut.register(obs1);
//                        resOut.register(obs2);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//        ).start();




        LivePath lp = new LivePath();
        lp.setup(
                new Gate.IO<>(LivePath.test)
        );

        Gate.Out.Single<Map<String, ?>> lpOut = lp.out(Gate.Out.Single.class);
        lpOut.register(
                stringMap -> {
                    System.err.println(stringMap);
                }
        );
        try {
            Thread.sleep(200);
            lpOut.unregister();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
