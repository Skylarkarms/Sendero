package sendero;


public interface IBasePath<T> {
    void appoint(BasePath.Receptor<T> receptor);
    void pathDispatch(boolean fullyParallel, Immutable<T> t);
    void demotionOverride(BasePath.Receptor<T> receptor);
}
