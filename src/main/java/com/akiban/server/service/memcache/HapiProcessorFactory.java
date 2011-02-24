/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.service.memcache;

import com.akiban.server.api.HapiProcessor;
import com.akiban.server.service.memcache.hprocessor.CachedProcessor;
import com.akiban.server.service.memcache.hprocessor.EmptyRows;
import com.akiban.server.service.memcache.hprocessor.Fetchrows;
import com.akiban.server.service.memcache.hprocessor.Scanrows;

@SuppressWarnings("unused")
public // jmx
enum HapiProcessorFactory {
    FETCHROWS(Fetchrows.instance()),
    EMPTY(EmptyRows.instance()),
    SCANROWS(null) {
        @Override
        public HapiProcessor getHapiProcessor() {
            return Scanrows.instance();
        }
    },
    CACHED(new CachedProcessor())
    ;

    private final HapiProcessor processor;

    HapiProcessorFactory(HapiProcessor processor) {
        this.processor = processor;
    }

    public HapiProcessor getHapiProcessor() {
        return processor;
    }
}
