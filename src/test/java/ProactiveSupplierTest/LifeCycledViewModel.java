package ProactiveSupplierTest;

import sendero.Path;
import sendero.ProactiveSupplier;
import sendero.ProactiveSuppliers;
import sendero.event_registers.BinaryEventRegisters;

import java.util.function.Supplier;

public class LifeCycledViewModel extends BinaryEventRegisters.SwitchSynchronizerImpl<Path<SwitchSwitchMapTest.Result>> implements Supplier<SwitchSwitchMapTest.Result> {

    Path<SwitchSwitchMapTest.Result> toBound = SwitchSwitchMapTest.getResult();
    ProactiveSupplier<SwitchSwitchMapTest.Result> resultSupp = putIfAbsent(toBound, ProactiveSuppliers.Bound.bound(
            toBound
    ));

//    @Override
//    protected void onStateChange(boolean isActive) {
//        if (isActive) resultSupp.start();
//        else resultSupp.shutoff();
//    }

    @Override
    public SwitchSwitchMapTest.Result get() {
        return resultSupp.get();
    }
}
