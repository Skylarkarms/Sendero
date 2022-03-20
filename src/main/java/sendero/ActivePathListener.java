package sendero;

import sendero.atomics.AtomicUtils;
import sendero.interfaces.BooleanConsumer;

import java.util.function.Function;
import java.util.function.UnaryOperator;

//A BooleanConsumer is bounded by a BasePath Producer, so Appointer can be final.
public /*abstract*/ class ActivePathListener<T> /*extends BasePath<T>*/ /*implements BaseLink2<T>*/{
    private final ActivationManager manager;
    private final BasePath.SelfAppointer<T> appointer;

//    abstract void acceptVersionValue(Pair.Immutables.Int<T> versionValue);

    private final AtomicUtils.WitnessAtomicReference<AppointerConsumer<?>> appointerConsumerCache = new AtomicUtils.WitnessAtomicReference<>(AppointerConsumer.not_set);

    private static final class AppointerConsumer<T> {
        final BasePath.Appointer<T> appointer;
        final BooleanConsumer consumer;

        static final AppointerConsumer<?> not_set = new AppointerConsumer<>(BasePath.Appointer.initiating);

        AppointerConsumer(BasePath.Appointer<T> appointer) {
            this.appointer = appointer;
            this.consumer = isActive -> {
                if (isActive) this.appointer.appoint();
                else this.appointer.demote();
            };
        }

        boolean equalTo(BasePath<?> basePath) {
            return appointer.equalTo(basePath);
        }

        /**This minus other*/
        int compareTo(BasePath.Appointer<?> other) {
            return this.appointer.appointerVersion - other.appointerVersion;
        }
    }

    protected ActivePathListener(ActivationManager manager, BasePath.SelfAppointer<T> appointer) {
//    protected ActivePathListener(ActivationManager manager, BasePath.SelfAppointer<T> appointer) {
        this.manager = manager;
        this.appointer = appointer;
    }

    /**should be protected*/
    protected  <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {

        final BasePath.Appointer<?> baseAppointer = appointer.setPath(basePath, map);

        // will be null if equal.
        if (baseAppointer != null) {
            //Will attempt to retry only under contention
            AppointerConsumer<?> nextAppointerConsumer = appointerConsumerCache.spinSwap(
                    appointerConsumer -> baseAppointer.compareTo(appointer.get()) == 0 //stalls winning threads
                            && (appointerConsumer == AppointerConsumer.not_set || appointerConsumer.compareTo(baseAppointer) < 0),
                    appointerConsumer -> new AppointerConsumer<>(baseAppointer),
                    appointerConsumer -> baseAppointer.compareTo(appointer.get()) == 0 //stops winning threads
            ).next;

            if (nextAppointerConsumer != null) {
                manager.setActivationListener(nextAppointerConsumer.consumer);
            }
        }
    }

    void forcedSet(BooleanConsumer activationListener) {
        BasePath.Appointer<?> app = appointer.clearAndGet();
        if (app != null && appointerConsumerCache.spinSwap(
                prev -> prev == AppointerConsumer.not_set || prev.compareTo(app) < 0, //If equal, do not set as it already changed
                prev -> new AppointerConsumer<>(app),
                //retries only if appointer is not in contention && this version is still lesser than newVersion
                // Which means:
                // winning forced sets will not update
                // && losing forced sets will continue spinning until update,
                // unless new contention is perceived at the tail of the contention stream,
                // OR a new set performed directly to appointer.
                prev -> app.compareTo(appointer.get()) == 0 && (prev != AppointerConsumer.not_set && prev.compareTo(app) < 0)
        ).next != null) {
            //If no contention reached set activationListener.
            manager.setActivationListener(activationListener);
        } else if (app == null) {
            //If appointer was cleared already.
            manager.setActivationListener(activationListener);
        }
    }

    /**should be protected*/
    protected  <S, P extends BasePath<T>> void bind(P basePath) {
        bindMap(basePath, UnaryOperator.identity());
    }

    protected boolean isBound() {
        return manager.activationListenerIsSet();
    }

    protected boolean unbound() {
        final BasePath.Appointer<?> app = appointer.clearAndGet();
        if (app != null) {
            final AtomicUtils.WitnessAtomicReference.Witness<AppointerConsumer<?>> witness = appointerConsumerCache.spinSwap(
                    appointerConsumer -> appointerConsumer != AppointerConsumer.not_set
                            && appointerConsumer.compareTo(app) < 0,
                    appointerConsumer -> new AppointerConsumer<>(app),
                    //If prev isNotSet OR lesser than after checking that appointer is still same, should break
                    prev -> app.compareTo(appointer.get()) == 0 && (prev != AppointerConsumer.not_set && prev.compareTo(app) < 0)
            );
            final AppointerConsumer<?> nextAppointerConsumer = witness.next;
            if (nextAppointerConsumer != null) {
                return manager.expectClearActivationListener(nextAppointerConsumer.consumer);
            }
        }
        return false;
    }
}
