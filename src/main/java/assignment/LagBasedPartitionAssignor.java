package assignment;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.protocol.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;


public class LagBasedPartitionAssignor extends AbstractAssignor implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LagBasedPartitionAssignor.class);





    private   String thenamelocal = System.getenv("THENAME");



    public LagBasedPartitionAssignor() {
        LOGGER.info("Constructor");
        thenamelocal = System.getenv("THENAME");
    }

    private Properties consumerGroupProps;
    private Properties metadataConsumerProps;
    private KafkaConsumer<byte[], byte[]> metadataConsumer;

    static final String TOPIC_PARTITIONS_KEY_NAME = "previous_assignment";
    static final String TOPIC_KEY_NAME = "topic";
    static final String PARTITIONS_KEY_NAME = "partitions";
    ;
    static final String MAX_CONSUMPTION_RATE = "maxConsumptionRate";

    static final String THE_name = "thename";



    private static final String GENERATION_KEY_NAME = "generation";

    static final Schema TOPIC_ASSIGNMENT = new Schema(
            new Field(TOPIC_KEY_NAME, Type.STRING),
            new Field(PARTITIONS_KEY_NAME, new ArrayOf(Type.INT32))
    );
    static final Schema STICKY_ASSIGNOR_USER_DATA_V0 = new Schema(
            new Field(TOPIC_PARTITIONS_KEY_NAME, new ArrayOf(TOPIC_ASSIGNMENT)));
    private static final Schema STICKY_ASSIGNOR_USER_DATA_V1 = new Schema(
           // new Field(TOPIC_PARTITIONS_KEY_NAME, new ArrayOf(TOPIC_ASSIGNMENT)),
            new Field(GENERATION_KEY_NAME, Type.INT32),
            new Field(MAX_CONSUMPTION_RATE, Type.FLOAT64),
            new Field(THE_name, Type.STRING));

    private List<TopicPartition> memberAssignment = null;
    private int generation = DEFAULT_GENERATION; // consumer group generation

    private static Map<String, Double> memberToRate = null;
    private static Map<String, String> memberToName = null;





    @Override
    protected AbstractAssignor.MemberData memberData(ConsumerPartitionAssignor.Subscription subscription) {
        ByteBuffer userData = subscription.userData();
        if (userData == null || !userData.hasRemaining()) {
            return new AbstractAssignor.MemberData(
                    0.0d, "empty", Optional.empty());
        }
        return deserializeTopicPartitionAssignment(userData);
    }

    private static AbstractAssignor.MemberData deserializeTopicPartitionAssignment(ByteBuffer buffer) {
        Struct struct;
        ByteBuffer copy = buffer.duplicate();
        try {
            struct = STICKY_ASSIGNOR_USER_DATA_V1.read(buffer);
        } catch (Exception e1) {
            try {
                // fall back to older schema
                struct = STICKY_ASSIGNOR_USER_DATA_V0.read(copy);
            } catch (Exception e2) {
                // ignore the consumer's previous assignment if it cannot be parsed
                return new AbstractAssignor.MemberData( 0.0d,"empty", Optional.of(DEFAULT_GENERATION));
            }
        }
       /* List<TopicPartition> partitions = new ArrayList<>();
        // List<Double> rates = new ArrayList<>();
        for (Object structObj : struct.getArray(TOPIC_PARTITIONS_KEY_NAME)) {
            Struct assignment = (Struct) structObj;
            String topic = assignment.getString(TOPIC_KEY_NAME);
            for (Object partitionObj : assignment.getArray(PARTITIONS_KEY_NAME)) {
                Integer partition = (Integer) partitionObj;
                partitions.add(new TopicPartition(topic, partition));
            }


        }*/
        LOGGER.info( "Maximum rate is {}", struct.getDouble(MAX_CONSUMPTION_RATE));
        LOGGER.info( "host name is {}", struct.getString(THE_name));

        Optional<Integer> generation = struct.hasField(GENERATION_KEY_NAME) ?
                Optional.of(struct.getInt(GENERATION_KEY_NAME)) : Optional.empty();
        Double maxRate = struct.hasField(MAX_CONSUMPTION_RATE) ? struct.getDouble(MAX_CONSUMPTION_RATE) : 0.0;
        String thename = struct.hasField(THE_name) ? struct.getString(THE_name) : "empty";

        return new AbstractAssignor.MemberData( maxRate, thename ,  generation);
    }





    @Override
    public ByteBuffer subscriptionUserData(Set<String> topics) {
     /*   if (memberAssignment == null)
            return null;*/
        //memberAssignment=Collections.emptyList();
        LOGGER.info("Inside sub user data and KafkaConsumerConfig.thename is {}",System.getenv("THENAME"));
        return serializeTopicPartitionAssignment(new AbstractAssignor.MemberData(
                ConsumerThread.maxConsumptionRatePerConsumer1, System.getenv("THENAME"), Optional.of(generation)));
    }

    // visible for testing
    static ByteBuffer serializeTopicPartitionAssignment(AbstractAssignor.MemberData memberData) {
        Struct struct = new Struct(STICKY_ASSIGNOR_USER_DATA_V1);
        List<Struct> topicAssignments = new ArrayList<>();
        /*for (Map.Entry<String, List<Integer>> topicEntry :
                CollectionUtils.groupPartitionsByTopic(memberData.partitions).entrySet()) {
            Struct topicAssignment = new Struct(TOPIC_ASSIGNMENT);
            topicAssignment.set(TOPIC_KEY_NAME, topicEntry.getKey());
            topicAssignment.set(PARTITIONS_KEY_NAME, topicEntry.getValue().toArray());
            topicAssignments.add(topicAssignment);
        }
        struct.set(TOPIC_PARTITIONS_KEY_NAME, topicAssignments.toArray());*/
        if (memberData.generation.isPresent())
            struct.set(GENERATION_KEY_NAME, memberData.generation.get());
        struct.set(MAX_CONSUMPTION_RATE, memberData.maxConsumptionRate);
        struct.set(THE_name, memberData.name);


        ByteBuffer buffer = ByteBuffer.allocate(STICKY_ASSIGNOR_USER_DATA_V1.sizeOf(struct));
        STICKY_ASSIGNOR_USER_DATA_V1.write(buffer, struct);
        buffer.flip();
        return buffer;
    }


    @Override
    public void onAssignment(ConsumerPartitionAssignor.Assignment assignment, ConsumerGroupMetadata metadata) {
        // TODO
        // if there is something to that is returned and to be saved across generations
        memberAssignment = assignment.partitions();
        this.generation = metadata.generationId();
        LOGGER.info(" Received the assignment and my partitions are:");

        for(TopicPartition tp: assignment.partitions())
            LOGGER.info("partition : {} {}",  tp.toString(), tp.partition());
    }

    @Override
    public String name() {
        return "LagAndStickyAwareAssignor";
    }

    @Override
    public ConsumerPartitionAssignor.GroupAssignment assign(Cluster metadata, ConsumerPartitionAssignor.GroupSubscription subscriptions) {

        if (metadataConsumer == null) {
            metadataConsumer = new KafkaConsumer<>(metadataConsumerProps);
        }
        memberToRate = new HashMap<>();
        memberToName = new HashMap<>();

        LOGGER.info(System.getenv("THENAME"));

        final Set<String> allSubscribedTopics = new HashSet<>();
        final Map<String, List<String>> topicSubscriptions = new HashMap<>();
        for (Map.Entry<String, ConsumerPartitionAssignor.Subscription> subscriptionEntry :
                subscriptions.groupSubscription().entrySet()) {
            printPreviousAssignments(subscriptionEntry.getKey(),  subscriptionEntry.getValue() );
            List<String> topics = subscriptionEntry.getValue().topics();
            //LOGGER.info("maximum consumption rate is {}", );
            allSubscribedTopics.addAll(topics);
            topicSubscriptions.put(subscriptionEntry.getKey(), topics);
        }
        final Map<String, List<TopicPartitionLag>> topicLags =
                readTopicPartitionLags(metadata, allSubscribedTopics);
        Map<String, List<TopicPartition>> rawAssignments =
                assign(topicLags, topicSubscriptions);

        // this class has maintains no user data, so just wrap the results
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = new HashMap<>();
        for (Map.Entry<String, List<TopicPartition>> assignmentEntry : rawAssignments.entrySet()) {
            assignments.put(assignmentEntry.getKey(), new ConsumerPartitionAssignor.Assignment(assignmentEntry.getValue()));
        }
        return new ConsumerPartitionAssignor.GroupAssignment(assignments);
    }


    void printPreviousAssignments(String memberid, ConsumerPartitionAssignor.Subscription sub) {
        AbstractAssignor.MemberData md = memberData(sub);
        memberToRate.put(memberid, md.maxConsumptionRate);
        memberToName.put(memberid, md.name);
        LOGGER.info("MaxConsumptionRate {} for {}", memberid, md.maxConsumptionRate);
        LOGGER.info("name of host is {} memberid {}", memberid, md.name);

    }



    //for each consumer returns the list of topic partitions assigned to it.
    static Map<String, List<TopicPartition>> assign(
            Map<String, List<TopicPartitionLag>> partitionLagPerTopic,
            Map<String, List<String>> subscriptions
    ) {
        // each memmber/consumer to its propsective assignment
        final Map<String, List<TopicPartition>> assignment = new HashMap<>();
        for (String memberId : subscriptions.keySet()) {
            assignment.put(memberId, new ArrayList<>());
        }
        //for each topic assign call assigntopic to perform lag-aware assignment per topic
        final Map<String, List<String>> consumersPerTopic = consumersPerTopic(subscriptions);
        for (Map.Entry<String, List<String>> topicEntry : consumersPerTopic.entrySet()) {
            assignTopic(
                    assignment,
                    //topic
                    topicEntry.getKey(),
                    //consumers
                    topicEntry.getValue(),
                    partitionLagPerTopic.getOrDefault(topicEntry.getKey(), Collections.emptyList())
            );
        }
        return assignment;
    }



    private static void assignTopic(
            final Map<String, List<TopicPartition>> assignment,
            final String topic,
            final List<String> consumers,
            final List<TopicPartitionLag> partitionLags) {
        if (consumers.isEmpty()) {
            return;
        }

        for (String cons: consumers) {
            LOGGER.info("member id {} is equivalent to host id {} :", cons,  memberToName.get(cons));
        }
        // Track total lag assigned to each consumer (for the current topic)
        final Map<String, Long> consumerTotalLags = new HashMap<>(consumers.size());
        for (String memberId : consumers) {
            consumerTotalLags.put(memberId, 0L);
            LOGGER.info("member id {} has the following rate {}", memberId, memberToRate.get(memberId));
                    LOGGER.info("member id {} has the following host name  {}", memberId, memberToName.get(memberId));
        }
        // Track total number of partitions assigned to each consumer (for the current topic)
        final Map<String, Integer> consumerTotalPartitions = new HashMap<>(consumers.size());
        for (String memberId : consumers) {
            consumerTotalPartitions.put(memberId, 0);}
        // Assign partitions in descending order of lag, then ascending by partition
        partitionLags.sort((p1, p2) -> {
            // If lag is equal, lowest partition id first
            if (p1.getLag() == p2.getLag()) {
                return Integer.compare(p1.getPartition(), p2.getPartition());
            }
            // Highest lag first
            return Long.compare(p2.getLag(), p1.getLag());
        });

        for (TopicPartitionLag partition : partitionLags) {
            // Assign to the consumer with least number of partitions, then smallest total lag, then smallest id
            // returns the consumer with lowest assigned partitions, if all assigned partitions equal returns the min total lag
            final String memberId = Collections
                    .min(consumerTotalLags.entrySet(), (c1, c2) -> {
                        // Lowest partition count first
                        final int comparePartitionCount = Integer.compare(consumerTotalPartitions.get(c1.getKey()),
                                consumerTotalPartitions.get(c2.getKey()));
                        if (comparePartitionCount != 0) {
                            return comparePartitionCount;}
                        // If partition count is equal, lowest total lag first
                        final int compareTotalLags = Long.compare(c1.getValue(), c2.getValue());
                        if (compareTotalLags != 0) {
                            return compareTotalLags;
                        }
                        // If total lag is equal, lowest consumer id first
                        return c1.getKey().compareTo(c2.getKey());
                    }).getKey();

            TopicPartition p =  new TopicPartition(partition.getTopic(), partition.getPartition());
            assignment.get(memberId).add(p);
            consumerTotalLags.put(memberId, consumerTotalLags.getOrDefault(memberId, 0L) + partition.getLag());
            consumerTotalPartitions.put(memberId, consumerTotalPartitions.getOrDefault(memberId, 0) + 1);
            LOGGER.info(
                    "Assigned partition {}-{} to consumer {}.  partition_lag={}, consumer_current_total_lag={}",
                    partition.getTopic(),
                    partition.getPartition(),
                    memberId,
                    partition.getLag(),
                    consumerTotalLags.get(memberId));
        }
    }






    private Map<String, List<TopicPartitionLag>> readTopicPartitionLags(
            final Cluster metadata,
            final Set<String> allSubscribedTopics
    ) {
        // metadataConsumer.enforceRebalance();
        final Map<String, List<TopicPartitionLag>> topicPartitionLags = new HashMap<>();
        for (String topic : allSubscribedTopics) {

            final List<PartitionInfo> topicPartitionInfo = metadata.partitionsForTopic(topic);
            if (topicPartitionInfo != null && !topicPartitionInfo.isEmpty()) {

                final List<TopicPartition> topicPartitions = topicPartitionInfo.stream().map(
                        (PartitionInfo p) -> new TopicPartition(p.topic(), p.partition())
                ).collect(Collectors.toList());

                topicPartitionLags.put(topic, new ArrayList<>());

                // Get begin/end offset in each partition
                final Map<TopicPartition, Long> topicBeginOffsets = metadataConsumer.beginningOffsets(topicPartitions);
                final Map<TopicPartition, Long> topicEndOffsets = metadataConsumer.endOffsets(topicPartitions);
                //get last committed offset
                Map<TopicPartition, OffsetAndMetadata> partitionMetadata =
                        metadataConsumer.committed(new HashSet<>(topicPartitions));
                // Determine lag for each partition
                for (TopicPartition partition : topicPartitions) {

                    final String autoOffsetResetMode = consumerGroupProps
                            .getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
                    final long lag = computePartitionLag(
                            partitionMetadata.get(partition),
                            topicBeginOffsets.getOrDefault(partition, 0L),
                            topicEndOffsets.getOrDefault(partition, 0L),
                            autoOffsetResetMode
                    );
                    topicPartitionLags.get(topic).add(new TopicPartitionLag(topic, partition.partition(), lag));
                }
            } else {
                LOGGER.info("Skipping assignment for topic {} since no metadata is available", topic);
            }
        }
        return topicPartitionLags;
    }



    static long computePartitionLag(
            final OffsetAndMetadata partitionMetadata,
            final long beginOffset,
            final long endOffset,
            final String autoOffsetResetMode
    ) {
        final long nextOffset;
        if (partitionMetadata != null) {

            nextOffset = partitionMetadata.offset();

        } else {

            // No committed offset for this partition, set based on auto.offset.reset
            if (autoOffsetResetMode.equalsIgnoreCase("latest")) {
                nextOffset = endOffset;
            } else {
                // assume earliest
                nextOffset = beginOffset;
            }
        }
        // The max() protects against the unlikely case when reading the partition end offset fails
        // but reading the last committed offsets succeeds
        return Long.max(endOffset - nextOffset, 0L);
    }

    private static Map<String, List<String>> consumersPerTopic(Map<String, List<String>> subscriptions) {

        final Map<String, List<String>> consumersPerTopic = new HashMap<>();
        for (Map.Entry<String, List<String>> subscriptionEntry : subscriptions.entrySet()) {

            final String consumerId = subscriptionEntry.getKey();
            for (String topic : subscriptionEntry.getValue()) {

                List<String> topicConsumers = consumersPerTopic.computeIfAbsent(topic, k -> new ArrayList<>());
                topicConsumers.add(consumerId);
            }
        }
        return consumersPerTopic;
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // Construct Properties from config map
        consumerGroupProps = new Properties();
        for (final Map.Entry<String, ?> prop : configs.entrySet()) {
            consumerGroupProps.put(prop.getKey(), prop.getValue());
        }

        // group.id must be defined
        final String groupId = consumerGroupProps.getProperty(ConsumerConfig.GROUP_ID_CONFIG);
        if (groupId == null) {
            throw new IllegalArgumentException(
                    ConsumerConfig.GROUP_ID_CONFIG + " cannot be null when using "
                            + ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG + "="
                            + this.getClass().getName());
        }

        // Create a new consumer that can be used to get lag metadata for the consumer group
        metadataConsumerProps = new Properties();
        metadataConsumerProps.putAll(consumerGroupProps);
        metadataConsumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        final String clientId = groupId + ".assignor";
        metadataConsumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);

        LOGGER.info(
                "Configured LagBasedPartitionAssignor with values:\n"
                        + "\tgroup.id = {}\n"
                        + "\tclient.id = {}\n",
                groupId,
                clientId
        );

        LOGGER.info("creating the metadataconsumer inside the configure");
    }


    static class TopicPartitionLag {

        private final String topic;
        private final int partition;
        private final long lag;

        TopicPartitionLag(String topic, int partition, long lag) {
            this.topic = topic;
            this.partition = partition;
            this.lag = lag;
        }

        String getTopic() {
            return topic;
        }

        int getPartition() {
            return partition;
        }

        long getLag() {
            return lag;
        }

    }






}






