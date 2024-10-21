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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.queryengine.execution.operator.source.relational.aggregation.grouped;

import org.apache.iotdb.db.queryengine.execution.operator.source.relational.aggregation.grouped.array.BinaryBigArray;
import org.apache.iotdb.db.queryengine.execution.operator.source.relational.aggregation.grouped.array.BooleanBigArray;
import org.apache.iotdb.db.queryengine.execution.operator.source.relational.aggregation.grouped.array.DoubleBigArray;
import org.apache.iotdb.db.queryengine.execution.operator.source.relational.aggregation.grouped.array.FloatBigArray;
import org.apache.iotdb.db.queryengine.execution.operator.source.relational.aggregation.grouped.array.IntBigArray;
import org.apache.iotdb.db.queryengine.execution.operator.source.relational.aggregation.grouped.array.LongBigArray;

import org.apache.tsfile.block.column.Column;
import org.apache.tsfile.block.column.ColumnBuilder;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.read.common.block.column.BinaryColumn;
import org.apache.tsfile.read.common.block.column.BinaryColumnBuilder;
import org.apache.tsfile.read.common.block.column.RunLengthEncodedColumn;
import org.apache.tsfile.utils.Binary;
import org.apache.tsfile.utils.BytesUtils;
import org.apache.tsfile.utils.RamUsageEstimator;
import org.apache.tsfile.write.UnSupportedDataTypeException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.iotdb.db.utils.constant.SqlConstant.LAST_BY_AGGREGATION;

public class GroupedFirstByAccumulator implements GroupedAccumulator {

  private static final long INSTANCE_SIZE =
      RamUsageEstimator.shallowSizeOfInstance(GroupedFirstByAccumulator.class);

  private final TSDataType xDataType;
  private final TSDataType yDataType;

  private final LongBigArray yFirstTimes = new LongBigArray(Long.MAX_VALUE);

  private final BooleanBigArray inits = new BooleanBigArray();

  private LongBigArray xLongValues;
  private IntBigArray xIntValues;
  private FloatBigArray xFloatValues;
  private DoubleBigArray xDoubleValues;
  private BinaryBigArray xBinaryValues;
  private BooleanBigArray xBooleanValues;

  private final BooleanBigArray xNulls = new BooleanBigArray(true);

  public GroupedFirstByAccumulator(TSDataType xDataType, TSDataType yDataType) {
    this.xDataType = xDataType;
    this.yDataType = yDataType;

    switch (xDataType) {
      case INT32:
      case DATE:
        xIntValues = new IntBigArray();
        break;
      case INT64:
      case TIMESTAMP:
        xLongValues = new LongBigArray();
        break;
      case FLOAT:
        xFloatValues = new FloatBigArray();
        break;
      case DOUBLE:
        xDoubleValues = new DoubleBigArray();
        break;
      case TEXT:
      case BLOB:
      case STRING:
        xBinaryValues = new BinaryBigArray();
        break;
      case BOOLEAN:
        xBooleanValues = new BooleanBigArray();
      default:
        throw new UnSupportedDataTypeException(
            String.format("Unsupported data type : %s", xDataType));
    }
  }

  @Override
  public long getEstimatedSize() {
    long valuesSize = 0;
    switch (xDataType) {
      case INT32:
      case DATE:
        valuesSize += xIntValues.sizeOf();
        break;
      case INT64:
      case TIMESTAMP:
        valuesSize += xLongValues.sizeOf();
        break;
      case FLOAT:
        valuesSize += xFloatValues.sizeOf();
        break;
      case DOUBLE:
        valuesSize += xDoubleValues.sizeOf();
        break;
      case TEXT:
      case STRING:
      case BLOB:
        valuesSize += xBinaryValues.sizeOf();
        break;
      case BOOLEAN:
        valuesSize += xBooleanValues.sizeOf();
        break;
      default:
        throw new UnSupportedDataTypeException(
            String.format("Unsupported data type in : %s", xDataType));
    }

    return INSTANCE_SIZE + valuesSize + yFirstTimes.sizeOf() + inits.sizeOf() + xNulls.sizeOf();
  }

