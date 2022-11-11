# cp4waiops-grpc-github-client
GitHub Connector for Watson AIOps gRPC clients

## Server Setup

Create a connector schema for the template connector and then a configuration.
```sh
oc create -f misc/connector-template-schema.yaml
oc create -f misc/example-template-configuration.yaml
```

Retrieve the connector id:
```sh
CONNECTORID=$(oc get connectorconfiguration example-template-connector -o jsonpath={.metadata.uid})
echo $CONNECTORID
```

Retrieve the client id and secret associated with the configuration or construct one using
the example in `misc/example-client-registration.json`.

## Docker Container

First obtain the desired version of the sdk (make sure that `connector/pom.xml` uses the same version!):

To build the docker image, navigate to this folder in a terminal and execute:
```sh
docker build -f container/Dockerfile -t connector-template .
```

To run the docker image, copy the server certs to a folder, gather the connector info, and run it like so:
```sh
docker run \
  -e grpc-bridge.host=connector-bridge-aiopsedge.apps.victor.cp.fyre.ibm.com \
  -e grpc-bridge.port=443 \
  -e grpc-bridge.server-certificate-file=/service-bindings/grpc-bridge/ca.crt \
  -e grpc-bridge.client-certificate-file=/service-bindings/grpc-bridge/tls.crt \
  -e grpc-bridge.client-private-key-file=/service-bindings/grpc-bridge/tls.key \
  -e connector-template.id="011eec67-54a3-4909-8050-16e89dc91eb7" \
  -e connector-template.client-id="example-template-connector-1938af98" \
  -e connector-template.client-secret="cryptographically-secure-value" \
  --mount type=bind,source=/path/to/certs,destination=/service-bindings/grpc-bridge -it connector-template
```

To see the cloud events being produced and sent, modify the container/server.xml file to enable tracing.

## Development

To run the liberty server outside of a docker container, the `mvn liberty:run` command can be ran. Be sure to
install the sdk first as described in the docker container section.

In `/open-liberty/connector/src/main/liberty/config/bootstrap.properties` change the properties required for local development.

Example:
```
grpc-bridge.host=connector-bridge-aiopsedge.apps.victor.cp.fyre.ibm.com
grpc-bridge.port=443
grpc-bridge.server-certificate-file="/path/to/certs/ca.crt"
grpc-bridge.client-certificate-file="/path/to/certs/tls.crt"
grpc-bridge.client-private-key-file="/path/to/certs/tls.key"
connector-template.id="011eec67-54a3-4909-8050-16e89dc91eb7"

com.ibm.ws.logging.console.format=simple#json
com.ibm.ws.logging.console.source=message,trace
com.ibm.ws.logging.console.log.level=info
com.ibm.ws.logging.trace.file.name=stdout
com.ibm.ws.logging.trace.format=BASIC
com.ibm.ws.logging.trace.specification="*=warning:com.ibm.aiops.connectors.*=all"

```

## Testing
Testing code is in `/open-liberty/connector/src/test`

You can run tests via `mvn test`

This will run tests in the test folder.

In `/open-liberty/connector/target/site`, a JaCoCo code coverage report is generated and can be viewed via `index.html`. 


## Inputs (actions)

A cloud event of type `com.ibm.watson.aiops.connectors.template.test-requested` with an address to test.

Example:
```yaml
{
    "specversion": "1.0",
    "id": "66451fc8-41c8-4fb2-a92a-ffa7ad523238",
    "source": "template.connectors.aiops.watson.ibm.com/connectortemplate",
    "type": "com.ibm.watson.aiops.connectors.template.test-requested",
    "time": "2021-11-02T04:49:14.5608556Z",
    "address": "https://www.ibm.com",
    "partitionkey": "partition-0",
    "componentname": "connector",
    "topic": "cp4waiops-cartridge.test-actions-1",
    "connectionid": "f5aa7fa9-92eb-4bec-942c-37eb3e3e9601",
}
```

## Outputs

A cloud event of type `com.ibm.watson.aiops.connectors.template.test-completed` or
`com.ibm.watson.aiops.connectors.template.test-failed` depending on whether or not a test succeeds.

Example:
```yaml
{
    "specversion": "1.0",
    "id": "5d2cfafa-76a7-4de2-8471-fb62878dc8f3",
    "source": "template.connectors.aiops.watson.ibm.com/connectortemplate",
    "type": "com.ibm.watson.aiops.connectors.template.test-completed",
    "time": "2021-11-02T04:53:36.543344Z",
    "connectionid": "f5aa7fa9-92eb-4bec-942c-37eb3e3e9601",
    "componentname": "connector",
    "topic": "cp4waiops-cartridge.test-output-1",
    "partitionkey": "https://www.ibm.com",
    "address": "https://www.ibm.com",
    "responsetime": "81",
    "datacontenttype": "text/plain",
    "data": "200"
}
```