package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.runtime.DivergenceStatus;

public class HandlerStatus<T> {
    DivergenceStatus.STATUS status;
    T returnValue;

    public HandlerStatus(DivergenceStatus.STATUS status, T returnValue) {
        this.status = status;
        this.returnValue = returnValue;
    }
}
