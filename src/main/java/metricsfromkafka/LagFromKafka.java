package metricsfromkafka;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutionException;


// This call can be used to get the lag directly from Kafka,
// in case Kafka exporter was not the best solution

public class LagFromKafka {
    private static final Logger log = LogManager.getLogger(LagFromKafka.class);
    public static String CONSUMER_GROUP;
    public static AdminClient admin = null;
    static String topic;
    static String BOOTSTRAP_SERVERS;
    static Map<TopicPartition, OffsetAndMetadata> committedOffsets;
    static long totalLag;
    static int nbofPartitions;

    public  static void readEnvAndCrateAdminClient() throws ExecutionException, InterruptedException {
        //can be passed as env variable as well.
        topic = "yourTopic";
        CONSUMER_GROUP = "YourGroup";
        BOOTSTRAP_SERVERS = System.getenv("BOOTSTRAP_SERVERS");
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        admin = AdminClient.create(props);
        nbofPartitions = getNumberOfPartitions();
    }

    private static int getNumberOfPartitions() throws ExecutionException, InterruptedException {
        // Retrieve topic description
        DescribeTopicsResult describeTopicsResult = admin.describeTopics(Collections.singleton(topic));
        KafkaFuture<TopicDescription> topicDescriptionFuture = describeTopicsResult.values().get(topic);
        TopicDescription topicDescription = topicDescriptionFuture.get();
        nbofPartitions = topicDescription.partitions().size();
        return nbofPartitions;
    }


    public static void computeLag() throws ExecutionException, InterruptedException {
        committedOffsets = admin.listConsumerGroupOffsets(CONSUMER_GROUP)
                .partitionsToOffsetAndMetadata().get();
        Map<TopicPartition, OffsetSpec> requestLatestOffsets = new HashMap<>();
        for (int i = 0; i < nbofPartitions; i++) {
            requestLatestOffsets.put(new TopicPartition(topic, i), OffsetSpec.latest());
        }
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                admin.listOffsets(requestLatestOffsets).all().get();
        totalLag=0L;
        for (int i = 0; i < nbofPartitions; i++) {
            TopicPartition t = new TopicPartition(topic, i);
            long latestOffset = latestOffsets.get(t).offset();
            long committedoffset = committedOffsets.get(t).offset();
            totalLag += (latestOffset-committedoffset);
        }
        log.info("total lag {}", totalLag);
    }


}