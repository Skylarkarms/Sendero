package sendero;

interface BaseMerge<T> {
    <S> void from(
            Merge.Entry<S, T> entry
    );
    <S> boolean drop(
            Merge.Entry<S, T> entry
    );
}
