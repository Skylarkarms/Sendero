import sendero.*;
import sendero.abstract_containers.Pair;
import sendero.interfaces.BinaryPredicate;
import sendero.interfaces.Updater;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class LivePath extends Path<Map<String, ?>> {

        public LivePath() {
            super(
                    mapHolderBuilder -> mapHolderBuilder.expectIn(((BinaryPredicate<Map<String, ?>>)
                            Map::equals).negate()
                    )
                    ,
                    Builders.ManagerBuilder.mutable()
//                    Builders.mutabilityAllowed()
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
            test.put("BUG TEST", new Object());
        }

        private final Updater<Map<String, ?>> updater = Inputs.getUpdater(this);

        public void setup(
                Gate.IO<Map<String, ?>> pref
        ) {
        pref.accept(test);
//            mapSupplier = pref;

            setOnStateChangeListener(
                    getConsumer(
                            pref,
                            new AtomicBinaryEventConsumer() {
                                final Consumer<Map<String, ?>> prefListener = (map) -> {
                                    Map<String, ?> deref = new HashMap<>(map);
                                    mapSupplier = () -> deref;
                                    updater.update(
                                            delay,
                                            prev -> deref
                                    );
                                };
                                @Override
                                protected void onStateChange(boolean isActive) {
                                    if (isActive) {
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
