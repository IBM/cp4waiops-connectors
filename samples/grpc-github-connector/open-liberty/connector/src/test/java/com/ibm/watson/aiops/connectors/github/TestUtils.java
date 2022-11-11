package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.ibm.cp4waiops.connectors.sdk.Util;

import org.json.JSONException;
import org.json.JSONObject;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class TestUtils {
    public static JSONObject getDataFromCloudEvent(CloudEvent ce) throws JSONException {
        String ceJSONString = Util.convertCloudEventToJSON(ce);
        JSONObject ceJSON = new JSONObject(ceJSONString);
        JSONObject ceDataJSON = ceJSON.getJSONObject("data");

        return ceDataJSON;
    }

    public static String getJSONFromTestData(String filePath) throws IOException {
        String file = "src/test/data/" + filePath;
        String json = new String(Files.readAllBytes(Paths.get(file)));
        return json;
    }

    // The run method in actions runs async, so certain
    // checks will fail without a small delay (for example metrics)
    public static void waitForSimpleRun() throws InterruptedException {
        Thread.sleep(3000);
    }

    public static CloudEvent buildCloudEventWithActionBody(String actionType, String jsonBody) throws URISyntaxException{
        CloudEvent ce = 
        CloudEventBuilder.v1()
        .withId(UUID.randomUUID().toString())
        .withSource(new URI("http://example.com"))
        .withTime(OffsetDateTime.now())
        .withType(actionType)
        .withDataContentType("application/json")
        .withExtension(ConnectorConstants.RESPONSE_TIME_CE_EXT, System.nanoTime())
        .withExtension(Connector.CONNECTION_ID_CE_EXTENSION_NAME, "connectorid")
        .withExtension(Connector.COMPONENT_NAME_CE_EXTENSION_NAME, "connectorname")
        .withExtension(ConnectorConstants.TOOL_TYPE_CE_EXT,ConnectorConstants.TOOL_TYPE_CE_EXT_VALUE)
        .withExtension(ConnectorConstants.CE_EXT_STRUCTURED_CONTENT_MODE, ConnectorConstants.CE_EXT_STRUCTURED_CONTENT_MODE_VALUE)
        .withData(jsonBody.getBytes()).build();

        return ce;
    }
} 