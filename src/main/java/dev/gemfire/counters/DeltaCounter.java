package dev.gemfire.counters;

import org.apache.geode.DataSerializable;
import org.apache.geode.DataSerializer;
import org.apache.geode.Delta;
import org.apache.geode.InvalidDeltaException;
import org.apache.geode.logging.internal.log4j.api.LogService;
import org.apache.logging.log4j.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Delta is a hard concept but very powerful.   You can read more here:
 * https://docs.vmware.com/en/VMware-GemFire/10.1/gf/developing-delta_propagation-chapter_overview.html
 *
 * This is the Object that is stored in GemFire.
 *
 */
public class DeltaCounter implements DataSerializable, Delta {

    private AtomicInteger atomicInteger; //This is the object that transfers state from the servers to client.
    private final ReadWriteLock lock = new ReentrantReadWriteLock(); //I want to ensure delta values are sent to the server without missing a delta change
    private final AtomicInteger accumulator = new AtomicInteger(0);

    public DeltaCounter() {
        atomicInteger = new AtomicInteger();
    }

    public DeltaCounter(int value) {
        atomicInteger = new AtomicInteger(value);
    }


    public int increment(int value) {
        try {
            lock.readLock().lock();
            accumulator.addAndGet(value);
            return atomicInteger.addAndGet(value);

        } finally {
            lock.readLock().unlock();
        }
    }

    public int getValue() {
        try {
            lock.readLock().lock();
            return atomicInteger.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void toData(DataOutput dataOutput) throws IOException {
        DataSerializer.writeInteger(atomicInteger.get(), dataOutput);
    }

    @Override
    public void fromData(DataInput dataInput) throws IOException, ClassNotFoundException {
        atomicInteger = new AtomicInteger(dataInput.readInt());
    }

    @Override
    public boolean hasDelta() {
        return accumulator.get() != 0;
    }

    @Override
    public void toDelta(DataOutput dataOutput) throws IOException {
        int value;
        try {
            lock.writeLock().lock();
            value = accumulator.getAndSet(0);
        } finally {
            lock.writeLock().unlock();
        }
        DataSerializer.writePrimitiveInt(value, dataOutput);
    }

    @Override
    public void fromDelta(DataInput dataInput) throws IOException, InvalidDeltaException {
        atomicInteger.addAndGet(DataSerializer.readPrimitiveInt(dataInput));
    }

    @Override
    public String toString() {
        return "DeltaCounter{" +
                "atomicInteger=" + atomicInteger +
                ", accumulator=" + accumulator +
                '}';
    }
}
