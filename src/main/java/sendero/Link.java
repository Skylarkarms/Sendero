package sendero;

import sendero.functions.Consumers;

import java.util.function.Consumer;
import java.util.function.Function;

public class Link<T> extends Path<T> implements Links.Link<T> {
    private final Links.AbsLink<T> absConnector = new Links.AbsLink<T>() {
        @Override
        protected Holders.ActivationHolder<T> holderSupplier() {
            return Link.this;
        }
    };

    public Link() {
        super(true);
    }

    @Override
    public boolean isBound() {
        return activationListenerIsSet();
    }

    @Override
    public boolean unbound() {
        return clearActivationListener();
    }

    @Override
    public void bind(BasePath<T> path) {
        absConnector.bind(path);
    }

    @Override
    public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
        absConnector.bindFun(path, exit);
    }

    @Override
    public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
        absConnector.switchMap(path, switchMap);
    }

    @Override
    public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
        absConnector.switchFun(path, exit);
    }
}

