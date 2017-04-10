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

package org.apache.nifi.minifi.codegen.schema;

import java.util.List;

public class EnumDefinition {
    private String packageName;
    private SchemaDefinition parent;
    private String name;
    private boolean caseSensitive;
    private List<String> values;

    public SchemaDefinition getParent() {
        return parent;
    }

    public void setParent(SchemaDefinition parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public String getPackage() {
        return packageName == null ? parent.getPackage() : packageName;
    }

    public void setPackage(String packageName) {
        this.packageName = packageName;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
}
