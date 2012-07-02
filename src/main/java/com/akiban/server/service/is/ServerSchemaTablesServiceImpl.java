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
package com.akiban.server.service.is;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.aisb2.AISBBasedBuilder;
import com.akiban.ais.model.aisb2.NewAISBuilder;
import com.akiban.qp.memoryadapter.BasicFactoryBase;
import com.akiban.qp.memoryadapter.MemoryAdapter;
import com.akiban.qp.memoryadapter.MemoryGroupCursor.GroupScan;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.service.Service;
import com.akiban.server.store.SchemaManager;
import com.akiban.sql.pg.PostgresServer;
import com.akiban.sql.pg.PostgresServerManager;
import com.google.inject.Inject;

public class ServerSchemaTablesServiceImpl
    extends SchemaTablesService
    implements Service<ServerSchemaTablesService>, ServerSchemaTablesService {

    static final TableName SERVER_INSTANCE_SUMMARY = new TableName (SCHEMA_NAME, "server_instance_summary");
    private final PostgresServer server;
    
    @Inject
    public ServerSchemaTablesServiceImpl (SchemaManager schemaManager, PostgresServerManager manager) {
        super(schemaManager);
        this.server = manager.getServer();
    }
    
    @Override
    public ServerSchemaTablesService cast() {
        return this;
    }

    @Override
    public Class<ServerSchemaTablesService> castClass() {
        return ServerSchemaTablesService.class;
    }

    @Override
    public void start() {
        AkibanInformationSchema ais = createTablesToRegister();
        //SERVER_INSTANCE_SUMMARY
        attach (ais, true, SERVER_INSTANCE_SUMMARY, InstanceSummary.class);
    }

    @Override
    public void stop() {
        // nothing
    }

    @Override
    public void crash() {
        // nothing
    }
    
    private class InstanceSummary extends BasicFactoryBase {

        public InstanceSummary(TableName sourceTable) {
            super(sourceTable);
        }

        @Override
        public GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            return 1;
        }
        
        private class Scan extends BaseScan {
            
            public Scan (RowType rowType) {
                super(rowType);
            }

            @Override
            public Row next() {
                return null;
            }
        }
    }
    
    static AkibanInformationSchema createTablesToRegister() {
        NewAISBuilder builder = AISBBasedBuilder.create();
        
        builder.userTable(SERVER_INSTANCE_SUMMARY)
            .colString("host_name", 1024, false)
            .colString("instance_status", 32, false)
            .colTimestamp("start_time");
        
        return builder.ais(false);
    }
}
