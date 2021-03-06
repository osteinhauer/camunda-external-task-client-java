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
package org.camunda.bpm.client.impl.variable;

import org.camunda.bpm.client.impl.variable.mapper.ValueMapper;
import org.camunda.bpm.engine.variable.value.TypedValue;

public interface ValueMappers {

  @SuppressWarnings("rawtypes")
  public ValueMapper findMapperForTypedValue(TypedValue value);

  @SuppressWarnings("rawtypes")
  public ValueMapper findMapperForTypedValueField(TypedValueField value);

  public ValueMappers addSerializer(ValueMapper<?> serializer);

}
