package sendero;

import java.util.function.Function;

interface PathListener<T> {
    /**Returns previous Path
     * @return*/
    <S, P extends BasePath<S>> BasePath.Appointer<?> setPath(P basePath, Function<S, T> map);
    <S, P extends BasePath<S>> void setAndStart(P basePath, Function<S, T> map);
//    <S, P extends BasePath<S>> void listen(P basePath, Function<S, T> map);
    <P extends BasePath<T>> void setAndStart(P basePath);
    void stopPath();
    void startPath();
    void stopAndClearPath();

    void clearAndDemote(BasePath.Appointer<?> expect);
    void clear(BasePath.Appointer<?> expect);
    BasePath.Appointer<?> getAndClear();
}
