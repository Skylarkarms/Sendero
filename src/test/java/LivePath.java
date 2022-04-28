import sendero.Builders;
import sendero.Gate;
import sendero.Path;
import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class LivePath extends Path<Map<String, ?>> {
        private static final String TAG = "LivePreferences";

//        @SuppressWarnings("unchecked")
        public LivePath() {
            super(
                    Builders.getHolderBuild(
                            mapHolderBuilder -> mapHolderBuilder.expectIn(((BinaryPredicate<Map<String, ?>>)
                                    Map::equals).negate()
                            )
                    )
                    ,
                    Builders.getManagerBuild().withMutable(true)
            );
        }

        private static final long delay = 50;

        private final AtomicReference<Pair.Immutables<Path<Map<String, ?>>, AtomicBinaryEventConsumer>> intAtomicReference = new AtomicReference<>();

        private AtomicBinaryEventConsumer getConsumer(Path<Map<String, ?>> preferences, AtomicBinaryEventConsumer newConsumer) {
            return intAtomicReference.updateAndGet(
                    booleanConsumerInt -> {
                        if (booleanConsumerInt == null) return new Pair.Immutables<>(preferences, newConsumer);
                        Path<Map<String, ?>> prev = booleanConsumerInt.firstValue;
                        if (prev != preferences) return new Pair.Immutables<>(preferences, newConsumer);
                        return booleanConsumerInt;
                    }
            ).secondValue;
        }

        public Map<String, ?> getAll() {
            return mapSupplier.get();
        }

        private volatile Supplier<Map<String, ?>> mapSupplier;

        public static final HashMap<String, Object> test = new HashMap<>();
        static {
            test.put("BUG", new Object());
        }

        public void setup(
                Gate.IO<Map<String, ?>> pref
        ) {
        pref.accept(test);
            mapSupplier = pref;

            setOnStateChangeListener(
                    getConsumer(
                            pref,
                            new AtomicBinaryEventConsumer() {
                                final Consumer<Map<String, ?>> prefListener = (map) -> {
                                    Map<String, ?> deref = new HashMap<>(map);
                                    update(
                                            delay,
                                            prev -> deref
                                    );
                                };
                                @Override
                                protected void onStateChange(boolean isActive) {
                                    if (isActive) {
//                                        pref.register(prefListener);
//                                        pref.registerOnSharedPreferenceChangeListener(prefListener);
                                        prefListener.accept(test);
                                    }
                                    else {
                                        pref.unregister(prefListener);
                                    }
                                }
                            }
                    )
            );
        }

        public<V> Path<V> getAt(Function<Map<String, ?>, V> mapVFunction) {
            return forkMap(mapVFunction);
        }

//    }
}
