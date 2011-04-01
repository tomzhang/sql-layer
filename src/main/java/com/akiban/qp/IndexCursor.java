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

package com.akiban.qp;

import com.akiban.qp.expression.IndexKeyRange;

public interface IndexCursor extends Cursor
{
    /**
     * Starts a scan of the underlying table that will visit index rows whose key is inside the given keyRange.
     *
     * @param keyRange Range of index keys to be visited.
     * @throws UnsupportedOperationException if applied to a group-based cursor.
     */
    void open(IndexKeyRange keyRange);
}
