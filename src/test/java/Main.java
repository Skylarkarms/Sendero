import ProactiveSupplierTest.LifeCycledViewModel;
import ProactiveSupplierTest.SwitchSwitchMapTest;
import sendero.Gate;
import sendero.Merge;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class Main {

    private static void sleep(int seconds) {
        for (int i = 0; i <= seconds; i++) {
            System.err.println("Main: sleeping..." + i);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void forkThread(Runnable runnable) {
        new Thread(runnable).start();
    }

    public static void main(String[] args) {

        SwitchSwitchMapTest switchSwitchMapTest = new SwitchSwitchMapTest();

        LifeCycledViewModel lifeCycledViewModel = new LifeCycledViewModel();

        lifeCycledViewModel.start();

        switchSwitchMapTest.delayedAccept(2000, true);

        switchSwitchMapTest.delayedAccept(5000, SwitchSwitchMapTest.titleType.not_set);
        switchSwitchMapTest.delayedAccept(3000, SwitchSwitchMapTest.titleType.set);
        switchSwitchMapTest.delayedAccept(6000, false);

        new Thread(
                () -> {
                    try {
                        Thread.sleep(8000);
                        SwitchSwitchMapTest.Result res = lifeCycledViewModel.get();
                        System.err.println(res);
                        lifeCycledViewModel.shutoff();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
        ).start();




        SwitchMapTest test = new SwitchMapTest();

        Consumer<SwitchMapTest.Obj> observerOne = obj -> System.out.println("Main: Observer ONE: " +
                "\n state is: <<<<<<[" + obj.state + "] >>>>>>" +
                ",\n content is: " + obj.content
        );

        Consumer<SwitchMapTest.Obj> observerTwo = obj -> System.out.println("Main: Observer ONE: " +
                ",\n state is: <<<<<<[" + obj.state + "] >>>>>>" +
                ",\n content is: " + obj.content
        );

        System.err.println("Main: connecting observer: ONE");
        test.observe(observerOne);

        forkThread(
                () -> {
                    sleep(3);
                    System.err.println("Main: set Source: TWO");
                    test.setSource(SwitchMapTest.TWO);
                    forkThread(
                            () -> {
                                sleep(3);
                                System.err.println("Main: set Source: ONE");
                                test.setSource(SwitchMapTest.ONE);
                                forkThread(
                                        () -> {
                                            sleep(3);
                                            System.err.println("Main: unregistering ONE");
                                            test.unregister(observerOne);
                                        }
                                );
                            }
                    );

                }
        );


        Gate.IO<String> stringIO = new Gate.IO<>("Hello");
        Gate.IO<String> stringWorld = new Gate.IO<>("World");

        String[] INIT = new String[]{"", "", "", ""};
        System.err.println("INIT is: " + INIT);
        Function<String[], Boolean> isEmpty = strings -> {
            for (String s:strings
                 ) {
                if (s.isEmpty()) return true;
            }
            return false;
        };
        Merge<String[]> merge = new Merge<String[]>(
                holderBuilder -> holderBuilder.withInitial(INIT).excludeOut(
                        strings -> {
                            boolean test2 = strings == INIT;
                            System.err.println("Main: test was: " + test2);
                            System.err.println("Main: isEmpty? " + isEmpty);
                            return test2 || isEmpty.apply(strings);
                        }
                ),
                Merge.Entry.get(
                        stringIO,
                        (strings, helloString) -> {
                            System.err.println("updating from Hello: " + stringIO +
                                    ",\n string is: " + helloString +
                                    ",\n main: original is: " + strings +
                                    ",\n from: HELLO!!!");
                            String[] clone = strings.clone();
                            System.err.println("clone is: " + clone +
                                    ",\n from: HELLO>>>");
                            clone[0] = helloString;
                            clone[2] = "DONE GREETING";
                            return clone;
                        }
                ),
                Merge.Entry.get(
                        stringWorld,
                        (strings, worldString) -> {
                            System.err.println("updating from World: " + stringWorld +
                                    ",\n string is: " + worldString +
                                    ",\n to updater: " +
                                    ",\n main: original is: " + strings +
                                    ",\n from: WORLD!!!");
                            String[] clone = strings.clone();
                            System.err.println("clone is: " + clone +
                                    ",\n FROM: WOLRD");
                            clone[1] = worldString;
                            clone[3] = "DONE WORLD";
                            return clone;
                        }
                )
        );

        Gate.Out.Many<String> resOut = merge.out(Gate.Out.Many.class, strings -> {
            System.err.println("strings are: " + strings);
            String res = strings[0] + " " + strings[1] + ", " + strings[2] + ", " + strings[3];
            System.err.println("to OUT: " + res + ">>>>>>>>>>>>>>>>>>");
            return res;
        });


        Consumer<String> obs1 = s -> {
            System.err.println("Main From 1 : " + s);
        };

        Consumer<String> obs2 = s -> {
            System.err.println("Main From 2 : " + s);
        };

        System.err.println("registering...1");
        resOut.register(
                obs1
        );
        System.err.println("registering...2");
        resOut.register(obs2);

        System.err.println("updating...");
        stringIO.updateAndGet(
                s -> "Good bye!"
        );

        forkThread(
                () -> {
                    System.err.println("unregistering...1: " + obs1);
                    resOut.unregister(obs1);
                    System.err.println("unregistering...2: " + obs2);
                    resOut.unregister(obs2);
                    try {
                        Thread.sleep(500);
                        System.err.println("updating 2...");
                        stringIO.updateAndGet(
                                s -> "Good bye FOREVER!"
                        );
                        System.err.println("Registering again...");
                        resOut.register(obs1);
                        resOut.register(obs2);
                        Thread.sleep(500);
                        System.err.println("Unregister FOREVER...");
                        resOut.unregister(obs1);
                        resOut.unregister(obs2);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
        );




        LivePath lp = new LivePath();
        lp.setup(
                new Gate.IO<>(LivePath.test)
        );

        Gate.Out.Single<Map<String, ?>> lpOut = lp.out(Gate.Out.Single.class);
        lpOut.register(
                stringMap -> {
                    System.out.println(stringMap);
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
        Merge<String[]> helloWorld = new Merge<>(
                holderBuilder -> holderBuilder.withInitial(og).expectOut(expectOut),
                Merge.Entry.get(
                        hello,
                        (strings, helloString) -> {
                            System.err.println("updating... for string: " + helloString);
                            String[] newRes = strings.clone();
                            newRes[0] = helloString;
                            System.err.println("Hello string is: " + helloString);
                            return newRes;
                        }
                ),
                Merge.Entry.get(
                        world,
                        (strings, worldString) -> {
                            System.err.println("updating... for string: " + worldString);
                            String[] newRes = strings.clone();
                            newRes[1] = worldString;
                            System.err.println("World string is: " + worldString);
                            return newRes;
                        }
                )
        );
        System.err.println("Hello is: " + hello);
        System.err.println("World is: " + world);
        System.err.println("HelloWorld is: " + helloWorld);

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
                        System.out.println("Result from Merge is: " + s);
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
