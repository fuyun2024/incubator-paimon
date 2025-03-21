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

package org.apache.paimon;

import org.apache.paimon.annotation.Documentation.ExcludeFromDocumentation;
import org.apache.paimon.annotation.Documentation.Immutable;
import org.apache.paimon.annotation.VisibleForTesting;
import org.apache.paimon.format.FileFormat;
import org.apache.paimon.fs.Path;
import org.apache.paimon.options.ConfigOption;
import org.apache.paimon.options.MemorySize;
import org.apache.paimon.options.Options;
import org.apache.paimon.options.OptionsUtils;
import org.apache.paimon.options.description.DescribedEnum;
import org.apache.paimon.options.description.Description;
import org.apache.paimon.options.description.InlineElement;
import org.apache.paimon.utils.Pair;
import org.apache.paimon.utils.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.paimon.options.ConfigOptions.key;
import static org.apache.paimon.options.description.TextElement.text;
import static org.apache.paimon.utils.Preconditions.checkArgument;

/** Core options for paimon. */
public class CoreOptions implements Serializable {

    public static final String DEFAULT_VALUE_SUFFIX = "default-value";

    public static final String FIELDS_PREFIX = "fields";

    public static final ConfigOption<Integer> BUCKET =
            key("bucket")
                    .intType()
                    .defaultValue(1)
                    .withDescription("Bucket number for file store.");

