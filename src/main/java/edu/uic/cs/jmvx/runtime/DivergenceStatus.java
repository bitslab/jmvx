package edu.uic.cs.jmvx.runtime;

public class DivergenceStatus<T> {
    public boolean hasDivergenceOccured = false;
    public T returnValue; // stuff the exception here in case the lambda throws an exception
    public boolean containsException = false;

    public enum STATUS { OK, NOT_OK };

}