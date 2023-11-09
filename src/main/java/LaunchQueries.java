import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class LaunchQueries {

    private static final Logger log = LogManager.getLogger(LaunchQueries.class);

    static double arrivalRate;
    static double lag;
    static void query() {
        HttpClient client = HttpClient.newHttpClient();
        ////////////////////////////////////////////////////
        List<URI> queries = new ArrayList<>();
        try {

            queries = Arrays.asList(
                    new URI(Queries.netRawMessageArrivalRateQuery),
                    new URI(Queries.authorModuleArrivalRateQuery),
                    new URI(Queries.authorModuleLagQuery)
            );
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        ///////////////////////////////////////////////////
        //launch queries for topic 1 lag and arrival get them from prometheus
        List<CompletableFuture<String>> queryFutures = queries.stream()
                .map(target -> client
                        .sendAsync(
                                HttpRequest.newBuilder(target).GET().build(),
                                HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::body))
                .collect(Collectors.toList());



        int index = 0;
        double result;
        for (CompletableFuture<String> cf : queryFutures) {

            try {
                result = Util.parseJsonArrivalRate(cf.get());
                if (index == 0) {
                    log.info("total arrival from the channel {}", result);
                } else if (index==1) {
                    log.info(" total arrival rate into the author module {}", result);
                    arrivalRate = result;
                } else {
                    log.info(" lag of the author module {}", result);
                    lag=result;
                }
                index++;
            } catch (Exception e) {
                 e.printStackTrace();
            }
        }
    }



}
