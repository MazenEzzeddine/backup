



public class Queries {

//any prometheus query can be done
//queries can be externalized
    public final static String netRawMessageArrivalRateQuery = "http://prometheus-operated.pwcv4-online-strimzi.svc.cluster.local:9090/api/v1/query?" +
            "query=sum(rate(kafka_topic_partition_current_offset%7Btopic=%22pwc-net-in-030001%22" +
            ",namespace=%22pwcv4-online-strimzi%22%7D%5B1m%5D))%20by%20(topic)";

    public final static String authorModuleArrivalRateQuery = "http://prometheus-operated.pwcv4-online-strimzi.svc.cluster.local:9090/api/v1/query?" +
            "query=sum(rate(kafka_topic_partition_current_offset%7Btopic=%22pwc-authorization-online-message-handler%22" +
            ",namespace=%22pwcv4-online-strimzi%22%7D%5B1m%5D))%20by%20(topic)";


    public final static String authorModuleLagQuery = "http://prometheus-operated.pwcv4-online-strimzi.svc.cluster.local:9090/api/v1/query?" +
            "query=sum(kafka_consumergroup_lag%7Bconsumergroup=%22AuthorizationModule%22," +
            "topic=%22pwc-authorization-online-message-handler%22" +
            ",namespace=%22pwcv4-online-strimzi%22%7D)%20by%20(consumergroup,topic)";
}
