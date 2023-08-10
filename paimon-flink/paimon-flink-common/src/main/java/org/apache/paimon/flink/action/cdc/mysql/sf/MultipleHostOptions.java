/*
 * Copyright 2022 Ververica Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.flink.action.cdc.mysql.sf;

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;

/** Multiple host Configurations. */
public class MultipleHostOptions {

    public static final ConfigOption<Boolean> MULTIPLE_HOST_ENABLED =
            ConfigOptions.key("multiple-host-enabled")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("IP address or hostname of the MySQL database server.");

    public static final ConfigOption<String> HOST_JSON_LIST =
            ConfigOptions.key("host-json-list")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "mysql host list, this is jso : [{\"hostname\":\"localhost\","
                                    + "\"port\":3306,\"username\":\"master\",\"password\":\"123456\","
                                    + "\"database\":\"db1\",\"table\":\"gx_fen\"}]");
}