  @Override
  public void setGroupCount(long groupCount) {
    yFirstTimes.ensureCapacity(groupCount);
    inits.ensureCapacity(groupCount);
    xNulls.ensureCapacity(groupCount);
    switch (xDataType) {
      case INT32:
      case DATE:
        xIntValues.ensureCapacity(groupCount);
        return;
      case INT64:
      case TIMESTAMP:
        xLongValues.ensureCapacity(groupCount);
        return;
      case FLOAT:
        xFloatValues.ensureCapacity(groupCount);
        return;
      case DOUBLE:
        xDoubleValues.ensureCapacity(groupCount);
        return;
      case TEXT:
      case STRING:
      case BLOB:
        xBinaryValues.ensureCapacity(groupCount);
        return;
      case BOOLEAN:
        xBooleanValues.ensureCapacity(groupCount);
        return;
      default:
        throw new UnSupportedDataTypeException(
            String.format("Unsupported data type in : %s", xDataType));
    }
  }

  @Override
  public void prepareFinal() {}

  @Override
  public void addInput(int[] groupIds, Column[] arguments) {
    checkArgument(arguments.length == 3, "Length of input Column[] for LastBy/FirstBy should be 3");

    // arguments[0] is x column, arguments[1] is y column, arguments[2] is time column
    switch (xDataType) {
      case INT32:
      case DATE:
        addIntInput(groupIds, arguments[0], arguments[1], arguments[2]);
        return;
      case INT64:
      case TIMESTAMP:
        addLongInput(groupIds, arguments[0], arguments[1], arguments[2]);
        return;
      case FLOAT:
        addFloatInput(groupIds, arguments[0], arguments[1], arguments[2]);
        return;
      case DOUBLE:
        addDoubleInput(groupIds, arguments[0], arguments[1], arguments[2]);
        return;
      case TEXT:
      case STRING:
      case BLOB:
        addBinaryInput(groupIds, arguments[0], arguments[1], arguments[2]);
        return;
      case BOOLEAN:
        addBooleanInput(groupIds, arguments[0], arguments[1], arguments[2]);
        return;
      default:
        throw new UnSupportedDataTypeException(
            String.format("Unsupported data type in LastBy: %s", yDataType));
    }
  }

