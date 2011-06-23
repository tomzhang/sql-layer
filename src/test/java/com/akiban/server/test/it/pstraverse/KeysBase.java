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

package com.akiban.server.test.it.pstraverse;

import com.akiban.ais.model.Index;
import com.akiban.server.InvalidOperationException;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.test.it.ITBase;
import com.akiban.server.test.it.keyupdate.RecordCollectingIndexKeyRecordVisitor;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public abstract class KeysBase extends ITBase {
    private int customers;
    private int orders;
    private int items;

    protected abstract String ordersPK();
    protected abstract String itemsPK();

    @Before
    public void setUp() throws Exception {
        String schema = "cascading";
        customers = createTable(schema, "customers", "cid int key");
        orders = createTable(schema, "orders",
                "cid int",
                "oid int",
                "PRIMARY KEY("+ordersPK()+")",
                "CONSTRAINT __akiban_o FOREIGN KEY __akiban_o(cid) REFERENCES customers(cid)"
        );
        items = createTable(schema, "items",
                "cid int",
                "oid int",
                "iid int",
                "PRIMARY KEY("+itemsPK()+")",
                "CONSTRAINT __akiban_i FOREIGN KEY __akiban_i("+ordersPK()+") REFERENCES orders("+ordersPK()+")"
        );

        writeRows(
                createNewRow(customers, 71),
                createNewRow(orders, 71, 81),
                createNewRow(items, 71, 81, 91),
                createNewRow(items, 71, 81, 92),
                createNewRow(orders, 72, 82),
                createNewRow(items, 72, 82, 93)

        );
    }

    protected int customers() {
        return customers;
    }

    protected int orders() {
        return orders;
    }

    protected int items() {
        return items;
    }

    @Test // (expected=IllegalArgumentException.class) @SuppressWarnings("unused") // junit will invoke
    public void traverseCustomersPK() throws Exception {
        traversePK(
                customers(),
                Arrays.asList(71L)
        );
    }

    @Test @SuppressWarnings("unused") // junit will invoke
    public void traverseOrdersPK() throws Exception {
        traversePK(
                orders(),
                Arrays.asList(81L, 71L),
                Arrays.asList(82L, 72L)
        );
    }

    @Test @SuppressWarnings("unused") // junit will invoke
    public void traverseItemsPK() throws Exception {
        traversePK(
                items(),
                Arrays.asList(91L, 71L, 81L),
                Arrays.asList(92L, 71L, 81L),
                Arrays.asList(93L, 72L, 82L)
        );
    }

    protected void traversePK(int rowDefId, List<? super Long>... expectedIndexes) throws Exception {
        Index pkIndex = rowDefCache().rowDef(rowDefId).getPKIndex();

        RecordCollectingIndexKeyRecordVisitor visitor = new RecordCollectingIndexKeyRecordVisitor();
        persistitStore().traverse(session(), pkIndex, visitor);

        assertEquals("traversed indexes", Arrays.asList(expectedIndexes), visitor.records());
    }

    @Test @SuppressWarnings("unused") // invoked via JMX
    public void scanCustomers() throws InvalidOperationException {

        List<NewRow> actual = scanAll(scanAllRequest(customers));
        List<NewRow> expected = Arrays.asList(
                createNewRow(customers, 71L)
        );
        assertEquals("rows scanned", expected, actual);
    }

    @Test @SuppressWarnings("unused") // invoked via JMX
    public void scanOrders() throws InvalidOperationException {

        List<NewRow> actual = scanAll(scanAllRequest(orders));
        List<NewRow> expected = Arrays.asList(
                createNewRow(orders, 71L, 81L),
                createNewRow(orders, 72L, 82L)
        );
        assertEquals("rows scanned", expected, actual);
    }

    @Test @SuppressWarnings("unused") // invoked via JMX
    public void scanItems() throws InvalidOperationException {
        List<NewRow> actual = scanAll(scanAllRequest(items));
        List<NewRow> expected = Arrays.asList(
                createNewRow(items, 71L, 81L, 91L),
                createNewRow(items, 71L, 81L, 92L),
                createNewRow(items, 72L, 82L, 93L)
        );
        assertEquals("rows scanned", expected, actual);
    }
}
