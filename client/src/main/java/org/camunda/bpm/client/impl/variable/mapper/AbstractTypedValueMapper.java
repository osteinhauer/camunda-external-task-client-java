/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.client.impl.variable.mapper;

import org.camunda.bpm.client.impl.variable.TypedValueField;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;

public abstract class AbstractTypedValueMapper<T extends TypedValue> implements ValueMapper<T> {

  protected ValueType valueType;

  public AbstractTypedValueMapper(ValueType type) {
    valueType = type;
  }

  public ValueType getType() {
    return valueType;
  }

  public String getSerializationDataformat() {
    // default implementation returns null
    return null;
  }

  public boolean canHandleTypedValue(TypedValue typedValue) {
    ValueType type = typedValue.getType();
    return (type == null || valueType.getClass().isAssignableFrom(type.getClass())) && canWriteValue(typedValue);
  }

  public boolean canHandleTypedValueField(TypedValueField typedValueField) {
    String type = typedValueField.getType();
    return type != null && type.equals(valueType.getName()) && canReadValue(typedValueField);
  }

  protected abstract boolean canWriteValue(TypedValue typedValue);

  protected abstract boolean canReadValue(TypedValueField typedValueField);

}
