package sendero;

import sendero.interfaces.Updater;

import java.util.function.Consumer;
import java.util.function.Function;

interface BaseMerge<T> {
    <S> Merge<T> from(
            BasePath<S> path,
            Function<Updater<T>, Consumer<S>> observer
    );
    <S> boolean drop(
            BasePath<S> path
    );
}
