/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.flink.action.cdc.kafka;

import org.apache.paimon.flink.action.cdc.MessageQueueSchemaUtils;
import org.apache.paimon.flink.action.cdc.MessageQueueSyncTableActionBase;
import org.apache.paimon.flink.action.cdc.format.DataFormat;
import org.apache.paimon.schema.Schema;

import org.apache.flink.api.connector.source.Source;
import org.apache.flink.streaming.connectors.kafka.table.KafkaConnectorOptions;

import java.util.Map;

/** Synchronize table from Kafka. */
public class KafkaSyncTableAction extends MessageQueueSyncTableActionBase {

    public KafkaSyncTableAction(
            String warehouse,
            String database,
            String table,
            Map<String, String> catalogConfig,
            Map<String, String> kafkaConfig) {
        super(warehouse, database, table, catalogConfig, kafkaConfig);
    }

    @Override
    protected Source<String, ?, ?> buildSource() {
        return KafkaActionUtils.buildKafkaSource(mqConfig);
    }

    @Override
    protected Schema buildSchema() {
        String topic = mqConfig.get(KafkaConnectorOptions.TOPIC).get(0);
        try (MessageQueueSchemaUtils.ConsumerWrapper consumer =
                KafkaActionUtils.getKafkaEarliestConsumer(mqConfig, topic)) {
            return MessageQueueSchemaUtils.getSchema(consumer, topic, getDataFormat(), typeMapping);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected DataFormat getDataFormat() {
        return KafkaActionUtils.getDataFormat(mqConfig);
    }

    @Override
    protected String sourceName() {
        return "Kafka Source";
    }

    @Override
    protected String jobName() {
        return String.format("Kafka-Paimon Table Sync: %s.%s", database, table);
    }
}
