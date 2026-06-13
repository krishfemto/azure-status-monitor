package com.krishna.functions;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs on a schedule (every 5 minutes) and checks if a list of websites
 * are reachable. Each result is saved as a row in Azure Table Storage.
 *
 * The cron expression "0 *\/5 * * * *" means:
 * second=0, minute=every 5, hour=any, day=any, month=any, weekday=any
 */
public class StatusCheckFunction {

    // Connection string is read from Function App settings (set later)
    private static final String CONNECTION_STRING =
            System.getenv("AzureWebJobsStorage");

    private static final String TABLE_NAME = "statuschecks";

    // Services to check - name -> URL
    private static final Map<String, String> SERVICES = new LinkedHashMap<>();
    static {
        SERVICES.put("AWS Project", "https://resume-visitor-website.s3.us-east-1.amazonaws.com/index.html");
        SERVICES.put("Google", "https://www.google.com");
        SERVICES.put("MyPortfolio", "https://krishfemto.github.io/portfolio");
    }

    @FunctionName("StatusCheck")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "0 */5 * * * *") String timerInfo,
            final ExecutionContext context) {

        context.getLogger().info("StatusCheck function triggered at " + Instant.now());

        TableClient tableClient = new TableClientBuilder()
                .connectionString(CONNECTION_STRING)
                .tableName(TABLE_NAME)
                .buildClient();

        for (Map.Entry<String, String> service : SERVICES.entrySet()) {
            String name = service.getKey();
            String url = service.getValue();
            boolean isUp = checkUrl(url);
            long responseTime = -1;

            long start = System.currentTimeMillis();
            isUp = checkUrl(url);
            responseTime = System.currentTimeMillis() - start;

            // PartitionKey groups rows by service name.
            // RowKey is unique per check (timestamp, reversed so newest sorts first).
            String rowKey = String.valueOf(Long.MAX_VALUE - Instant.now().toEpochMilli());

            TableEntity entity = new TableEntity(name, rowKey)
                    .addProperty("ServiceName", name)
                    .addProperty("Url", url)
                    .addProperty("Status", isUp ? "up" : "down")
                    .addProperty("ResponseTimeMs", responseTime)
                    .addProperty("CheckedAt", Instant.now().toString());

            tableClient.createEntity(entity);

            context.getLogger().info(
                String.format("Checked %s -> %s (%dms)", name, isUp ? "UP" : "DOWN", responseTime)
            );
        }
    }

    /**
     * Makes an HTTP HEAD request and returns true if the response code is < 400.
     */
    private boolean checkUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode < 400;
        } catch (Exception e) {
            return false;
        }
    }
}