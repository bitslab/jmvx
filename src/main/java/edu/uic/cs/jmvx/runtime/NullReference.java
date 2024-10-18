package edu.uic.cs.jmvx.runtime;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

public class NullReference extends SoftReference {
    public NullReference(Object referent) {
        super(referent);
    }

    public NullReference(Object referent, ReferenceQueue q) {
        super(referent, q);
    }

    @Override
    /**
     * Use this to cause caches made with soft refs to always miss
     */
    public Object get() {
        return null;
    }
}
