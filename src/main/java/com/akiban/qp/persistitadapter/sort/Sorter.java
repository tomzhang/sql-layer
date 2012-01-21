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

package com.akiban.qp.persistitadapter.sort;

import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.OperatorExecutionBase;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.qp.row.Row;
import com.akiban.qp.row.ValuesHolderRow;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.PersistitKeyValueTarget;
import com.akiban.server.PersistitValueValueSource;
import com.akiban.server.PersistitValueValueTarget;
import com.akiban.server.error.PersistitAdapterException;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.std.LiteralExpression;
import com.akiban.server.service.session.Session;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.conversion.Converters;
import com.akiban.server.types.util.ValueHolder;
import com.persistit.*;
import com.persistit.exception.PersistitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Sorter
{
    public Sorter(PersistitAdapter adapter, 
                  Cursor input, 
                  RowType rowType, 
                  API.Ordering ordering,
                  API.SortOption sortOption,
                  QueryContext context)
        throws PersistitException
    {
        this.adapter = adapter;
        this.input = input;
        this.queryStartTimeMsec = context.getStartTime();
        this.rowType = rowType;
        this.ordering = ordering.copy();
        this.context = context;
        String sortTreeName = SORT_TREE_NAME_PREFIX + SORTER_ID_GENERATOR.getAndIncrement();
        this.exchange =
            SORT_USING_TEMP_VOLUME
            ? exchange(adapter, sortTreeName)
            : adapter.takeExchangeForSorting(new SortTreeLink(sortTreeName));
        this.key = exchange.getKey();
        this.keyTarget = new PersistitKeyValueTarget();
        this.keyTarget.attach(this.key);
        this.value = exchange.getValue();
        this.valueTarget = new PersistitValueValueTarget();
        this.valueTarget.attach(this.value);
        this.rowFields = rowType.nFields();
        this.fieldTypes = new AkType[this.rowFields];
        for (int i = 0; i < rowFields; i++) {
            fieldTypes[i] = rowType.typeAt(i);
        }
        preserveDuplicates = sortOption == API.SortOption.PRESERVE_DUPLICATES;
        if (preserveDuplicates) {
            // Append a count field as a sort key, to ensure key uniqueness for Persisit. By setting
            // the ascending flag equal to that of some other sort field, we don't change an all-ASC or all-DESC sort
            // into a less efficient mixed-mode sort.
            this.ordering.append(DUMMY_EXPRESSION, ordering.ascending(0));
        }
        int nsort = this.ordering.sortColumns();
        this.evaluations = new ArrayList<ExpressionEvaluation>(nsort);
        this.orderingTypes = new AkType[nsort];
        for (int i = 0; i < nsort; i++) {
            orderingTypes[i] = this.ordering.type(i);
            ExpressionEvaluation evaluation = this.ordering.expression(i).evaluation();
            evaluation.of(context);
            evaluations.add(evaluation);
        }
    }

    public Cursor sort() throws PersistitException
    {
        loadTree();
        return cursor();
    }

    void close()
    {
        if (exchange != null) {
            try {
                if (SORT_USING_TEMP_VOLUME) {
                    TempVolumeState tempVolumeState = adapter.session().get(TEMP_VOLUME_STATE);
                    int sortsInProgress = tempVolumeState.endSort();
                    if (sortsInProgress == 0) {
                        // Returns disk space used by the volume
                        tempVolumeState.volume().close();
                        adapter.session().remove(TEMP_VOLUME_STATE);
                    }
                } else {
                    exchange.removeTree();
                }
            } catch (PersistitException e) {
                adapter.handlePersistitException(e);
            } finally {
                // Don't return the exchange. TreeServiceImpl caches it for the tree, and we're done with the tree.
                // THIS CAUSES A LEAK OF EXCHANGES: adapter.returnExchange(exchange);
                exchange = null;
            }
        }
    }

    private void loadTree() throws PersistitException
    {
        try {
            Row row;
            while ((row = input.next()) != null) {
                adapter.checkQueryCancelation(queryStartTimeMsec);
                createKey(row);
                createValue(row);
                exchange.store();
            }
        } catch (PersistitException e) {
            LOG.error("Caught exception while loading tree for sort", e);
            exchange.removeAll();
            adapter.handlePersistitException(e);
        }
    }

    private Cursor cursor()
    {
        exchange.clear();
        SortCursor cursor = SortCursor.create(adapter, context, null, ordering, new SorterIterationHelper());
        cursor.open();
        return cursor;
    }

    private void createKey(Row row)
    {
        key.clear();
        int sortFields = ordering.sortColumns() - (preserveDuplicates ? 1 : 0);
        for (int i = 0; i < sortFields; i++) {
            ExpressionEvaluation evaluation = evaluations.get(i);
            evaluation.of(row);
            ValueSource keySource = evaluation.eval();
            keyTarget.expectingType(orderingTypes[i]);
            Converters.convert(keySource, keyTarget);
        }
        if (preserveDuplicates) {
            key.append(rowCount++);
        }
    }

    private void createValue(Row row)
    {
        value.clear();
        value.setStreamMode(true);
        for (int i = 0; i < rowFields; i++) {
            ValueSource field = row.eval(i);
            valueTarget.expectingType(fieldTypes[i]);
            Converters.convert(field, valueTarget);
        }
    }

    private static Exchange exchange(PersistitAdapter adapter, String treeName) throws PersistitException
    {
        Session session = adapter.session();
        Persistit persistit = adapter.persistit().getDb();
        TempVolumeState tempVolumeState = session.get(TEMP_VOLUME_STATE);
        if (tempVolumeState == null) {
            // Persistit creates a temp volume per "Persistit session", and these are currently one-to-one with threads.
            // Conveniently, server sessions and threads are also one-to-one. If either of these relationships ever
            // change, then the use of session resources and temp volumes will need to be revisited. But for now,
            // persistit.createTemporaryVolume creates a temp volume that is private to the persistit session and
            // therefore to the server session.
            Volume volume = persistit.createTemporaryVolume();
            tempVolumeState = new TempVolumeState(volume);
            session.put(TEMP_VOLUME_STATE, tempVolumeState);
        }
        tempVolumeState.startSort();
        return new Exchange(persistit, tempVolumeState.volume(), treeName, true);
    }

    // Class state

    private static final Logger LOG = LoggerFactory.getLogger(Sorter.class);
    private static final Expression DUMMY_EXPRESSION = LiteralExpression.forNull();
    private static final String SORT_TREE_NAME_PREFIX = "sort.";
    private static final AtomicLong SORTER_ID_GENERATOR = new AtomicLong(0);
    private static final boolean SORT_USING_TEMP_VOLUME =
        System.getProperty("sorttemp", "true").toLowerCase().equals("true");
    private static final Session.Key<TempVolumeState> TEMP_VOLUME_STATE = Session.Key.named("TEMP_VOLUME_STATE");

    // Object state

    final PersistitAdapter adapter;
    final Cursor input;
    final RowType rowType;
    final API.Ordering ordering;
    final boolean preserveDuplicates;
    final List<ExpressionEvaluation> evaluations;
    final QueryContext context;
    final Key key;
    final Value value;
    final PersistitKeyValueTarget keyTarget;
    final PersistitValueValueTarget valueTarget;
    final int rowFields;
    final AkType fieldTypes[], orderingTypes[];
    Exchange exchange;
    long rowCount = 0;
    long queryStartTimeMsec;

    // Inner classes

    private class SorterIterationHelper implements IterationHelper
    {
        @Override
        public Row row()
        {
            ValuesHolderRow row = new ValuesHolderRow(rowType);
            value.setStreamMode(true);
            for (int i = 0; i < rowFields; i++) {
                ValueHolder valueHolder = row.holderAt(i);
                valueSource.expectedType(fieldTypes[i]);
                valueHolder.copyFrom(valueSource);
            }
            return row;
        }

        @Override
        public void close()
        {
            Sorter.this.close();
        }

        @Override
        public Exchange exchange()
        {
            return exchange;
        }

        SorterIterationHelper()
        {
            valueSource = new PersistitValueValueSource();
            valueSource.attach(value);
        }

        private final PersistitValueValueSource valueSource;
    }

    // public so that tests can see it
    public static class TempVolumeState
    {
        public TempVolumeState(Volume volume)
        {
            this.volume = volume;
            sortsInProgress = 0;
        }

        public Volume volume()
        {
            return volume;
        }

        public void startSort()
        {
            sortsInProgress++;
        }

        public int endSort()
        {
            sortsInProgress--;
            assert sortsInProgress >= 0;
            return sortsInProgress;
        }

        public int sortsInProgress()
        {
            return sortsInProgress;
        }

        private final Volume volume;
        private int sortsInProgress;
    }
}
