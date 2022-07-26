package ProactiveSupplierTest;

import sendero.ProactiveSupplier;
import sendero.ProactiveSuppliers;
import sendero.event_registers.BinaryEventRegisters;

import java.util.function.Supplier;

public class LifeCycledViewModel extends BinaryEventRegisters.NonConcurrentToMany implements Supplier<SwitchSwitchMapTest.Result> {

    ProactiveSupplier<SwitchSwitchMapTest.Result> resultSupp = add(ProactiveSuppliers.Bound.bound(
            SwitchSwitchMapTest.getResult()
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
