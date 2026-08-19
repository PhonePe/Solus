package com.phonepe.solus;

import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.hbase.HBaseTableConnection;
import com.phonepe.solus.store.context.impl.HBaseStorageContext;
import io.dropwizard.validation.BaseValidator;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;

public class HBaseSolusTest {

    private static final String HBASE_ZOOKEEPER_QUORUM = "localhost";
    private static final int HBASE_ZOOKEEPER_PORT = 2181;
    private static final String TABLE_NAME = "hbase_solus_test";
    private static final String DE_DUPER_NAME = "lead";
    private static final String CLIENT_ID = "vidar";
    private static final String FARM = "NB6";
    private static final String COLUMN_FAMILY = "info"; // Default Column Family required for HBase

    public static void main(String[] args) throws Exception {
        // 1. Initialize HBase Configuration properly
        final Configuration baseConf = new Configuration();
        baseConf.set("hbase.zookeeper.quorum", HBASE_ZOOKEEPER_QUORUM);
        baseConf.setInt("hbase.zookeeper.property.clientPort", HBASE_ZOOKEEPER_PORT);
        baseConf.set("zookeeper.znode.parent", "/hbase");

        // Use HBaseConfiguration.create to load missing default RPC/Region properties
        final Configuration conf = HBaseConfiguration.create(baseConf);

        try (Connection connection = ConnectionFactory.createConnection(conf)) {
            // 2. Re-create / Ensure Table exists on your local HBase instance
            ensureTableCreated(connection);

            final HBaseTableConnection tableConnection = new HBaseTableConnection(false, connection);
            final HBaseStorageContext storageContext = HBaseStorageContext.builder()
                .connection(tableConnection)
                .tableName(TABLE_NAME)
                .farm(FARM)
                .build();

            final int deduperExpiryInSeconds = 1000;
            final SolusEngine<String> engine = new SolusEngine<>(BaseValidator.newValidator(), CLIENT_ID, storageContext);
            engine.register(DE_DUPER_NAME, DeDuperConfig.builder()
                .expiryInSeconds(deduperExpiryInSeconds)
                .build());

            final String singleEntity = "single-entity1";
            final long requestedSingleTtlInMs = 5000;

            engine.add(DE_DUPER_NAME, singleEntity, requestedSingleTtlInMs);
            System.out.println(engine.checkAbsence(DE_DUPER_NAME, singleEntity));

            Thread.sleep(7_000);
            System.out.println(engine.checkAbsence(DE_DUPER_NAME, singleEntity));

        }
    }

    /**
     * Safely drops and creates the table natively without relying on Docker scripts
     */
    private static void ensureTableCreated(final Connection connection) throws IOException {
        try (Admin admin = connection.getAdmin()) {
            final TableName tableName = TableName.valueOf(FARM + "_" + TABLE_NAME);

            if (admin.tableExists(tableName)) {
                if (admin.isTableEnabled(tableName)) {
                    admin.disableTable(tableName);
                }
                admin.deleteTable(tableName);
                System.out.println("Dropped existing local table: " + tableName);
            }

            // Create table with column family "info"
            TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
            ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes(COLUMN_FAMILY));
            tableBuilder.setColumnFamily(cfBuilder.build());

            admin.createTable(tableBuilder.build());
            System.out.println("Successfully created table locally: " + tableName);
        }
    }
}
