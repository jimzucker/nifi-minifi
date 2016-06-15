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

package org.apache.nifi.minifi.bootstrap.util.jaxb;

import org.apache.nifi.controller.Template;
import org.apache.nifi.minifi.bootstrap.util.TemplateCodec;
import org.apache.nifi.web.api.dto.TemplateDTO;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class TemplateJaxbCodec implements TemplateCodec {
    @Override
    public Template read(Reader reader) throws IOException {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(TemplateDTO.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            TemplateDTO templateDTO = (TemplateDTO) unmarshaller.unmarshal(reader);
            return new Template(templateDTO);
        } catch (JAXBException e) {
            throw new IOException("Unable to read template.", e);
        }
    }

    @Override
    public void write(Template template, Writer writer) throws IOException {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(TemplateDTO.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(template.getDetails(), writer);
        } catch (JAXBException e) {
            throw new IOException("Unable to write template.", e);
        }
    }
}