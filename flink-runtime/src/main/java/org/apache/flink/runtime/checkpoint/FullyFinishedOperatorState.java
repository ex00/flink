/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.checkpoint;

import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.state.memory.ByteStreamStateHandle;

import javax.annotation.Nullable;

/**
 * A special operator state implementation representing the operators whose instances are all
 * finished.
 */
public class FullyFinishedOperatorState extends OperatorState {

    private static final long serialVersionUID = 1L;

    public FullyFinishedOperatorState(
            @Nullable String operatorName,
            @Nullable String operatorUid,
            OperatorID operatorID,
            int parallelism,
            int maxParallelism) {
        super(operatorName, operatorUid, operatorID, parallelism, maxParallelism);
    }

    @Override
    public boolean isFullyFinished() {
        return true;
    }

    @Override
    public void putState(int subtaskIndex, OperatorSubtaskState subtaskState) {
        throw new UnsupportedOperationException(
                "Could not put state to a fully finished operator state.");
    }

    @Override
    public void setCoordinatorState(@Nullable ByteStreamStateHandle coordinatorState) {
        throw new UnsupportedOperationException(
                "Could not set coordinator state to a fully finished operator state.");
    }

    @Override
    public OperatorState copyAndDiscardInFlightData() {
        return new FullyFinishedOperatorState(
                getOperatorName().orElse(null),
                getOperatorUid().orElse(null),
                getOperatorID(),
                getParallelism(),
                getMaxParallelism());
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FullyFinishedOperatorState) {
            return super.equals(obj);
        }

        return false;
    }

    @Override
    public String toString() {
        return "FullyFinishedOperatorState("
                + "name: "
                + getOperatorName()
                + "uid: "
                + getOperatorUid()
                + "operatorID: "
                + getOperatorID()
                + ", parallelism: "
                + getParallelism()
                + ", maxParallelism: "
                + getMaxParallelism()
                + ')';
    }
}
