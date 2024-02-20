package dev.gemfire.counters;

import org.apache.geode.DataSerializable;
import org.apache.geode.DataSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class DeltaCounterFunctionArguments implements DataSerializable {

    private int maxValue;
    private int deltaChange;
    private String counterName;

    public DeltaCounterFunctionArguments() {
        this("name", 0, 0);
    }

    public DeltaCounterFunctionArguments(String counterName, int deltaChange, int maxValue) {
        this.maxValue = maxValue;
        this.deltaChange = deltaChange;
        this.counterName = counterName;
    }

    public String getCounterName() {
        return counterName;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public int getDeltaChange() {
        return deltaChange;
    }

    @Override
    public void toData(DataOutput dataOutput) throws IOException {
        DataSerializer.writePrimitiveInt(maxValue, dataOutput);
        DataSerializer.writePrimitiveInt(deltaChange, dataOutput);
        DataSerializer.writeString(counterName, dataOutput);
    }

    @Override
    public void fromData(DataInput dataInput) throws IOException, ClassNotFoundException {
        maxValue = DataSerializer.readPrimitiveInt(dataInput);
        deltaChange = DataSerializer.readPrimitiveInt(dataInput);
        counterName = DataSerializer.readString(dataInput);
    }
}
