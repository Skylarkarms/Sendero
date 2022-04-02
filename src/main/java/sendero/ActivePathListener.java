package sendero;

import sendero.atomics.AtomicUtils;
import sendero.interfaces.BooleanConsumer;

import java.util.function.Function;
import java.util.function.UnaryOperator;

//A BooleanConsumer is bounded by a BasePath Producer, so Appointer can be final.
class ActivePathListener<T> {
    private final ActivationManager manager;
    private final Appointers.HolderAppointer<T> appointerCache;

    private final AtomicUtils.WitnessAtomicReference<AppointerConsumer<?>> appointerConsumerCache = new AtomicUtils.WitnessAtomicReference<>(AppointerConsumer.not_set);

    private static final class AppointerConsumer<T> {
        final Appointers.Appointer<T> appointer;
        final BooleanConsumer consumer;

        static final AppointerConsumer<?> not_set = new AppointerConsumer<>(Appointers.Appointer.initiating);

        AppointerConsumer(Appointers.Appointer<T> appointer) {
            this.appointer = appointer;
            if (!appointer.isCleared()) {
                this.consumer = Appointers.Appointer.booleanConsumerAppointer(this.appointer);
            } else this.consumer = BooleanConsumer.cleared();
        }

        boolean equalTo(BasePath<?> basePath) {
            return appointer.equalTo(basePath);
        }

        /**This minus other*/
        int compareTo(Appointers.Appointer<?> other) {
            return this.appointer.appointerVersion - other.appointerVersion;
        }
    }

    protected ActivePathListener(ActivationManager manager, Appointers.HolderAppointer<T> appointerCache) {
        this.manager = manager;
        this.appointerCache = appointerCache;
    }

    /**should be protected*/
    protected  <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {

        final Appointers.Appointer<?> baseAppointer = appointerCache.setPathAndGet(basePath, map);

        // will be null if equal.
        if (baseAppointer != null) {
            //Will **attempt** to retry only under contention
            AppointerConsumer<?> nextAppointerConsumer = appointerConsumerCache.compliantCAS(
                    appointerConsumer -> baseAppointer.compareTo(appointerCache.getAppointer()) == 0 //stalls winning threads
                            && (appointerConsumer == AppointerConsumer.not_set || appointerConsumer.compareTo(baseAppointer) < 0),
                    appointerConsumer -> new AppointerConsumer<>(baseAppointer),
                    appointerConsumer -> baseAppointer.compareTo(appointerCache.getAppointer()) == 0 //stops winning threads
            ).next;

            if (nextAppointerConsumer != null) {
                manager.setActivationListener(nextAppointerConsumer.consumer);
            }
        }
    }

    void forcedSet(BooleanConsumer activationListener) {
        final Appointers.Appointer<?> appointer = appointerCache.clearAndGet();
        boolean cleared = appointer != null;
        if (cleared && appointerConsumerCache.compliantCAS(
                prev -> prev == AppointerConsumer.not_set || prev.compareTo(appointer) < 0, //If equal, do not set as it already changed
                prev -> new AppointerConsumer<>(appointer),
                //retries only if appointer is not in contention && this version is still lesser than newVersion
                // Which means:
                // winning forced sets will not update
                // && losing forced sets will continue spinning until update,
                // unless new contention is perceived at the tail of the contention stream,
                // OR a new set performed directly to appointer.
                prev -> {
                    return appointer.compareTo(appointerCache.getAppointer()) == 0 && (prev != AppointerConsumer.not_set && prev.compareTo(appointer) < 0);
                }
        ).next != null) {
            //If no contention reached set activationListener.
            manager.setActivationListener(activationListener);
        } else {
            //If appointer was cleared already, fast set.
            manager.setActivationListener(activationListener);
        }
    }

    /**should be protected*/
    protected  <S, P extends BasePath<T>> void bind(P basePath) {
        bindMap(basePath, UnaryOperator.identity());
    }

    protected boolean unbound() {
        final Appointers.Appointer<?> app = appointerCache.clearAndGet();
        if (app != null) {
            final AtomicUtils.WitnessAtomicReference.Witness<AppointerConsumer<?>> witness = appointerConsumerCache.compliantCAS(
                    appointerConsumer -> appointerConsumer != AppointerConsumer.not_set
                            && appointerConsumer.compareTo(app) < 0,
                    appointerConsumer -> new AppointerConsumer<>(app),
                    //If prev isNotSet OR lesser than after checking that appointer is still same, should break
                    prev -> app.compareTo(appointerCache.getAppointer()) == 0 && (prev != AppointerConsumer.not_set && prev.compareTo(app) < 0)
            );
            final AppointerConsumer<?> nextAppointerConsumer = witness.next;
            if (nextAppointerConsumer != null) {
                return manager.expectClearActivationListener(nextAppointerConsumer.consumer);
            }
        }
        return false;
    }
}