    @Immutable
    public static final ConfigOption<String> BUCKET_KEY =
            key("bucket-key")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "Specify the paimon distribution policy. Data is assigned"
                                                    + " to each bucket according to the hash value of bucket-key.")
                                    .linebreak()
                                    .text("If you specify multiple fields, delimiter is ','.")
                                    .linebreak()
                                    .text(
                                            "If not specified, the primary key will be used; "
                                                    + "if there is no primary key, the full row will be used.")
                                    .build());

    @ExcludeFromDocumentation("Internal use only")
    public static final ConfigOption<String> PATH =
            key("path")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("The file path of this table in the filesystem.");

    public static final ConfigOption<FileFormatType> FILE_FORMAT =
            key("file.format")
                    .enumType(FileFormatType.class)
                    .defaultValue(FileFormatType.ORC)
                    .withDescription(
                            "Specify the message format of data files, currently orc, parquet and avro are supported.");

    public static final ConfigOption<String> ORC_BLOOM_FILTER_COLUMNS =
            key("orc.bloom.filter.columns")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "A comma-separated list of columns for which to create a bloom filter when writing.");

    public static final ConfigOption<Double> ORC_BLOOM_FILTER_FPP =
            key("orc.bloom.filter.fpp")
                    .doubleType()
                    .defaultValue(0.05)
                    .withDescription(
                            "Define the default false positive probability for bloom filters.");

    public static final ConfigOption<Map<String, String>> FILE_COMPRESSION_PER_LEVEL =
            key("file.compression.per.level")
                    .mapType()
                    .defaultValue(new HashMap<>())
                    .withDescription(
                            "Define different compression policies for different level, you can add the conf like this:"
                                    + " 'file.compression.per.level' = '0:lz4,1:zlib', for orc file format, the compression value "
                                    + "could be NONE, ZLIB, SNAPPY, LZO, LZ4, for parquet file format, the compression value could be "
                                    + "UNCOMPRESSED, SNAPPY, GZIP, LZO, BROTLI, LZ4, ZSTD.");

    public static final ConfigOption<Map<String, String>> FILE_FORMAT_PER_LEVEL =
            key("file.format.per.level")
                    .mapType()
                    .defaultValue(new HashMap<>())
                    .withDescription(
                            "Define different file format for different level, you can add the conf like this:"
                                    + " 'file.format.per.level' = '0:avro,3:parquet', if the file format for level is not provided, "
                                    + "the default format which set by `"
                                    + FILE_FORMAT.key()
                                    + "` will be used.");

    public static final ConfigOption<String> FILE_COMPRESSION =
            key("file.compression")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Default file compression format, can be overridden by "
                                    + FILE_COMPRESSION_PER_LEVEL.key());

    public static final ConfigOption<FileFormatType> MANIFEST_FORMAT =
            key("manifest.format")
                    .enumType(FileFormatType.class)
                    .defaultValue(FileFormatType.AVRO)
                    .withDescription("Specify the message format of manifest files.");

    public static final ConfigOption<MemorySize> MANIFEST_TARGET_FILE_SIZE =
            key("manifest.target-file-size")
                    .memoryType()
                    .defaultValue(MemorySize.ofMebiBytes(8))
                    .withDescription("Suggested file size of a manifest file.");

    public static final ConfigOption<MemorySize> MANIFEST_FULL_COMPACTION_FILE_SIZE =
            key("manifest.full-compaction-threshold-size")
                    .memoryType()
                    .defaultValue(MemorySize.ofMebiBytes(16))
                    .withDescription(
                            "The size threshold for triggering full compaction of manifest.");

    public static final ConfigOption<Integer> MANIFEST_MERGE_MIN_COUNT =
            key("manifest.merge-min-count")
                    .intType()
                    .defaultValue(30)
                    .withDescription(
                            "To avoid frequent manifest merges, this parameter specifies the minimum number "
                                    + "of ManifestFileMeta to merge.");

    public static final ConfigOption<String> PARTITION_DEFAULT_NAME =
            key("partition.default-name")
                    .stringType()
                    .defaultValue("__DEFAULT_PARTITION__")
                    .withDescription(
                            "The default partition name in case the dynamic partition"
                                    + " column value is null/empty string.");

    public static final ConfigOption<Integer> SNAPSHOT_NUM_RETAINED_MIN =
            key("snapshot.num-retained.min")
                    .intType()
                    .defaultValue(10)
                    .withDescription(
                            "The minimum number of completed snapshots to retain. Should be greater than or equal to 1.");

    public static final ConfigOption<Integer> SNAPSHOT_NUM_RETAINED_MAX =
            key("snapshot.num-retained.max")
                    .intType()
                    .defaultValue(Integer.MAX_VALUE)
                    .withDescription(
                            "The maximum number of completed snapshots to retain. Should be greater than or equal to the minimum number.");

    public static final ConfigOption<Duration> SNAPSHOT_TIME_RETAINED =
            key("snapshot.time-retained")
                    .durationType()
                    .defaultValue(Duration.ofHours(1))
                    .withDescription("The maximum time of completed snapshots to retain.");

    public static final ConfigOption<ExpireExecutionMode> SNAPSHOT_EXPIRE_EXECUTION_MODE =
            key("snapshot.expire.execution-mode")
                    .enumType(ExpireExecutionMode.class)
                    .defaultValue(ExpireExecutionMode.SYNC)
                    .withDescription("Specifies the execution mode of expire.");

    public static final ConfigOption<Integer> SNAPSHOT_EXPIRE_LIMIT =
            key("snapshot.expire.limit")
                    .intType()
                    .defaultValue(10)
                    .withDescription(
                            "The maximum number of snapshots allowed to expire at a time.");

    public static final ConfigOption<Duration> CONTINUOUS_DISCOVERY_INTERVAL =
            key("continuous.discovery-interval")
                    .durationType()
                    .defaultValue(Duration.ofSeconds(10))
                    .withDescription("The discovery interval of continuous reading.");

    @Immutable
    public static final ConfigOption<MergeEngine> MERGE_ENGINE =
            key("merge-engine")
                    .enumType(MergeEngine.class)
                    .defaultValue(MergeEngine.DEDUPLICATE)
                    .withDescription("Specify the merge engine for table with primary key.");

    public static final ConfigOption<Boolean> PARTIAL_UPDATE_IGNORE_DELETE =
            key("partial-update.ignore-delete")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether to ignore delete records in partial-update mode.");

    public static final ConfigOption<SortEngine> SORT_ENGINE =
            key("sort-engine")
                    .enumType(SortEngine.class)
                    .defaultValue(SortEngine.LOSER_TREE)
                    .withDescription("Specify the sort engine for table with primary key.");

    public static final ConfigOption<Integer> SORT_SPILL_THRESHOLD =
            key("sort-spill-threshold")
                    .intType()
                    .noDefaultValue()
                    .withDescription(
                            "If the maximum number of sort readers exceeds this value, a spill will be attempted. "
                                    + "This prevents too many readers from consuming too much memory and causing OOM.");

    public static final ConfigOption<MemorySize> SORT_SPILL_BUFFER_SIZE =
            key("sort-spill-buffer-size")
                    .memoryType()
                    .defaultValue(MemorySize.parse("64 mb"))
                    .withDescription("Amount of data to spill records to disk in spilled sort.");

    @Immutable
    public static final ConfigOption<WriteMode> WRITE_MODE =
            key("write-mode")
                    .enumType(WriteMode.class)
                    .defaultValue(WriteMode.AUTO)
                    .withDescription("Specify the write mode for table.");

    public static final ConfigOption<Boolean> WRITE_ONLY =
            key("write-only")
                    .booleanType()
                    .defaultValue(false)
                    .withDeprecatedKeys("write.compaction-skip")
                    .withDescription(
                            "If set to true, compactions and snapshot expiration will be skipped. "
                                    + "This option is used along with dedicated compact jobs.");

    public static final ConfigOption<MemorySize> SOURCE_SPLIT_TARGET_SIZE =
            key("source.split.target-size")
                    .memoryType()
                    .defaultValue(MemorySize.ofMebiBytes(128))
                    .withDescription("Target size of a source split when scanning a bucket.");

    public static final ConfigOption<MemorySize> SOURCE_SPLIT_OPEN_FILE_COST =
            key("source.split.open-file-cost")
                    .memoryType()
                    .defaultValue(MemorySize.ofMebiBytes(4))
                    .withDescription(
                            "Open file cost of a source file. It is used to avoid reading"
                                    + " too many files with a source split, which can be very slow.");

    public static final ConfigOption<MemorySize> WRITE_BUFFER_SIZE =
            key("write-buffer-size")
                    .memoryType()
                    .defaultValue(MemorySize.parse("256 mb"))
                    .withDescription(
                            "Amount of data to build up in memory before converting to a sorted on-disk file.");

    public static final ConfigOption<Boolean> WRITE_BUFFER_SPILLABLE =
            key("write-buffer-spillable")
                    .booleanType()
                    .noDefaultValue()
                    .withDescription(
                            "Whether the write buffer can be spillable. Enabled by default when using object storage.");

    public static final ConfigOption<Boolean> WRITE_BUFFER_FOR_APPEND =
            key("write-buffer-for-append")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            "This option only works for append-only table. Whether the write use write buffer to avoid out-of-memory error.");

    public static final ConfigOption<MemorySize> WRITE_MANIFEST_CACHE =
            key("write-manifest-cache")
                    .memoryType()
                    .defaultValue(MemorySize.ofMebiBytes(0))
                    .withDescription(
                            "Cache size for reading manifest files for write initialization.");

    public static final ConfigOption<Integer> LOCAL_SORT_MAX_NUM_FILE_HANDLES =
            key("local-sort.max-num-file-handles")
                    .intType()
                    .defaultValue(128)
                    .withDescription(
                            "The maximal fan-in for external merge sort. It limits the number of file handles. "
                                    + "If it is too small, may cause intermediate merging. But if it is too large, "
                                    + "it will cause too many files opened at the same time, consume memory and lead to random reading.");

    public static final ConfigOption<MemorySize> PAGE_SIZE =
            key("page-size")
                    .memoryType()
                    .defaultValue(MemorySize.parse("64 kb"))
                    .withDescription("Memory page size.");

    public static final ConfigOption<MemorySize> TARGET_FILE_SIZE =
            key("target-file-size")
                    .memoryType()
                    .defaultValue(MemorySize.ofMebiBytes(128))
                    .withDescription("Target size of a file.");

    public static final ConfigOption<Integer> NUM_SORTED_RUNS_COMPACTION_TRIGGER =
            key("num-sorted-run.compaction-trigger")
                    .intType()
                    .defaultValue(5)
                    .withDescription(
                            "The sorted run number to trigger compaction. Includes level0 files (one file one sorted run) and "
                                    + "high-level runs (one level one sorted run).");

    public static final ConfigOption<Integer> NUM_SORTED_RUNS_STOP_TRIGGER =
            key("num-sorted-run.stop-trigger")
                    .intType()
                    .noDefaultValue()
                    .withDescription(
                            "The number of sorted runs that trigger the stopping of writes,"
                                    + " the default value is 'num-sorted-run.compaction-trigger' + 1.");

    public static final ConfigOption<Integer> NUM_LEVELS =
            key("num-levels")
                    .intType()
                    .noDefaultValue()
                    .withDescription(
                            "Total level number, for example, there are 3 levels, including 0,1,2 levels.");

    public static final ConfigOption<Boolean> COMMIT_FORCE_COMPACT =
            key("commit.force-compact")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether to force a compaction before commit.");

    public static final ConfigOption<Integer> COMPACTION_MAX_SIZE_AMPLIFICATION_PERCENT =
            key("compaction.max-size-amplification-percent")
                    .intType()
                    .defaultValue(200)
                    .withDescription(
                            "The size amplification is defined as the amount (in percentage) of additional storage "
                                    + "needed to store a single byte of data in the merge tree for changelog mode table.");

    public static final ConfigOption<Integer> COMPACTION_SIZE_RATIO =
            key("compaction.size-ratio")
                    .intType()
                    .defaultValue(1)
                    .withDescription(
                            "Percentage flexibility while comparing sorted run size for changelog mode table. If the candidate sorted run(s) "
                                    + "size is 1% smaller than the next sorted run's size, then include next sorted run "
                                    + "into this candidate set.");

    public static final ConfigOption<Integer> COMPACTION_MIN_FILE_NUM =
            key("compaction.min.file-num")
                    .intType()
                    .defaultValue(5)
                    .withDescription(
                            "For file set [f_0,...,f_N], the minimum file number which satisfies "
                                    + "sum(size(f_i)) >= targetFileSize to trigger a compaction for "
                                    + "append-only table. This value avoids almost-full-file to be compacted, "
                                    + "which is not cost-effective.");

    public static final ConfigOption<Integer> COMPACTION_MAX_FILE_NUM =
            key("compaction.max.file-num")
                    .intType()
                    .defaultValue(50)
                    .withDeprecatedKeys("compaction.early-max.file-num")
                    .withDescription(
                            "For file set [f_0,...,f_N], the maximum file number to trigger a compaction "
                                    + "for append-only table, even if sum(size(f_i)) < targetFileSize. This value "
                                    + "avoids pending too much small files, which slows down the performance.");

    public static final ConfigOption<ChangelogProducer> CHANGELOG_PRODUCER =
            key("changelog-producer")
                    .enumType(ChangelogProducer.class)
                    .defaultValue(ChangelogProducer.NONE)
                    .withDescription(
                            "Whether to double write to a changelog file. "
                                    + "This changelog file keeps the details of data changes, "
                                    + "it can be read directly during stream reads. This can be applied to tables with primary keys. ");

    public static final ConfigOption<Boolean> CHANGELOG_PRODUCER_ROW_DEDUPLICATE =
            key("changelog-producer.row-deduplicate")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            "Whether to generate -U, +U changelog for the same record. This configuration is only valid for the changelog-producer is lookup or full-compaction.");

    @Immutable
    public static final ConfigOption<String> SEQUENCE_FIELD =
            key("sequence.field")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "The field that generates the sequence number for primary key table,"
                                    + " the sequence number determines which data is the most recent.");

    public static final ConfigOption<String> SEQUENCE_AUTO_PADDING =
            key("sequence.auto-padding")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "Specify the way of padding precision, if the provided sequence field is used to indicate \"time\" but doesn't meet the precise.")
                                    .list(
                                            text("You can specific:"),
                                            text(
                                                    "1. \"row-kind-flag\": Pads a bit flag to indicate whether it is retract (0) or add (1) message."),
                                            text(
                                                    "2. \"second-to-micro\": Pads the sequence field that indicates time with precision of seconds to micro-second."),
                                            text(
                                                    "3. \"millis-to-micro\": Pads the sequence field that indicates time with precision of milli-second to micro-second."),
                                            text(
                                                    "4. Composite pattern: for example, \"second-to-micro,row-kind-flag\"."))
                                    .build());

    public static final ConfigOption<StartupMode> SCAN_MODE =
            key("scan.mode")
                    .enumType(StartupMode.class)
                    .defaultValue(StartupMode.DEFAULT)
                    .withDeprecatedKeys("log.scan")
                    .withDescription("Specify the scanning behavior of the source.");

    public static final ConfigOption<Long> SCAN_TIMESTAMP_MILLIS =
            key("scan.timestamp-millis")
                    .longType()
                    .noDefaultValue()
                    .withDeprecatedKeys("log.scan.timestamp-millis")
                    .withDescription(
                            "Optional timestamp used in case of \"from-timestamp\" scan mode.");

    public static final ConfigOption<Long> SCAN_SNAPSHOT_ID =
            key("scan.snapshot-id")
                    .longType()
                    .noDefaultValue()
                    .withDescription(
                            "Optional snapshot id used in case of \"from-snapshot\" or \"from-snapshot-full\" scan mode");

    public static final ConfigOption<String> SCAN_TAG_NAME =
            key("scan.tag-name")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Optional tag name used in case of \"from-snapshot\" scan mode.");

    public static final ConfigOption<Long> SCAN_BOUNDED_WATERMARK =
            key("scan.bounded.watermark")
                    .longType()
                    .noDefaultValue()
                    .withDescription(
                            "End condition \"watermark\" for bounded streaming mode. Stream"
                                    + " reading will end when a larger watermark snapshot is encountered.");

    public static final ConfigOption<Integer> SCAN_MANIFEST_PARALLELISM =
            key("scan.manifest.parallelism")
                    .intType()
                    .noDefaultValue()
                    .withDescription(
                            "The parallelism of scanning manifest files, default value is the size of cpu processor. "
                                    + "Note: Scale-up this parameter will increase memory usage while scanning manifest files. "
                                    + "We can consider downsize it when we encounter an out of memory exception while scanning");

    public static final ConfigOption<LogConsistency> LOG_CONSISTENCY =
            key("log.consistency")
                    .enumType(LogConsistency.class)
                    .defaultValue(LogConsistency.TRANSACTIONAL)
                    .withDescription("Specify the log consistency mode for table.");

    public static final ConfigOption<LogChangelogMode> LOG_CHANGELOG_MODE =
            key("log.changelog-mode")
                    .enumType(LogChangelogMode.class)
                    .defaultValue(LogChangelogMode.AUTO)
                    .withDescription("Specify the log changelog mode for table.");

    public static final ConfigOption<String> LOG_KEY_FORMAT =
            key("log.key.format")
                    .stringType()
                    .defaultValue("json")
                    .withDescription(
                            "Specify the key message format of log system with primary key.");

    public static final ConfigOption<String> LOG_FORMAT =
            key("log.format")
                    .stringType()
                    .defaultValue("debezium-json")
                    .withDescription("Specify the message format of log system.");

    public static final ConfigOption<Boolean> AUTO_CREATE =
            key("auto-create")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            "Whether to create underlying storage when reading and writing the table.");

    public static final ConfigOption<Boolean> STREAMING_READ_OVERWRITE =
            key("streaming-read-overwrite")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            "Whether to read the changes from overwrite in streaming mode.");

    public static final ConfigOption<Boolean> DYNAMIC_PARTITION_OVERWRITE =
            key("dynamic-partition-overwrite")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription(
                            "Whether only overwrite dynamic partition when overwriting a partitioned table with "
                                    + "dynamic partition columns. Works only when the table has partition keys.");

    public static final ConfigOption<Duration> PARTITION_EXPIRATION_TIME =
            key("partition.expiration-time")
                    .durationType()
                    .noDefaultValue()
                    .withDescription(
                            "The expiration interval of a partition. A partition will be expired if"
                                    + " it‘s lifetime is over this value. Partition time is extracted from"
                                    + " the partition value.");

    public static final ConfigOption<Duration> PARTITION_EXPIRATION_CHECK_INTERVAL =
            key("partition.expiration-check-interval")
                    .durationType()
                    .defaultValue(Duration.ofHours(1))
                    .withDescription("The check interval of partition expiration.");

    public static final ConfigOption<String> PARTITION_TIMESTAMP_FORMATTER =
            key("partition.timestamp-formatter")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "The formatter to format timestamp from string. It can be used"
                                                    + " with 'partition.timestamp-pattern' to create a formatter"
                                                    + " using the specified value.")
                                    .list(
                                            text(
                                                    "Default formatter is 'yyyy-MM-dd HH:mm:ss' and 'yyyy-MM-dd'."),
                                            text(
                                                    "Supports multiple partition fields like '$year-$month-$day $hour:00:00'."),
                                            text(
                                                    "The timestamp-formatter is compatible with Java's DateTimeFormatter."))
                                    .build());

    public static final ConfigOption<String> PARTITION_TIMESTAMP_PATTERN =
            key("partition.timestamp-pattern")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "You can specify a pattern to get a timestamp from partitions. "
                                                    + "The formatter pattern is defined by 'partition.timestamp-formatter'.")
                                    .list(
                                            text("By default, read from the first field."),
                                            text(
                                                    "If the timestamp in the partition is a single field called 'dt', you can use '$dt'."),
                                            text(
                                                    "If it is spread across multiple fields for year, month, day, and hour,"
                                                            + " you can use '$year-$month-$day $hour:00:00'."),
                                            text(
                                                    "If the timestamp is in fields dt and hour, you can use '$dt "
                                                            + "$hour:00:00'."))
                                    .build());

    public static final ConfigOption<Boolean> SCAN_PLAN_SORT_PARTITION =
            key("scan.plan-sort-partition")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "Whether to sort plan files by partition fields, this allows you to read"
                                                    + " according to the partition order, even if your partition writes are out of order.")
                                    .linebreak()
                                    .text(
                                            "It is recommended that you use this for streaming read of the 'append-only' table."
                                                    + " By default, streaming read will read the full snapshot first. In order to"
                                                    + " avoid the disorder reading for partitions, you can open this option.")
                                    .build());

    @Immutable
    public static final ConfigOption<String> PRIMARY_KEY =
            key("primary-key")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Define primary key by table options, cannot define primary key on DDL and table options at the same time.");

    @Immutable
    public static final ConfigOption<String> PARTITION =
            key("partition")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Define partition by table options, cannot define partition on DDL and table options at the same time.");

    public static final ConfigOption<Float> LOOKUP_HASH_LOAD_FACTOR =
            key("lookup.hash-load-factor")
                    .floatType()
                    .defaultValue(0.75F)
                    .withDescription("The index load factor for lookup.");

    public static final ConfigOption<Duration> LOOKUP_CACHE_FILE_RETENTION =
            key("lookup.cache-file-retention")
                    .durationType()
                    .defaultValue(Duration.ofHours(1))
                    .withDescription(
                            "The cached files retention time for lookup. After the file expires,"
                                    + " if there is a need for access, it will be re-read from the DFS to build"
                                    + " an index on the local disk.");

    public static final ConfigOption<MemorySize> LOOKUP_CACHE_MAX_DISK_SIZE =
            key("lookup.cache-max-disk-size")
                    .memoryType()
                    .defaultValue(MemorySize.MAX_VALUE)
                    .withDescription(
                            "Max disk size for lookup cache, you can use this option to limit the use of local disks.");

    public static final ConfigOption<MemorySize> LOOKUP_CACHE_MAX_MEMORY_SIZE =
            key("lookup.cache-max-memory-size")
                    .memoryType()
                    .defaultValue(MemorySize.parse("256 mb"))
                    .withDescription("Max memory size for lookup cache.");

    public static final ConfigOption<Integer> READ_BATCH_SIZE =
            key("read.batch-size")
                    .intType()
                    .defaultValue(1024)
                    .withDescription("Read batch size for orc and parquet.");

    public static final ConfigOption<Integer> ORC_WRITE_BATCH_SIZE =
            key("orc.write.batch-size")
                    .intType()
                    .defaultValue(1024)
                    .withDescription("write batch size for orc.");

    public static final ConfigOption<String> CONSUMER_ID =
            key("consumer-id")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Consumer id for recording the offset of consumption in the storage.");

    public static final ConfigOption<Integer> FULL_COMPACTION_DELTA_COMMITS =
            key("full-compaction.delta-commits")
                    .intType()
                    .noDefaultValue()
                    .withDescription(
                            "Full compaction will be constantly triggered after delta commits.");

    @ExcludeFromDocumentation("Internal use only")
    public static final ConfigOption<StreamScanMode> STREAM_SCAN_MODE =
            key("stream-scan-mode")
                    .enumType(StreamScanMode.class)
                    .defaultValue(StreamScanMode.NONE)
                    .withDescription(
                            "Only used to force TableScan to construct suitable 'StartingUpScanner' and 'FollowUpScanner' "
                                    + "dedicated internal streaming scan.");

    public static final ConfigOption<StreamingReadMode> STREAMING_READ_MODE =
            key("streaming-read-mode")
                    .enumType(StreamingReadMode.class)
                    .noDefaultValue()
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "The mode of streaming read that specifies to read the data of table file or log")
                                    .linebreak()
                                    .linebreak()
                                    .text("Possible values:")
                                    .linebreak()
                                    .list(
                                            text(
                                                    StreamingReadMode.FILE.getValue()
                                                            + ": Reads from the data of table file store."))
                                    .list(
                                            text(
                                                    StreamingReadMode.LOG.getValue()
                                                            + ": Read from the data of table log store."))
                                    .build());

    public static final ConfigOption<Duration> CONSUMER_EXPIRATION_TIME =
            key("consumer.expiration-time")
                    .durationType()
                    .noDefaultValue()
                    .withDescription(
                            "The expiration interval of consumer files. A consumer file will be expired if "
                                    + "it's lifetime after last modification is over this value.");

    public static final ConfigOption<Long> DYNAMIC_BUCKET_TARGET_ROW_NUM =
            key("dynamic-bucket.target-row-num")
                    .longType()
                    .defaultValue(2_000_000L)
                    .withDescription(
                            "If the bucket is -1, for primary key table, is dynamic bucket mode, "
                                    + "this option controls the target row number for one bucket.");

    public static final ConfigOption<Integer> DYNAMIC_BUCKET_ASSIGNER_PARALLELISM =
            key("dynamic-bucket.assigner-parallelism")
                    .intType()
                    .noDefaultValue()
                    .withDescription(
                            "Parallelism of assigner operator for dynamic bucket mode, it is"
                                    + " related to the number of initialized bucket, too small will lead to"
                                    + " insufficient processing speed of assigner.");

    public static final ConfigOption<String> INCREMENTAL_BETWEEN =
            key("incremental-between")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Read incremental changes between start snapshot (exclusive) and end snapshot, "
                                    + "for example, '5,10' means changes between snapshot 5 and snapshot 10.");

    public static final ConfigOption<IncrementalBetweenScanMode> INCREMENTAL_BETWEEN_SCAN_MODE =
            key("incremental-between-scan-mode")
                    .enumType(IncrementalBetweenScanMode.class)
                    .defaultValue(IncrementalBetweenScanMode.DELTA)
                    .withDescription(
                            "Scan kind when Read incremental changes between start snapshot (exclusive) and end snapshot, "
                                    + "'delta' for scan newly changed files between snapshots, 'changelog' scan changelog files between snapshots.");

    public static final ConfigOption<String> INCREMENTAL_BETWEEN_TIMESTAMP =
            key("incremental-between-timestamp")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Read incremental changes between start timestamp (exclusive) and end timestamp, "
                                    + "for example, 't1,t2' means changes between timestamp t1 and timestamp t2.");

    public static final String STATS_MODE_SUFFIX = "stats-mode";

    public static final ConfigOption<String> METADATA_STATS_MODE =
            key("metadata." + STATS_MODE_SUFFIX)
                    .stringType()
                    .defaultValue("truncate(16)")
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "The mode of metadata stats collection. none, counts, truncate(16), full is available.")
                                    .linebreak()
                                    .list(
                                            text(
                                                    "\"none\": means disable the metadata stats collection."))
                                    .list(text("\"counts\" means only collect the null count."))
                                    .list(
                                            text(
                                                    "\"full\": means collect the null count, min/max value."))
                                    .list(
                                            text(
                                                    "\"truncate(16)\": means collect the null count, min/max value with truncated length of 16."))
                                    .list(
                                            text(
                                                    "Field level stats mode can be specified by "
                                                            + FIELDS_PREFIX
                                                            + "."
                                                            + "{field_name}."
                                                            + STATS_MODE_SUFFIX))
                                    .build());

    public static final ConfigOption<String> COMMIT_CALLBACKS =
            key("commit.callbacks")
                    .stringType()
                    .defaultValue("")
                    .withDescription(
                            "A list of commit callback classes to be called after a successful commit. "
                                    + "Class names are connected with comma "
                                    + "(example: com.test.CallbackA,com.sample.CallbackB).");

    public static final ConfigOption<String> COMMIT_CALLBACK_PARAM =
            key("commit.callback.#.param")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Parameter string for the constructor of class #. "
                                    + "Callback class should parse the parameter by itself.");

    public static final ConfigOption<Boolean> METASTORE_PARTITIONED_TABLE =
            key("metastore.partitioned-table")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            "Whether to create this table as a partitioned table in metastore.\n"
                                    + "For example, if you want to list all partitions of a Paimon table in Hive, "
                                    + "you need to create this table as a partitioned table in Hive metastore.\n"
                                    + "This config option does not affect the default filesystem metastore.");

    public static final ConfigOption<TagCreationMode> TAG_AUTOMATIC_CREATION =
            key("tag.automatic-creation")
                    .enumType(TagCreationMode.class)
                    .defaultValue(TagCreationMode.NONE)
                    .withDescription(
                            "Whether to create tag automatically. And how to generate tags.");

    public static final ConfigOption<TagCreationPeriod> TAG_CREATION_PERIOD =
            key("tag.creation-period")
                    .enumType(TagCreationPeriod.class)
                    .defaultValue(TagCreationPeriod.DAILY)
                    .withDescription("What frequency is used to generate tags.");

    public static final ConfigOption<Duration> TAG_CREATION_DELAY =
            key("tag.creation-delay")
                    .durationType()
                    .defaultValue(Duration.ofMillis(0))
                    .withDescription(
                            "How long is the delay after the period ends before creating a tag."
                                    + " This can allow some late data to enter the Tag.");

    public static final ConfigOption<Integer> TAG_NUM_RETAINED_MAX =
            key("tag.num-retained-max")
                    .intType()
                    .noDefaultValue()
                    .withDescription("The maximum number of tags to retain.");

    public static final ConfigOption<Integer> PARQUET_ENABLE_DICTIONARY =
            key("parquet.enable.dictionary")
                    .intType()
                    .noDefaultValue()
                    .withDescription("Turn off the dictionary encoding for all fields in parquet.");

    public static final ConfigOption<Integer> ORC_COLUMN_ENCODING_DIRECT =
            key("orc.column.encoding.direct")
                    .intType()
                    .noDefaultValue()
                    .withDescription(
                            "Comma-separated list of fields for which dictionary encoding is to be skipped in orc.");

    public static final ConfigOption<Integer> ORC_DICTIONARY_KEY_THRESHOLD =
            key("orc.dictionary.key.threshold")
                    .intType()
                    .noDefaultValue()
                    .withDescription(
                            "If the number of distinct keys in a dictionary is greater than this "
                                    + "fraction of the total number of non-null rows, turn off "
                                    + "dictionary encoding in orc.  Use 1 to always use dictionary encoding.");

    public static final ConfigOption<String> SINK_WATERMARK_TIME_ZONE =
            key("sink.watermark-time-zone")
                    .stringType()
                    .defaultValue("UTC")
                    .withDescription(
                            "The time zone to parse the long watermark value to TIMESTAMP value."
                                    + " The default value is 'UTC', which means the watermark is defined on TIMESTAMP column or not defined."
                                    + " If the watermark is defined on TIMESTAMP_LTZ column, the time zone of watermark is user configured time zone,"
                                    + " the value should be the user configured local time zone. The option value is either a full name"
                                    + " such as 'America/Los_Angeles', or a custom timezone id such as 'GMT-08:00'.");

    public static final ConfigOption<MemorySize> LOCAL_MERGE_BUFFER_SIZE =
            key("local-merge-buffer-size")
                    .memoryType()
                    .noDefaultValue()
                    .withDescription(
                            "Local merge will buffer and merge input records "
                                    + "before they're shuffled by bucket and written into sink. "
                                    + "The buffer will be flushed when it is full.\n"
                                    + "Mainly to resolve data skew on primary keys. "
                                    + "We recommend starting with 64 mb when trying out this feature.");

    public static final ConfigOption<Duration> CROSS_PARTITION_UPSERT_INDEX_TTL =
            key("cross-partition-upsert.index-ttl")
                    .durationType()
                    .noDefaultValue()
                    .withDescription(
                            "The TTL in rocksdb index for cross partition upsert (primary keys not contain all partition fields), "
                                    + "this can avoid maintaining too many indexes and lead to worse and worse performance, "
                                    + "but please note that this may also cause data duplication.");

    public static final ConfigOption<String> CROSS_PARTITION_UPSERT_BOOTSTRAP_MIN_PARTITION =
            key("cross-partition-upsert.bootstrap-min-partition")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "The min partition bootstrap of rocksdb index for cross partition upsert (primary keys not contain all partition fields), "
                                    + "bootstrap will only read the partitions above it, and the smaller partitions will not be read into the index. "
                                    + "This can reduce job startup time and excessive initialization of index, "
                                    + "but please note that this may also cause data duplication.");

    public static final ConfigOption<Integer> ZORDER_VAR_LENGTH_CONTRIBUTION =
            key("zorder.var-length-contribution")
                    .intType()
                    .defaultValue(8)
                    .withDescription(
                            "The bytes of types (CHAR, VARCHAR, BINARY, VARBINARY) devote to the zorder sort.");

    private final Options options;

    public CoreOptions(Map<String, String> options) {
        this(Options.fromMap(options));
    }

    public CoreOptions(Options options) {
        this.options = options;
    }

    public static CoreOptions fromMap(Map<String, String> options) {
        return new CoreOptions(options);
    }

    public Options toConfiguration() {
        return options;
    }

    public Map<String, String> toMap() {
        return options.toMap();
    }

    public int bucket() {
        return options.get(BUCKET);
    }

    public Path path() {
        return path(options.toMap());
    }

    public static Path path(Map<String, String> options) {
        return new Path(options.get(PATH.key()));
    }

    public static Path path(Options options) {
        return new Path(options.get(PATH));
    }

    public FileFormatType formatType() {
        return options.get(FILE_FORMAT);
    }

    public FileFormat fileFormat() {
        return createFileFormat(options, FILE_FORMAT);
    }

    public FileFormat manifestFormat() {
        return createFileFormat(options, MANIFEST_FORMAT);
    }

    public MemorySize manifestTargetSize() {
        return options.get(MANIFEST_TARGET_FILE_SIZE);
    }

    public MemorySize manifestFullCompactionThresholdSize() {
        return options.get(MANIFEST_FULL_COMPACTION_FILE_SIZE);
    }

    public MemorySize writeManifestCache() {
        return options.get(WRITE_MANIFEST_CACHE);
    }

    public String partitionDefaultName() {
        return options.get(PARTITION_DEFAULT_NAME);
    }

    public static FileFormat createFileFormat(
            Options options, ConfigOption<FileFormatType> formatOption) {
        String formatIdentifier = options.get(formatOption).toString();
        return FileFormat.getFileFormat(options, formatIdentifier);
    }

    public Map<Integer, String> fileCompressionPerLevel() {
        Map<String, String> levelCompressions = options.get(FILE_COMPRESSION_PER_LEVEL);
        return levelCompressions.entrySet().stream()
                .collect(Collectors.toMap(e -> Integer.valueOf(e.getKey()), Map.Entry::getValue));
    }

    public Map<Integer, String> fileFormatPerLevel() {
        Map<String, String> levelFormats = options.get(FILE_FORMAT_PER_LEVEL);
        return levelFormats.entrySet().stream()
                .collect(Collectors.toMap(e -> Integer.valueOf(e.getKey()), Map.Entry::getValue));
    }

    public String fileCompression() {
        return options.get(FILE_COMPRESSION);
    }

    public int snapshotNumRetainMin() {
        return options.get(SNAPSHOT_NUM_RETAINED_MIN);
    }

    public int snapshotNumRetainMax() {
        return options.get(SNAPSHOT_NUM_RETAINED_MAX);
    }

    public Duration snapshotTimeRetain() {
        return options.get(SNAPSHOT_TIME_RETAINED);
    }

    public ExpireExecutionMode snapshotExpireExecutionMode() {
        return options.get(SNAPSHOT_EXPIRE_EXECUTION_MODE);
    }

    public int snapshotExpireLimit() {
        return options.get(SNAPSHOT_EXPIRE_LIMIT);
    }

    public int manifestMergeMinCount() {
        return options.get(MANIFEST_MERGE_MIN_COUNT);
    }

    public MergeEngine mergeEngine() {
        return options.get(MERGE_ENGINE);
    }

    public SortEngine sortEngine() {
        return options.get(SORT_ENGINE);
    }

    public int sortSpillThreshold() {
        Integer maxSortedRunNum = options.get(SORT_SPILL_THRESHOLD);
        if (maxSortedRunNum == null) {
            int stopNum = numSortedRunStopTrigger();
            maxSortedRunNum = Math.max(stopNum, stopNum + 1);
        }
        checkArgument(maxSortedRunNum > 1, "The sort spill threshold cannot be smaller than 2.");
        return maxSortedRunNum;
    }

    public long splitTargetSize() {
        return options.get(SOURCE_SPLIT_TARGET_SIZE).getBytes();
    }

    public long splitOpenFileCost() {
        return options.get(SOURCE_SPLIT_OPEN_FILE_COST).getBytes();
    }

    public long writeBufferSize() {
        return options.get(WRITE_BUFFER_SIZE).getBytes();
    }

    public boolean writeBufferSpillable(boolean usingObjectStore, boolean isStreaming) {
        // if not streaming mode, we turn spillable on by default.
        return options.getOptional(WRITE_BUFFER_SPILLABLE).orElse(usingObjectStore || !isStreaming);
    }

    public boolean useWriteBufferForAppend() {
        return options.get(WRITE_BUFFER_FOR_APPEND);
    }

    public long sortSpillBufferSize() {
        return options.get(SORT_SPILL_BUFFER_SIZE).getBytes();
    }

    public Duration continuousDiscoveryInterval() {
        return options.get(CONTINUOUS_DISCOVERY_INTERVAL);
    }

    public int localSortMaxNumFileHandles() {
        return options.get(LOCAL_SORT_MAX_NUM_FILE_HANDLES);
    }

    public int pageSize() {
        return (int) options.get(PAGE_SIZE).getBytes();
    }

    public long targetFileSize() {
        return options.get(TARGET_FILE_SIZE).getBytes();
    }

    public long compactionFileSize() {
        // file size to join the compaction, we don't process on middle file size to avoid
        // compact a same file twice (the compression is not calculate so accurately. the output
        // file maybe be less than target file generated by rolling file write).
        return options.get(TARGET_FILE_SIZE).getBytes() / 10 * 7;
    }

    public int numSortedRunCompactionTrigger() {
        return options.get(NUM_SORTED_RUNS_COMPACTION_TRIGGER);
    }

    public int numSortedRunStopTrigger() {
        Integer stopTrigger = options.get(NUM_SORTED_RUNS_STOP_TRIGGER);
        if (stopTrigger == null) {
            stopTrigger = numSortedRunCompactionTrigger() + 1;
        }
        return Math.max(numSortedRunCompactionTrigger(), stopTrigger);
    }

    public int numLevels() {
        // By default, this ensures that the compaction does not fall to level 0, but at least to
        // level 1
        Integer numLevels = options.get(NUM_LEVELS);
        numLevels = numLevels == null ? numSortedRunCompactionTrigger() + 1 : numLevels;
        return numLevels;
    }

    public boolean commitForceCompact() {
        return options.get(COMMIT_FORCE_COMPACT);
    }

    public int maxSizeAmplificationPercent() {
        return options.get(COMPACTION_MAX_SIZE_AMPLIFICATION_PERCENT);
    }

    public int sortedRunSizeRatio() {
        return options.get(COMPACTION_SIZE_RATIO);
    }

    public int compactionMinFileNum() {
        return options.get(COMPACTION_MIN_FILE_NUM);
    }

    public int compactionMaxFileNum() {
        return options.get(COMPACTION_MAX_FILE_NUM);
    }

    public long dynamicBucketTargetRowNum() {
        return options.get(DYNAMIC_BUCKET_TARGET_ROW_NUM);
    }

    public ChangelogProducer changelogProducer() {
        return options.get(CHANGELOG_PRODUCER);
    }

    public boolean changelogRowDeduplicate() {
        return options.get(CHANGELOG_PRODUCER_ROW_DEDUPLICATE);
    }

    public boolean scanPlanSortPartition() {
        return options.get(SCAN_PLAN_SORT_PARTITION);
    }

    public StartupMode startupMode() {
        return startupMode(options);
    }

    public static StartupMode startupMode(Options options) {
        StartupMode mode = options.get(SCAN_MODE);
        if (mode == StartupMode.DEFAULT) {
            if (options.getOptional(SCAN_TIMESTAMP_MILLIS).isPresent()) {
                return StartupMode.FROM_TIMESTAMP;
            } else if (options.getOptional(SCAN_SNAPSHOT_ID).isPresent()
                    || options.getOptional(SCAN_TAG_NAME).isPresent()) {
                return StartupMode.FROM_SNAPSHOT;
            } else if (options.getOptional(INCREMENTAL_BETWEEN).isPresent()
                    || options.getOptional(INCREMENTAL_BETWEEN_TIMESTAMP).isPresent()) {
                return StartupMode.INCREMENTAL;
            } else {
                return StartupMode.LATEST_FULL;
            }
        } else if (mode == StartupMode.FULL) {
            return StartupMode.LATEST_FULL;
        } else {
            return mode;
        }
    }

    public Long scanTimestampMills() {
        return options.get(SCAN_TIMESTAMP_MILLIS);
    }

    public Long scanBoundedWatermark() {
        return options.get(SCAN_BOUNDED_WATERMARK);
    }

    public Long scanSnapshotId() {
        return options.get(SCAN_SNAPSHOT_ID);
    }

    public String scanTagName() {
        return options.get(SCAN_TAG_NAME);
    }

    public Pair<String, String> incrementalBetween() {
        String str = options.get(INCREMENTAL_BETWEEN);
        if (str == null) {
            str = options.get(INCREMENTAL_BETWEEN_TIMESTAMP);
            if (str == null) {
                return null;
            }
        }

        String[] split = str.split(",");
        if (split.length != 2) {
            throw new IllegalArgumentException(
                    "The incremental-between or incremental-between-timestamp  must specific start(exclusive) and end snapshot or timestamp,"
                            + " for example, 'incremental-between'='5,10' means changes between snapshot 5 and snapshot 10. But is: "
                            + str);
        }
        return Pair.of(split[0], split[1]);
    }

    public IncrementalBetweenScanMode incrementalBetweenScanMode() {
        return options.get(INCREMENTAL_BETWEEN_SCAN_MODE);
    }

    public Integer scanManifestParallelism() {
        return options.get(SCAN_MANIFEST_PARALLELISM);
    }

    public Integer dynamicBucketAssignerParallelism() {
        return options.get(DYNAMIC_BUCKET_ASSIGNER_PARALLELISM);
    }

    public Optional<String> sequenceField() {
        return options.getOptional(SEQUENCE_FIELD);
    }

    public List<String> sequenceAutoPadding() {
        String padding = options.get(SEQUENCE_AUTO_PADDING);
        if (padding == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(padding.split(","));
    }

    public WriteMode writeMode() {
        return options.get(WRITE_MODE);
    }

    public boolean writeOnly() {
        return options.get(WRITE_ONLY);
    }

    public boolean streamingReadOverwrite() {
        return options.get(STREAMING_READ_OVERWRITE);
    }

    public boolean dynamicPartitionOverwrite() {
        return options.get(DYNAMIC_PARTITION_OVERWRITE);
    }

    public Duration partitionExpireTime() {
        return options.get(PARTITION_EXPIRATION_TIME);
    }

    public Duration partitionExpireCheckInterval() {
        return options.get(PARTITION_EXPIRATION_CHECK_INTERVAL);
    }

    public String partitionTimestampFormatter() {
        return options.get(PARTITION_TIMESTAMP_FORMATTER);
    }

    public String partitionTimestampPattern() {
        return options.get(PARTITION_TIMESTAMP_PATTERN);
    }

    public int readBatchSize() {
        return options.get(READ_BATCH_SIZE);
    }

    public String consumerId() {
        return options.get(CONSUMER_ID);
    }

    public static StreamingReadMode streamReadType(Options options) {
        return options.get(STREAMING_READ_MODE);
    }

    public Duration consumerExpireTime() {
        return options.get(CONSUMER_EXPIRATION_TIME);
    }

    public boolean partitionedTableInMetastore() {
        return options.get(METASTORE_PARTITIONED_TABLE);
    }

    public TagCreationMode tagCreationMode() {
        return options.get(TAG_AUTOMATIC_CREATION);
    }

    public TagCreationPeriod tagCreationPeriod() {
        return options.get(TAG_CREATION_PERIOD);
    }

    public Duration tagCreationDelay() {
        return options.get(TAG_CREATION_DELAY);
    }

    public Integer tagNumRetainedMax() {
        return options.get(TAG_NUM_RETAINED_MAX);
    }

    public String sinkWatermarkTimeZone() {
        return options.get(SINK_WATERMARK_TIME_ZONE);
    }

    public Map<String, String> getFieldDefaultValues() {
        Map<String, String> defaultValues = new HashMap<>();
        String fieldPrefix = FIELDS_PREFIX + ".";
        String defaultValueSuffix = "." + DEFAULT_VALUE_SUFFIX;
        for (Map.Entry<String, String> option : options.toMap().entrySet()) {
            String key = option.getKey();
            if (key != null && key.startsWith(fieldPrefix) && key.endsWith(defaultValueSuffix)) {
                String fieldName = key.replace(fieldPrefix, "").replace(defaultValueSuffix, "");
                defaultValues.put(fieldName, option.getValue());
            }
        }
        return defaultValues;
    }

    public Map<String, String> commitCallbacks() {
        Map<String, String> result = new HashMap<>();
        for (String className : options.get(COMMIT_CALLBACKS).split(",")) {
            className = className.trim();
            if (className.length() == 0) {
                continue;
            }

            String param = options.get(COMMIT_CALLBACK_PARAM.key().replace("#", className));
            result.put(className, param);
        }
        return result;
    }

    public int orcWriteBatch() {
        return options.getInteger(ORC_WRITE_BATCH_SIZE.key(), ORC_WRITE_BATCH_SIZE.defaultValue());
    }

    public boolean localMergeEnabled() {
        return options.get(LOCAL_MERGE_BUFFER_SIZE) != null;
    }

    public long localMergeBufferSize() {
        return options.get(LOCAL_MERGE_BUFFER_SIZE).getBytes();
    }

    public Duration crossPartitionUpsertIndexTtl() {
        return options.get(CROSS_PARTITION_UPSERT_INDEX_TTL);
    }

    public String crossPartitionUpsertBootstrapMinPartition() {
        return options.get(CROSS_PARTITION_UPSERT_BOOTSTRAP_MIN_PARTITION);
    }

    public int varTypeSize() {
        return options.get(ZORDER_VAR_LENGTH_CONTRIBUTION);
    }

    /** Specifies the merge engine for table with primary key. */
    public enum MergeEngine implements DescribedEnum {
        DEDUPLICATE("deduplicate", "De-duplicate and keep the last row."),

        PARTIAL_UPDATE("partial-update", "Partial update non-null fields."),

        AGGREGATE("aggregation", "Aggregate fields with same primary key."),

        FIRST_ROW("first-row", "De-duplicate and keep the first row.");

        private final String value;
        private final String description;

        MergeEngine(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }
    }

    /** Specifies the startup mode for log consumer. */
    public enum StartupMode implements DescribedEnum {
        DEFAULT(
                "default",
                "Determines actual startup mode according to other table properties. "
                        + "If \"scan.timestamp-millis\" is set the actual startup mode will be \"from-timestamp\", "
                        + "and if \"scan.snapshot-id\" or \"scan.tag-name\" is set the actual startup mode will be \"from-snapshot\". "
                        + "Otherwise the actual startup mode will be \"latest-full\"."),

        LATEST_FULL(
                "latest-full",
                "For streaming sources, produces the latest snapshot on the table upon first startup, "
                        + "and continue to read the latest changes. "
                        + "For batch sources, just produce the latest snapshot but does not read new changes."),

        FULL("full", "Deprecated. Same as \"latest-full\"."),

        LATEST(
                "latest",
                "For streaming sources, continuously reads latest changes "
                        + "without producing a snapshot at the beginning. "
                        + "For batch sources, behaves the same as the \"latest-full\" startup mode."),

        COMPACTED_FULL(
                "compacted-full",
                "For streaming sources, produces a snapshot after the latest compaction on the table "
                        + "upon first startup, and continue to read the latest changes. "
                        + "For batch sources, just produce a snapshot after the latest compaction "
                        + "but does not read new changes. Snapshots of full compaction are picked "
                        + "when scheduled full-compaction is enabled."),

        FROM_TIMESTAMP(
                "from-timestamp",
                "For streaming sources, continuously reads changes "
                        + "starting from timestamp specified by \"scan.timestamp-millis\", "
                        + "without producing a snapshot at the beginning. "
                        + "For batch sources, produces a snapshot at timestamp specified by \"scan.timestamp-millis\" "
                        + "but does not read new changes."),

        FROM_SNAPSHOT(
                "from-snapshot",
                "For streaming sources, continuously reads changes starting from snapshot "
                        + "specified by \"scan.snapshot-id\", without producing a snapshot at the beginning. "
                        + "For batch sources, produces a snapshot specified by \"scan.snapshot-id\" "
                        + "or \"scan.tag-name\" but does not read new changes."),

        FROM_SNAPSHOT_FULL(
                "from-snapshot-full",
                "For streaming sources, produces from snapshot specified by \"scan.snapshot-id\" "
                        + "on the table upon first startup, and continuously reads changes. For batch sources, "
                        + "produces a snapshot specified by \"scan.snapshot-id\" but does not read new changes."),

        INCREMENTAL(
                "incremental",
                "Read incremental changes between start and end snapshot or timestamp.");

        private final String value;
        private final String description;

        StartupMode(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }
    }

    /** Specifies the log consistency mode for table. */
    public enum LogConsistency implements DescribedEnum {
        TRANSACTIONAL(
                "transactional",
                "Only the data after the checkpoint can be seen by readers, the latency depends on checkpoint interval."),

        EVENTUAL(
                "eventual",
                "Immediate data visibility, you may see some intermediate states, "
                        + "but eventually the right results will be produced, only works for table with primary key.");

        private final String value;
        private final String description;

        LogConsistency(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }
    }

    /** Specifies the log changelog mode for table. */
    public enum LogChangelogMode implements DescribedEnum {
        AUTO("auto", "Upsert for table with primary key, all for table without primary key."),

        ALL("all", "The log system stores all changes including UPDATE_BEFORE."),

        UPSERT(
                "upsert",
                "The log system does not store the UPDATE_BEFORE changes, the log consumed job"
                        + " will automatically add the normalized node, relying on the state"
                        + " to generate the required update_before.");

        private final String value;
        private final String description;

        LogChangelogMode(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }
    }

    /** Specifies the changelog producer for table. */
    public enum ChangelogProducer implements DescribedEnum {
        NONE("none", "No changelog file."),

        INPUT(
                "input",
                "Double write to a changelog file when flushing memory table, the changelog is from input."),

        FULL_COMPACTION("full-compaction", "Generate changelog files with each full compaction."),

        LOOKUP(
                "lookup",
                "Generate changelog files through 'lookup' before committing the data writing.");

        private final String value;
        private final String description;

        ChangelogProducer(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }
    }

    /** Specifies the file format type for store. */
    public enum FileFormatType implements DescribedEnum {
        ORC("orc", "ORC file format."),
        PARQUET("parquet", "Parquet file format."),
        AVRO("avro", "Avro file format.");

        private final String value;
        private final String description;

        FileFormatType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }

        @VisibleForTesting
        public static FileFormatType fromValue(String value) {
            for (FileFormatType formatType : FileFormatType.values()) {
                if (formatType.value.equals(value)) {
                    return formatType;
                }
            }
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid format type %s, only support [%s]",
                            value,
                            StringUtils.join(
                                    Arrays.stream(FileFormatType.values()).iterator(), ",")));
        }
    }

    /** Specifies the type for streaming read. */
    public enum StreamingReadMode implements DescribedEnum {
        LOG("log", "Reads from the log store."),
        FILE("file", "Reads from the file store.");

        private final String value;
        private final String description;

        StreamingReadMode(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }

        public String getValue() {
            return value;
        }

        @VisibleForTesting
        public static StreamingReadMode fromValue(String value) {
            for (StreamingReadMode formatType : StreamingReadMode.values()) {
                if (formatType.value.equals(value)) {
                    return formatType;
                }
            }
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid format type %s, only support [%s]",
                            value,
                            StringUtils.join(
                                    Arrays.stream(StreamingReadMode.values()).iterator(), ",")));
        }
    }

    /** Inner stream scan mode for some internal requirements. */
    public enum StreamScanMode implements DescribedEnum {
        NONE("none", "No requirement."),
        COMPACT_BUCKET_TABLE("compact-bucket-table", "Compaction for traditional bucket table."),
        COMPACT_APPEND_NO_BUCKET(
                "compact-append-no-bucket", "Compaction for append table with bucket unaware."),
        FILE_MONITOR("file-monitor", "Monitor data file changes.");

        private final String value;
        private final String description;

        StreamScanMode(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }

        public String getValue() {
            return value;
        }
    }

    /** Specifies this scan type for incremental scan . */
    public enum IncrementalBetweenScanMode implements DescribedEnum {
        DELTA("delta", "Scan newly changed files between snapshots."),
        CHANGELOG("changelog", "Scan changelog files between snapshots.");

        private final String value;
        private final String description;

        IncrementalBetweenScanMode(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }

        public String getValue() {
            return value;
        }

        @VisibleForTesting
        public static IncrementalBetweenScanMode fromValue(String value) {
            for (IncrementalBetweenScanMode formatType : IncrementalBetweenScanMode.values()) {
                if (formatType.value.equals(value)) {
                    return formatType;
                }
            }
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid format type %s, only support [%s]",
                            value,
                            StringUtils.join(
                                    Arrays.stream(IncrementalBetweenScanMode.values()).iterator(),
                                    ",")));
        }
    }

    /**
     * Set the default values of the {@link CoreOptions} via the given {@link Options}.
     *
     * @param options the options to set default values
     */
    public static void setDefaultValues(Options options) {
        if (options.contains(SCAN_TIMESTAMP_MILLIS) && !options.contains(SCAN_MODE)) {
            options.set(SCAN_MODE, StartupMode.FROM_TIMESTAMP);
        }

        if (options.contains(SCAN_SNAPSHOT_ID) && !options.contains(SCAN_MODE)) {
            options.set(SCAN_MODE, StartupMode.FROM_SNAPSHOT);
        }

        if ((options.contains(INCREMENTAL_BETWEEN_TIMESTAMP)
                        || options.contains(INCREMENTAL_BETWEEN))
                && !options.contains(SCAN_MODE)) {
            options.set(SCAN_MODE, StartupMode.INCREMENTAL);
        }
    }

    public static List<ConfigOption<?>> getOptions() {
        final Field[] fields = CoreOptions.class.getFields();
        final List<ConfigOption<?>> list = new ArrayList<>(fields.length);
        for (Field field : fields) {
            if (ConfigOption.class.isAssignableFrom(field.getType())) {
                try {
                    list.add((ConfigOption<?>) field.get(CoreOptions.class));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return list;
    }

    public static Set<String> getImmutableOptionKeys() {
        final Field[] fields = CoreOptions.class.getFields();
        final Set<String> immutableKeys = new HashSet<>(fields.length);
        for (Field field : fields) {
            if (ConfigOption.class.isAssignableFrom(field.getType())
                    && field.getAnnotation(Immutable.class) != null) {
                try {
                    immutableKeys.add(((ConfigOption<?>) field.get(CoreOptions.class)).key());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return immutableKeys;
    }

    /** Specifies the sort engine for table with primary key. */
    public enum SortEngine implements DescribedEnum {
        MIN_HEAP("min-heap", "Use min-heap for multiway sorting."),
        LOSER_TREE(
                "loser-tree",
                "Use loser-tree for multiway sorting. Compared with heapsort, loser-tree has fewer comparisons and is more efficient.");

        private final String value;
        private final String description;

        SortEngine(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }
    }

    /** Specifies the way of making up time precision for sequence field. */
    public enum SequenceAutoPadding implements DescribedEnum {
        ROW_KIND_FLAG(
                "row-kind-flag",
                "Pads a bit flag to indicate whether it is retract (0) or add (1) message."),
        SECOND_TO_MICRO(
                "second-to-micro",
                "Pads the sequence field that indicates time with precision of seconds to micro-second."),
        MILLIS_TO_MICRO(
                "millis-to-micro",
                "Pads the sequence field that indicates time with precision of milli-second to micro-second.");

        private final String value;
        private final String description;

        SequenceAutoPadding(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }

        public static SequenceAutoPadding fromString(String s) {
            return OptionsUtils.convertToEnum(s, SequenceAutoPadding.class);
        }
    }

    /** The mode for tag creation. */
    public enum TagCreationMode implements DescribedEnum {
        NONE("none", "No automatically created tags."),
        PROCESS_TIME(
                "process-time",
                "Based on the time of the machine, create TAG once the processing time passes period time plus delay."),
        WATERMARK(
                "watermark",
                "Based on the watermark of the input, create TAG once the watermark passes period time plus delay.");

        private final String value;
        private final String description;

        TagCreationMode(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }
    }

    /** The period for tag creation. */
    public enum TagCreationPeriod implements DescribedEnum {
        DAILY("daily", "Generate a tag every day."),
        HOURLY("hourly", "Generate a tag every hour."),
        TWO_HOURS("two-hours", "Generate a tag every two hours.");

        private final String value;
        private final String description;

        TagCreationPeriod(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }
    }

    /** The execution mode for expire. */
    public enum ExpireExecutionMode implements DescribedEnum {
        SYNC(
                "sync",
                "Execute expire synchronously. If there are too many files, it may take a long time and block stream processing."),
        ASYNC(
                "async",
                "Execute expire asynchronously. If the generation of snapshots is greater than the deletion, there will be a backlog of files.");

        private final String value;
        private final String description;

        ExpireExecutionMode(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public InlineElement getDescription() {
            return text(description);
        }
    }
}
