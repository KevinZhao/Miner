package com.miner.obd;

public interface ObdProgressListener {

    void stateUpdate(final ObdCommandJob job);

}