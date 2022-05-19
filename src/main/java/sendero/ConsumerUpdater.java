package sendero;

import sendero.interfaces.Updater;

import java.util.function.Consumer;

public interface ConsumerUpdater<T> extends Updater<T>, Consumer<T> {
}
