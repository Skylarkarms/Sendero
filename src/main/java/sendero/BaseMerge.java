package sendero;

interface BaseMerge<T> {
    void from(
            Merge.Entry<T> entry
    );
    boolean drop(
            Merge.Entry<T> entry
    );
}
