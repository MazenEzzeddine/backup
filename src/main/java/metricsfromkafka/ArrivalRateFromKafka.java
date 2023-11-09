package metricsfromkafka;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;



public class ArrivalRateFromKafka {
    private static final Logger log = LogManager.getLogger(ArrivalRateFromKafka.class);
    public static String CONSUMER_GROUP;
    public static AdminClient admin = null;
    static Long sleep;
    static double doublesleep;
    static String topic;
    static String cluster;
    static Long poll;
    static String BOOTSTRAP_SERVERS;
    static Map<TopicPartition, OffsetAndMetadata> committedOffsets;


    static Map<String, ConsumerGroupDescription> consumerGroupDescriptionMap;
    //////////////////////////////////////////////////////////////////////////////
    static TopicDescription td;
    static DescribeTopicsResult tdr;
    static ArrayList<Partition> partitions = new ArrayList<>();

    static double dynamicTotalMaxConsumptionRate = 0.0;
    static double dynamicAverageMaxConsumptionRate = 0.0;

    static double wsla = 5.0;
    static List<Consumer> assignment;
    static Instant lastScaleUpDecision;
    static Instant lastScaleDownDecision;
    static Instant lastCGQuery;


    private static void readEnvAndCrateAdminClient() throws ExecutionException, InterruptedException {
        sleep = Long.valueOf(System.getenv("SLEEP"));
        topic = System.getenv("TOPIC");
        cluster = System.getenv("CLUSTER");
        poll = Long.valueOf(System.getenv("POLL"));
        CONSUMER_GROUP = System.getenv("CONSUMER_GROUP");
        BOOTSTRAP_SERVERS = System.getenv("BOOTSTRAP_SERVERS");
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        admin = AdminClient.create(props);
        tdr = admin.describeTopics(Collections.singletonList(topic));
        td = tdr.values().get(topic).get();
        lastScaleUpDecision = Instant.now();
        lastScaleDownDecision = Instant.now();
        lastCGQuery = Instant.now();

        for (TopicPartitionInfo p : td.partitions()) {
            partitions.add(new Partition(p.partition(), 0, 0));
        }
        log.info("topic has the following partitions {}", td.partitions().size());
    }


    private static void queryConsumerGroup() throws ExecutionException, InterruptedException {
        DescribeConsumerGroupsResult describeConsumerGroupsResult =
                admin.describeConsumerGroups(Collections
                        .singletonList(ArrivalRateFromKafka.CONSUMER_GROUP));
        KafkaFuture<Map<String, ConsumerGroupDescription>> futureOfDescribeConsumerGroupsResult =
                describeConsumerGroupsResult.all();

        consumerGroupDescriptionMap = futureOfDescribeConsumerGroupsResult.get();

        dynamicTotalMaxConsumptionRate = 0.0;
        for (MemberDescription memberDescription : consumerGroupDescriptionMap
                .get(ArrivalRateFromKafka.CONSUMER_GROUP).members()) {
            log.info("Calling the consumer {} for its consumption rate ",
                    memberDescription.host());

            float rate = callForConsumptionRate(memberDescription.host());
            dynamicTotalMaxConsumptionRate += rate;
        }

        dynamicAverageMaxConsumptionRate = dynamicTotalMaxConsumptionRate /
                (float) consumerGroupDescriptionMap.get(ArrivalRateFromKafka.CONSUMER_GROUP)
                        .members().size();

        log.info("The total consumption rate of the CG is {}", String.format("%.2f",
                dynamicTotalMaxConsumptionRate));
        log.info("The average consumption rate of the CG is {}", String.format("%.2f",
                dynamicAverageMaxConsumptionRate));
    }


