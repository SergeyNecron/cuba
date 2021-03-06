/*
 * Copyright (c) 2008-2016 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.haulmont.cuba.web.app.domain;

import com.haulmont.cuba.core.app.DomainDescriptionService;
import com.haulmont.cuba.core.global.BeanLocator;
import com.haulmont.cuba.core.sys.BeanLocatorAware;
import com.haulmont.cuba.gui.export.ByteArrayDataProvider;
import com.haulmont.cuba.gui.export.ExportDisplay;
import com.haulmont.cuba.gui.export.ExportFormat;

import java.nio.charset.StandardCharsets;

/**
 * Class providing domain model description. It can be called from the main menu.
 */
public class DomainProvider implements Runnable, BeanLocatorAware {

    protected BeanLocator beanLocator;

    @Override
    public void run() {
        DomainDescriptionService service = beanLocator.get(DomainDescriptionService.NAME);
        String description = service.getDomainDescription();

        ExportDisplay exportDisplay = beanLocator.getPrototype(ExportDisplay.NAME, true);
        exportDisplay.show(new ByteArrayDataProvider(description.getBytes(StandardCharsets.UTF_8)),
                "domain-description.html", ExportFormat.HTML);
    }

    @Override
    public void setBeanLocator(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }
}