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

package org.apache.nifi.minifi.c2.provider.filesystem;

import org.apache.nifi.minifi.c2.api.Configuration;
import org.apache.nifi.minifi.c2.api.ConfigurationProvider;
import org.apache.nifi.minifi.c2.api.ConfigurationProviderException;
import org.apache.nifi.minifi.c2.api.cache.ConfigCache;

import java.util.List;
import java.util.Map;

public class FileSystemConfigurationProvider implements ConfigurationProvider {
    private final String contentType;
    private final ConfigCache configCache;

    public FileSystemConfigurationProvider(String contentType, ConfigCache configCache) {
        this.contentType = contentType;
        this.configCache = configCache;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public Configuration getConfiguration(String version, Map<String, List<String>> parameters) throws ConfigurationProviderException {
        return configCache.getConfiguration(version, parameters);
    }
}
