/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.qp.persistitadapter.sort;

import com.akiban.ais.model.Column;
import com.akiban.qp.expression.BoundExpressions;
import com.akiban.qp.row.Row;
import com.akiban.server.collation.AkCollator;
import com.akiban.server.expression.std.Comparison;
import com.akiban.server.types.AkType;
import com.akiban.server.types3.TInstance;
import com.persistit.Key;

interface SortKeyAdapter<S, E> {
    AkType[] createAkTypes(int size);
    AkCollator[] createAkCollators(int size);
    TInstance[] createTInstances(int size);
    void setColumnMetadata(Column column, int f, AkType[] akTypes, AkCollator[] collators, TInstance[] tInstances);

    void checkConstraints(BoundExpressions loExpressions, BoundExpressions hiExpressions, int f);

    S[] createSourceArray(int size);
    S get(BoundExpressions boundExpressions, int f);

    SortKeyTarget<S> createTarget();
    SortKeySource<S> createSource();
    
    boolean areEqual(S one, S two);
    long compare(S one, S two);
    E createComparison(S one, Comparison comparison, S two);
    boolean evaluateComparison(E comparison);

    boolean isNull(S source);

    S eval(Row row, int field);
}
