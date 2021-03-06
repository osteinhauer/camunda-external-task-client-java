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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.client.impl.variable.TypedValueField;
import org.camunda.bpm.client.impl.variable.ValueMappers;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;

public class DefaultValueMappers implements Serializable, ValueMappers {

  private static final long serialVersionUID = 1L;

  protected List<ValueMapper<?>> serializerList = new ArrayList<ValueMapper<?>>();
  protected String defaultSerializationFormat;

  public DefaultValueMappers(String defaultSerializationFormat) {
    this.defaultSerializationFormat = defaultSerializationFormat;
  }

  public ValueMapper<?> findMapperForTypedValue(TypedValue typedValue) {
    ValueType type = typedValue.getType();

    if (type != null && type.isAbstract()) {
      throw new RuntimeException("Cannot serialize value of abstract type " + type.getName());
    }

    List<ValueMapper<?>> matchedSerializers = new ArrayList<ValueMapper<?>>();

    for (ValueMapper<?> serializer : serializerList) {
      if(serializer.canHandleTypedValue(typedValue)) {
        matchedSerializers.add(serializer);
        if(serializer.getType().isPrimitiveValueType()) {
          break;
        }
      }
    }

    if(matchedSerializers.size() == 1) {
      return matchedSerializers.get(0);
    }
    else if (matchedSerializers.size() > 1) {
      // ambiguous match, use default serializer
      return matchedSerializers.stream()
        .filter(serializer -> defaultSerializationFormat.equals(serializer.getSerializationDataformat()))
        .findFirst()
        .orElse(matchedSerializers.get(0));
    }
    else {
      throw new RuntimeException("Cannot find serializer for value '"+typedValue+"'.");
    }
  }

  @Override
  public ValueMapper<?> findMapperForTypedValueField(TypedValueField typedValueField) {
    ValueMapper<?> matchedSerializer = serializerList.stream()
      .filter(serializer -> serializer.canHandleTypedValueField(typedValueField))
      .findFirst()
      .orElse(null);

    if (matchedSerializer == null) {
      throw new RuntimeException("Cannot find serializer for value '"+typedValueField+"'.");
    }

    return matchedSerializer;
  }

  public DefaultValueMappers addSerializer(ValueMapper<?> serializer) {
    serializerList.add(serializer);
    return this;
  }
}
