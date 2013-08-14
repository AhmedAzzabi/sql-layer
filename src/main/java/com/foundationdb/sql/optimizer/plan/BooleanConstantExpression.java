/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.akiban.sql.optimizer.plan;

import com.akiban.server.types.AkType;
import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.sql.parser.ValueNode;

public class BooleanConstantExpression extends ConstantExpression 
                                       implements ConditionExpression 
{
    public BooleanConstantExpression(Object value, 
                                     DataTypeDescriptor sqlType, ValueNode sqlSource) {
        super(value, sqlType, AkType.BOOL, sqlSource);
    }

    public BooleanConstantExpression(Boolean value) {
        super(value, AkType.BOOL);
    }
    
    @Override
    public Implementation getImplementation() {
        return null;
    }

}