  @Override
  public void addIntermediate(int[] groupIds, Column argument) {
    checkArgument(
        argument instanceof BinaryColumn || argument instanceof RunLengthEncodedColumn,
        "intermediate input and output of LastBy should be BinaryColumn");

    for (int i = 0; i < argument.getPositionCount(); i++) {
      if (argument.isNull(i)) {
        continue;
      }

      byte[] bytes = argument.getBinary(i).getValues();
      long curTime = BytesUtils.bytesToLongFromOffset(bytes, Long.BYTES, 0);
      int offset = Long.BYTES;
      boolean isXNull = BytesUtils.bytesToBool(bytes, offset);
      offset += 1;
      int groupId = groupIds[i];

      if (isXNull) {
        if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
          inits.set(groupId, true);
          yFirstTimes.set(groupId, curTime);
          xNulls.set(groupId, true);
        }
        continue;
      }

      switch (xDataType) {
        case INT32:
        case DATE:
          int xIntVal = BytesUtils.bytesToInt(bytes, offset);
          updateIntLastValue(groupId, xIntVal, curTime);
          break;
        case INT64:
        case TIMESTAMP:
          long longVal = BytesUtils.bytesToLongFromOffset(bytes, Long.BYTES, offset);
          updateLongLastValue(groupId, longVal, curTime);
          break;
        case FLOAT:
          float floatVal = BytesUtils.bytesToFloat(bytes, offset);
          updateFloatLastValue(groupId, floatVal, curTime);
          break;
        case DOUBLE:
          double doubleVal = BytesUtils.bytesToDouble(bytes, offset);
          updateDoubleLastValue(groupId, doubleVal, curTime);
          break;
        case TEXT:
        case BLOB:
        case STRING:
          int length = BytesUtils.bytesToInt(bytes, offset);
          offset += Integer.BYTES;
          Binary binaryVal = new Binary(BytesUtils.subBytes(bytes, offset, length));
          updateBinaryLastValue(groupId, binaryVal, curTime);
          break;
        case BOOLEAN:
          boolean boolVal = BytesUtils.bytesToBool(bytes, offset);
          updateBooleanLastValue(groupId, boolVal, curTime);
          break;
        default:
          throw new UnSupportedDataTypeException(
              String.format("Unsupported data type in First Aggregation: %s", yDataType));
      }
    }
  }

  @Override
  public void evaluateIntermediate(int groupId, ColumnBuilder columnBuilder) {
    checkArgument(
        columnBuilder instanceof BinaryColumnBuilder,
        "intermediate input and output of FirstBy should be BinaryColumn");

    if (!inits.get(groupId)) {
      columnBuilder.appendNull();
    } else {
      columnBuilder.writeBinary(new Binary(serializeTimeWithValue(groupId)));
    }
  }

  private byte[] serializeTimeWithValue(int groupId) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
    try {
      dataOutputStream.writeLong(yFirstTimes.get(groupId));
      dataOutputStream.writeBoolean(xNulls.get(groupId));
      if (!xNulls.get(groupId)) {
        switch (xDataType) {
          case INT32:
          case DATE:
            dataOutputStream.writeInt(xIntValues.get(groupId));
            break;
          case INT64:
          case TIMESTAMP:
            dataOutputStream.writeLong(xLongValues.get(groupId));
            break;
          case FLOAT:
            dataOutputStream.writeFloat(xFloatValues.get(groupId));
            break;
          case DOUBLE:
            dataOutputStream.writeDouble(xDoubleValues.get(groupId));
            break;
          case TEXT:
          case BLOB:
          case STRING:
            dataOutputStream.writeInt(xBinaryValues.get(groupId).getValues().length);
            dataOutputStream.write(xBinaryValues.get(groupId).getValues());
            break;
          case BOOLEAN:
            dataOutputStream.writeBoolean(xBooleanValues.get(groupId));
            break;
          default:
            throw new UnSupportedDataTypeException(
                String.format(
                    "Unsupported data type: %s in aggregation %s", xDataType, LAST_BY_AGGREGATION));
        }
      }
    } catch (IOException e) {
      throw new UnsupportedOperationException(
          String.format(
              "Failed to serialize intermediate result for Accumulator %s, errorMsg: %s.",
              LAST_BY_AGGREGATION, e.getMessage()));
    }
    return byteArrayOutputStream.toByteArray();
  }

  @Override
  public void evaluateFinal(int groupId, ColumnBuilder columnBuilder) {
    if (!inits.get(groupId) || xNulls.get(groupId)) {
      columnBuilder.appendNull();
      return;
    }

    switch (xDataType) {
      case INT32:
      case DATE:
        columnBuilder.writeInt(xIntValues.get(groupId));
        break;
      case INT64:
      case TIMESTAMP:
        columnBuilder.writeLong(xLongValues.get(groupId));
        break;
      case FLOAT:
        columnBuilder.writeFloat(xFloatValues.get(groupId));
        break;
      case DOUBLE:
        columnBuilder.writeDouble(xDoubleValues.get(groupId));
        break;
      case TEXT:
      case BLOB:
      case STRING:
        columnBuilder.writeBinary(xBinaryValues.get(groupId));
        break;
      case BOOLEAN:
        columnBuilder.writeBoolean(xBooleanValues.get(groupId));
        break;
      default:
        throw new UnSupportedDataTypeException(
            String.format("Unsupported data type in LastBy: %s", xDataType));
    }
  }

  private void addIntInput(int[] groupIds, Column xColumn, Column yColumn, Column timeColumn) {
    for (int i = 0; i < groupIds.length; i++) {
      if (!yColumn.isNull(i)) {
        updateIntLastValue(groupIds[i], xColumn, i, timeColumn.getLong(i));
      }
    }
  }

  protected void updateIntLastValue(int groupId, Column xColumn, int xIdx, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      if (xColumn.isNull(xIdx)) {
        xNulls.set(groupId, true);
      } else {
        xNulls.set(groupId, false);
        xIntValues.set(groupId, xColumn.getInt(xIdx));
      }
    }
  }

  protected void updateIntLastValue(int groupId, int val, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      xNulls.set(groupId, false);
      xIntValues.set(groupId, val);
    }
  }

  private void addLongInput(int[] groupIds, Column xColumn, Column yColumn, Column timeColumn) {
    for (int i = 0; i < yColumn.getPositionCount(); i++) {
      if (!yColumn.isNull(i)) {
        updateLongLastValue(groupIds[i], xColumn, i, timeColumn.getLong(i));
      }
    }
  }

  protected void updateLongLastValue(int groupId, Column xColumn, int xIdx, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      if (xColumn.isNull(xIdx)) {
        xNulls.set(groupId, true);
      } else {
        xNulls.set(groupId, false);
        xLongValues.set(groupId, xColumn.getLong(xIdx));
      }
    }
  }

  protected void updateLongLastValue(int groupId, long val, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      xNulls.set(groupId, false);
      xLongValues.set(groupId, val);
    }
  }

  private void addFloatInput(int[] groupIds, Column xColumn, Column yColumn, Column timeColumn) {
    for (int i = 0; i < yColumn.getPositionCount(); i++) {
      if (!yColumn.isNull(i)) {
        updateFloatLastValue(groupIds[i], xColumn, i, timeColumn.getLong(i));
      }
    }
  }

  protected void updateFloatLastValue(int groupId, Column xColumn, int xIdx, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      if (xColumn.isNull(xIdx)) {
        xNulls.set(groupId, true);
      } else {
        xNulls.set(groupId, false);
        xFloatValues.set(groupId, xColumn.getFloat(xIdx));
      }
    }
  }

  protected void updateFloatLastValue(int groupId, float val, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      xNulls.set(groupId, false);
      xFloatValues.set(groupId, val);
    }
  }

  private void addDoubleInput(int[] groupIds, Column xColumn, Column yColumn, Column timeColumn) {
    for (int i = 0; i < yColumn.getPositionCount(); i++) {
      if (!yColumn.isNull(i)) {
        updateDoubleLastValue(groupIds[i], xColumn, i, timeColumn.getLong(i));
      }
    }
  }

  protected void updateDoubleLastValue(int groupId, Column xColumn, int xIdx, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      if (xColumn.isNull(xIdx)) {
        xNulls.set(groupId, true);
      } else {
        xNulls.set(groupId, false);
        xDoubleValues.set(groupId, xColumn.getDouble(xIdx));
      }
    }
  }

  protected void updateDoubleLastValue(int groupId, double val, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      xNulls.set(groupId, false);
      xDoubleValues.set(groupId, val);
    }
  }

  private void addBinaryInput(int[] groupIds, Column xColumn, Column yColumn, Column timeColumn) {
    for (int i = 0; i < yColumn.getPositionCount(); i++) {
      if (!yColumn.isNull(i)) {
        updateBinaryLastValue(groupIds[i], xColumn, i, timeColumn.getLong(i));
      }
    }
  }

  protected void updateBinaryLastValue(int groupId, Column xColumn, int xIdx, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      if (xColumn.isNull(xIdx)) {
        xNulls.set(groupId, true);
      } else {
        xNulls.set(groupId, false);
        xIntValues.set(groupId, xColumn.getInt(xIdx));
      }
    }
  }

  protected void updateBinaryLastValue(int groupId, Binary val, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      xNulls.set(groupId, false);
      xBinaryValues.set(groupId, val);
    }
  }

  private void addBooleanInput(int[] groupIds, Column xColumn, Column yColumn, Column timeColumn) {
    for (int i = 0; i < yColumn.getPositionCount(); i++) {
      if (!yColumn.isNull(i)) {
        updateBooleanLastValue(groupIds[i], xColumn, i, timeColumn.getLong(i));
      }
    }
  }

  protected void updateBooleanLastValue(int groupId, Column xColumn, int xIdx, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      if (xColumn.isNull(xIdx)) {
        xNulls.set(groupId, true);
      } else {
        xNulls.set(groupId, false);
        xBooleanValues.set(groupId, xColumn.getBoolean(xIdx));
      }
    }
  }

  protected void updateBooleanLastValue(int groupId, boolean val, long curTime) {
    if (!inits.get(groupId) || curTime < yFirstTimes.get(groupId)) {
      inits.set(groupId, true);
      yFirstTimes.set(groupId, curTime);
      xNulls.set(groupId, false);
      xBooleanValues.set(groupId, val);
    }
  }
}