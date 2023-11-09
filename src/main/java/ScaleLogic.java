import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

public class ScaleLogic {

    static Instant lastScaleDecision = Instant.now();
    private static final Logger log = LogManager.getLogger(ScaleLogic.class);


    public static void scaleConsumerGroup() {
        if(LaunchQueries.lag > 1000) {
            try (final KubernetesClient k8s = new KubernetesClientBuilder().build()) {
                k8s.apps().deployments().inNamespace("pwcv4-online-strimzi")
                        .withName("pwc-switch-authorization-module")
                        .scale(2);
                log.info("Upscaling {} there shall be {} replicas", "AuthorizationModule", 2);
            }catch(Exception e) {
                e.printStackTrace();
            }
            lastScaleDecision = Instant.now();
        }
    }
}
