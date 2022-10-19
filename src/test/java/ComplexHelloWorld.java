import sendero.*;
import sendero.functions.Consumers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

class ComplexHelloWorld {
        private final Map<Integer, String> cache = new HashMap<>(5);
        private final Map<Integer, Path<String>> domainCache = new HashMap<>(2);

        private final Gate.In<String> holderCache1 = new Gate.In<>("Good Bye");
        private final Gate.In<String> holderCache2 = new Gate.In<>("Pluto");

        {
            domainCache.put(1, holderCache1);
            domainCache.put(5, holderCache2);

            cache.put(1, "Hello");
            cache.put(2, "World");
            cache.put(3, "Bye");
            cache.put(4, "Hey!");
            cache.put(5, "Sun");
        }

        private final Gate.IO<Integer> holder2 = new Gate.IO<>();
        private final Gate.IO<Integer> holder3 = new Gate.IO<>(5);

        {
            holder2.accept(3);
        }

        private final Link.Unbound<Integer> integerSubscriber = new Link.Unbound<>();

        private final BasePath<String> process = integerSubscriber.switchMap(
                domainCache::get
        );

        private final Path<String> firstString = holder2.map(
                cache::get
        );

        private final Merge<String[]> res = new Merge<>(
                builder -> builder.expectOut(
                                strings -> {
                                    for (String s:strings
                                    ) {
                                        if (s == null) return false;
                                    }
                                    return true;                                    }
                        )
                        .withInitial(
                                new String[2]
                        ),
                Merge.Entry.get(
                        firstString,
                        (strings, s) -> {
                            String finalS = s + "[FROM first string]";
                            String[] copy = strings.clone();
                            copy[0] = finalS;
                            return copy;
                        }
                ),
                Merge.Entry.get(
                        process,
                        (strings, s) -> {
                            String finalS = s + "[from second string]";
                            String[] copy = strings.clone();
                            copy[1] = finalS;
                            return copy;
                        }
                )
        );
        private final Gate.Out.Single<String[]> finalRes = res.out(Gate.Out.Single.class);

//        {
//            res.from(
//                    firstString,
//                    (strings, s) -> {
//                        String finalS = s + "[FROM first string]";
//                        String[] copy = strings.clone();
//                        copy[0] = finalS;
//                        return copy;
//                    }
//            );
//
//            res.from(
//                    process,
//                    (strings, s) -> {
//                        String finalS = s + "[from second string]";
//                        String[] copy = strings.clone();
//                        copy[1] = finalS;
//                        return copy;
//                    }
//            );
//        }

        final Consumer<String[]> exitGate = Consumers.transform(
                strings -> strings[0] + " " + strings[1],
                s -> {
                    System.out.println(s);
                    finalRes.unregister();
                }
        );

        void commence() {
            System.err.println("registering observer...");
            finalRes.register(
                    exitGate
            );

            System.err.println("binding...");
            integerSubscriber.bind(holder3);
            System.err.println("accepting integer...");
            holder2.accept(1);
            System.err.println("accepting string...");
            holderCache2.accept(cache.get(2));
        }
}
