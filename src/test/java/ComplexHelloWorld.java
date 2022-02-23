import sendero.*;
import sendero.functions.Consumers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

class ComplexHelloWorld {
        private final Map<Integer, String> cache = new HashMap<>(5);
        private final Map<Integer, Path<String>> domainCache = new HashMap<>(2);

        private final Gates.In<String> holderCache1 = new Gates.In<>("Good Bye");
        private final Gates.In<String> holderCache2 = new Gates.In<>("Pluto");

        {
            domainCache.put(1, holderCache1);
            domainCache.put(5, holderCache2);

            cache.put(1, "Hello");
            cache.put(2, "World");
            cache.put(3, "Bye");
            cache.put(4, "Hey!");
            cache.put(5, "Sun");
        }

        private final Gates.IO<Integer> holder2 = new Gates.IO<>();
        private final Gates.IO<Integer> holder3 = new Gates.IO<>(5);

        {
            holder2.accept(3);
        }

        private final Link<Integer> integerSubscriber = new Link<>();

        private final BasePath<String> process = integerSubscriber.forkSwitch(
                domainCache::get
        );

        private final Path<String> firstString = holder2.forkMap(
                cache::get
        );

        private final Merge<String[]> res = new Merge<>(new String[2], strings -> {
            for (String s:strings
            ) {
                if (s == null) return false;
            }
            return true;
        });
        private final Gates.Out.Single<String[]> finalRes = res.out(Gates.Out.Single.class);

        {
            res.from(
                    firstString,
                    stringConsumerUpdater -> s1 -> {
                        String finalS = s1 + "[FROM first string]";
                        stringConsumerUpdater.update(
                                s2 -> {
                                    String[] copy = s2.clone();
                                    copy[0] = finalS;
                                    return copy;
                                }
                        );
                    }
            );

            res.from(
                    process,
                    stringConsumerUpdater -> s1 -> {
                        String finalS = s1 + "[from second string]";
                        stringConsumerUpdater.update(
                                s -> {
                                    String[] copy = s.clone();
                                    copy[1] = finalS;
                                    return copy;
                                }
                        );
                    }
            );
        }

        final Consumer<String[]> exitGate = Consumers.transform(
                strings -> strings[0] + " " + strings[1],
                s -> {
                    System.out.println(s);
                    finalRes.unregister();
                }
        );

        void commence() {
            System.out.println("registering observer...");
            finalRes.register(
                    exitGate
            );

            System.out.println("binding...");
            integerSubscriber.bind(holder3);
            System.out.println("accepting integer...");
            holder2.accept(1);
            System.out.println("accepting string...");
            holderCache2.accept(cache.get(2));
        }
}
