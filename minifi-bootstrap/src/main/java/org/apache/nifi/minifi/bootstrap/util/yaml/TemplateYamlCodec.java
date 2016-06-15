/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.minifi.bootstrap.util.yaml;

import org.apache.nifi.controller.Template;
import org.apache.nifi.minifi.bootstrap.util.TemplateCodec;
import org.apache.nifi.web.api.dto.TemplateDTO;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeId;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TemplateYamlCodec implements TemplateCodec {
    @Override
    public void write(Template template, Writer writer) throws IOException {
        DumperOptions dumperOptions = new DumperOptions();
        Yaml yaml = new Yaml(new DTORepresenter(), dumperOptions);
        yaml.dump(template.getDetails(), writer);
    }

    @Override
    public Template read(Reader reader) throws IOException {
        try {
            return new Template((TemplateDTO) new Yaml(new BooleanCompatibleConstructor()).load(reader));
        } catch (ClassCastException e) {
            throw new IOException("Was expecting TemplateDTO at root of file.", e);
        } catch (YAMLException e) {
            throw new IOException("Unable to parse yaml.", e);
        }
    }

    protected class BooleanCompatibleConstructor extends Constructor {
        public BooleanCompatibleConstructor() {
            yamlClassConstructors.put(NodeId.mapping, new ConstructMapping() {
                private Map<Class<?>, Map<String, Property>> booleanProps = new HashMap<>();

                @Override
                protected Property getProperty(Class<? extends Object> type, String name) throws IntrospectionException {
                    try {
                        return super.getProperty(type, name);
                    } catch (YAMLException e) {
                        Map<String, Property> namePropertyMap = booleanProps.get(type);
                        if (namePropertyMap == null) {
                            namePropertyMap = new HashMap<>();
                            booleanProps.put(type, namePropertyMap);
                        }
                        Property property = namePropertyMap.get(name);
                        if (property == null) {
                            if (namePropertyMap.containsKey(name)) {
                                throw e;
                            } else {
                                Optional<Property> first = DTORepresenter.getBooleanProperties(type).stream()
                                        .filter(booleanProperty -> booleanProperty.getName().equals(name))
                                        .findFirst();
                                namePropertyMap.put(name, first.orElse(null));
                                return first.orElseThrow(() -> e);
                            }
                        } else {
                            return property;
                        }
                    }
                }
            });
        }
    }
}
