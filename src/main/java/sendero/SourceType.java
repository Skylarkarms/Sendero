package sendero;

public enum SourceType {
    /**A source from type stream means that this signal comes from a parent producer Path*/
    stream,
    /**A source of type client means the signal comes directly from an Input from client*/
    client
}
