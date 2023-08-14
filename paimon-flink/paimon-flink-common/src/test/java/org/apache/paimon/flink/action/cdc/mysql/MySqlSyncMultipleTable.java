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

package org.apache.paimon.flink.action.cdc.mysql;

import org.apache.flink.configuration.Configuration;
import org.apache.paimon.flink.action.cdc.mysql.sf.MultipleHostOptions;

import org.apache.flink.api.common.JobStatus;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** IT cases for {@link MySqlSyncTableAction}. */
public class MySqlSyncMultipleTable {

    @Test
    public void multipleHostToPaimon() throws Exception {
        System.setProperty("HADOOP_USER_NAME", "hive");

        Map<String, String> mySqlConfig = new HashMap<>();
        mySqlConfig.put(MultipleHostOptions.MULTIPLE_HOST_ENABLED.key(), "true");
        mySqlConfig.put(MultipleHostOptions.HOST_JSON_LIST.key(), "[{\"hostname\":\"annotation-m.dbsit.sfcloud" +
                ".local\",\"port\":3306,\"username\":\"bincanal\",\"password\":\"sf123456\",\"database\":\"test\"," +
                "\"table\":\"gx_fen1\"},{\"hostname\":\"annotation-m.dbsit.sfcloud.local\",\"port\":3306," +
                "\"username\":\"bincanal\",\"password\":\"sf123456\",\"database\":\"test\",\"table\":\"gx_fen2\"}]");


        Configuration conf = new Configuration();
        conf.setString("state.backend", "filesystem");
        conf.setString("state.checkpoints.num-retained", "3");
        conf.setString(
                "state.checkpoints.dir",
                "file:\\" + System.getProperty("user.dir") + "\\checkpoint-dir");

        // 从某个 checkpoint 启动
        //        conf.setString("execution.savepoint.path", "file:\\" +
        // System.getProperty("user.dir")
        //                + "\\checkpoint-dir\\59b16ff3a28c32c706c513cedc0199a5\\chk-5");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(conf);
        env.setParallelism(2);
        env.enableCheckpointing(10 * 1000);
        env.setRestartStrategy(RestartStrategies.noRestart());

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Map<String, String> tableConfig = new HashMap<>();
        tableConfig.put("bucket", String.valueOf(random.nextInt(3) + 1));
        tableConfig.put("sink.parallelism", String.valueOf(random.nextInt(3) + 1));


        Map<String, String> catalogConfig = new HashMap<>();
        catalogConfig.put("metastore", "hive");
        catalogConfig.put("uri", "thrift://10.202.77.200:9083");
        MySqlSyncTableAction action =
                new MySqlSyncTableAction(
                        mySqlConfig,
                        "hdfs://test-cluster/user/hive/warehouse/",
                        "sssjh_mysql",
                        "gx_fen",
                        Collections.singletonList("inc_day"),
                        Arrays.asList("id", "inc_day"),
                        Arrays.asList("inc_day=formatDate(dt,yyyyMMdd)"),
                        catalogConfig,
                        tableConfig);
        action.build(env);
        env.execute();
    }

}
