package sendero;

import sendero.atomics.AtomicUtils;
import sendero.interfaces.BooleanConsumer;
import sendero.pairs.Pair;

import java.util.function.Function;
import java.util.function.UnaryOperator;

//A BooleanConsumer is bounded by a BasePath Producer, so Appointer can be final.
public abstract class ActivePathListener<T> /*extends BasePath<T>*/ /*implements BaseLink2<T>*/{
    private final ActivationManager manager;
    private final BasePath.SelfAppointer<T> appointer;

    abstract void acceptVersionValue(Pair.Immutables.Int<T> versionValue);

    private final AtomicUtils.TaggedAtomicReference<BasePath.Appointer<?>, BooleanConsumer> ownerCache = new AtomicUtils.TaggedAtomicReference<>();
//    private final AtomicUtils.TaggedAtomicReference<BasePath<?>, BooleanConsumer> ownerCache = new AtomicUtils.TaggedAtomicReference<>();

    protected ActivePathListener(ActivationManager manager, BasePath.SelfAppointer<T> appointer) {
        this.manager = manager;
        this.appointer = appointer;
    }

//    private static<S> BooleanConsumer activationListenerCreator2(
//            Supplier<BasePath<S>> basePathSupplier,
//            Consumer<Pair.Immutables.Int<S>> toAppoint
//    ) {
//        return new BooleanConsumer() {
//            //The supplier is for the Client to be allowed to  create a path at super()
//            final BasePath<S> basePath = basePathSupplier.get();
//            @Override
//            public void accept(boolean isActive) {
//                if (isActive) basePath.appoint(toAppoint);
//                else if (basePath instanceof BasePath.ToMany) {
//                    ((BasePath.ToMany<S>) basePath).demote(toAppoint);
//                } else if (basePath instanceof BasePath.Injective) {
//                    ((BasePath.Injective<S>) basePath).demote();
//                }
//            }
//        };
//    }
    private static<S> BooleanConsumer activeFixedAppointerListenerFactory(
            final BasePath.Appointer<S> fixedAppointer
    ) {
        return new BooleanConsumer() {
            //The supplier is for the Client to be allowed to  create a path at super()
//            final BasePath.Appointer<S> booleanAppointer = fixedAppointer;
            @Override
            public void accept(boolean isActive) {
                if (isActive) fixedAppointer.appoint();
                else fixedAppointer.demote();
            }

            @Override
            public boolean equals(Object obj) {
                assert obj instanceof BasePath.Appointer;
                return fixedAppointer.equals(obj);
            }
        };
    }
    /**should be protected*/
    protected  <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {
//        manager.setActivationListener(
//                ownerCache.diffUpdateAndGet(basePath, () -> activationListenerCreator2(
//                        () -> basePath, sInt -> acceptVersionValue(new Pair.Immutables.Int<>(sInt.getInt(), map.apply(sInt.getValue())))
//                )));
        manager.setActivationListener(
//                appointer.setPath(basePath, map);
                ownerCache.diffUpdateAndGet(appointer.setPath(basePath, map),
                        ActivePathListener::activeFixedAppointerListenerFactory
//                        () -> activeFixedAppointerListenerFactory(
////                ownerCache.diffUpdateAndGet(basePath, () -> activeFixedAppointerListenerFactory(
//                                new BasePath.Appointer<>(basePath, sInt -> acceptVersionValue(new Pair.Immutables.Int<>(sInt.getInt(), map.apply(sInt.getValue()))))
//                        )
                ));
    }

    //Why should I hide manager???
    //activation lsitener is protected
    //any user implementation can be performed with a mixture of listen() and method onStateChange(boolean isActive) override.
    //Why is Activation Listener needed then?
    //ActivationListener offers a "swap" function in which a change of listenner is able to remember the last state of activation, delivering a deactivation to the last listener accordingly.
    //This swap function is useful on "bind" operation and fast re-"bind" operations.
    //Is not only useful, it is a requirement.
    //This requirement can be mimmicked by the client but in a lesser and less performant extent.
    //By misxing both onStateChange(boolean isActive) override plus an implementation of both "listen(Path) AND stopListeningPath();"
    //In reality the cient should never feel in the need to do this since a bindMap operation can be found in the Path protected implementation, all it needs to do is eiather make it public OR
    //Use the Link class.
    void forcedSet(BooleanConsumer activationListener) {
//        ownerCache.set(activationListener);
//        ownerCache.set(activationListener);
//        manager.setActivationListener(ownerCache.expectTagAndSet(appointer.getAndClear(), activationListener));
        manager.setActivationListener(ownerCache.forceUpdateAndGet(activationListener, appointer::clear));
//        manager.setActivationListener(ownerCache.forceUpdateAndGet(activationListener, appointer1 -> appointer1.demote()));
//        manager.setActivationListener(activationListener);
    }

    /**should be protected*/
    protected  <S, P extends BasePath<T>> void bind(P basePath) {
        bindMap(basePath, UnaryOperator.identity());
    }

    protected boolean isBound() {
        return manager.activationListenerIsSet();
    }

    protected boolean unbound() {
        return manager.expectClearActivationListener(ownerCache.expectTagAndClear(appointer.getAndClear()));
        //Todo: Wrong
//        return ownerCache.expectAndClear(manager.getAndClearActivationListener());
    }
}