    private static float callForConsumptionRate(String host) {
        ManagedChannel managedChannel = ManagedChannelBuilder.forAddress(host.substring(1), 5002)
                .usePlaintext()
                .build();

        RateServiceGrpc.RateServiceBlockingStub rateServiceBlockingStub
                = RateServiceGrpc.newBlockingStub(managedChannel);
        RateRequest rateRequest = RateRequest.newBuilder().setRate("Give me your rate")
                .build();
        log.info("connected to server {}", host);
        RateResponse rateResponse = rateServiceBlockingStub.consumptionRate(rateRequest);
        log.info("Received response on the rate: " + String.format("%.2f", rateResponse.getRate()));
        managedChannel.shutdown();
        return rateResponse.getRate();
    }

    private static void getCommittedLatestOffsetsAndLag() throws ExecutionException, InterruptedException {
        committedOffsets = admin.listConsumerGroupOffsets(CONSUMER_GROUP)
                .partitionsToOffsetAndMetadata().get();

        Map<TopicPartition, OffsetSpec> requestLatestOffsets = new HashMap<>();
        Map<TopicPartition, OffsetSpec> requestTimestampOffsets1 = new HashMap<>();
        Map<TopicPartition, OffsetSpec> requestTimestampOffsets2 = new HashMap<>();

        for (TopicPartitionInfo p : td.partitions()) {
            requestLatestOffsets.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest());
            requestTimestampOffsets2.put(new TopicPartition(topic, p.partition()),
                    OffsetSpec.forTimestamp(Instant.now().minusMillis(1500).toEpochMilli()));
            requestTimestampOffsets1.put(new TopicPartition(topic, p.partition()),
                    OffsetSpec.forTimestamp(Instant.now().minusMillis(sleep + 1500).toEpochMilli()));
        }

        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                admin.listOffsets(requestLatestOffsets).all().get();
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> timestampOffsets1 =
                admin.listOffsets(requestTimestampOffsets1).all().get();
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> timestampOffsets2 =
                admin.listOffsets(requestTimestampOffsets2).all().get();


        long totalArrivalRate = 0;
        double currentPartitionArrivalRate;
        Map<Integer, Double> previousPartitionArrivalRate = new HashMap<>();
        for (TopicPartitionInfo p : td.partitions()) {
            previousPartitionArrivalRate.put(p.partition(), 0.0);
        }

        for (TopicPartitionInfo p : td.partitions()) {
            TopicPartition t = new TopicPartition(topic, p.partition());
            long latestOffset = latestOffsets.get(t).offset();
            long timeoffset1 = timestampOffsets1.get(t).offset();
            long timeoffset2 = timestampOffsets2.get(t).offset();
            long committedoffset = committedOffsets.get(t).offset();
            partitions.get(p.partition()).setLag(latestOffset - committedoffset);
            //TODO if abs(currentPartitionArrivalRate -  previousPartitionArrivalRate) > 15
            //TODO currentPartitionArrivalRate= previousPartitionArrivalRate;
            currentPartitionArrivalRate = (double) (timeoffset2 - timeoffset1) / doublesleep;
            //TODO only update currentPartitionArrivalRate if
            // (currentPartitionArrivalRate - previousPartitionArrivalRate) < 10 or threshold
            // currentPartitionArrivalRate = previousPartitionArrivalRate.get(p.partition());
            //TODO break
            partitions.get(p.partition()).setArrivalRate(currentPartitionArrivalRate);
            log.info(" Arrival rate into partition {} is {}", t.partition(),
                    partitions.get(p.partition()).getArrivalRate());

            log.info(" lag of  partition {} is {}", t.partition(),
                    partitions.get(p.partition()).getLag());
            log.info(partitions.get(p.partition()));

            //TODO add a condition for when both offsets timeoffset2 and timeoffset1 do not exist, i.e., are -1,
            previousPartitionArrivalRate.put(p.partition(), currentPartitionArrivalRate);
            totalArrivalRate += currentPartitionArrivalRate;
        }
        //report total arrival only if not zero only the loop has not exited.
        log.info("totalArrivalRate {}", totalArrivalRate);


        // attention not to have this CG querying interval less than cooldown interval
        if (Duration.between(lastCGQuery, Instant.now()).toSeconds() >= 30) {
            queryConsumerGroup();
            lastCGQuery = Instant.now();
        }
    }

}
