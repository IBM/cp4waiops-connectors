# Connector Security And Permissions

## Authentication
CP4WAIOps connectors must authenticate with the CP4WAIOPs grpc server (aka connector-bridge) using two concurrent
mechanisms. The first is a client TLS certificate that is shared by all connectors. The second is an OIDC Client-ID and
Client-Secret that is unique per connection instance. The Connector-SDK will automatically bootstrap these credentials 
and the Client-ID and Client-Secret are included automatically within the metadata of each individual RPC call in this 
format: `Authentication: base64(Client-ID:ClientSecret)`. This unique set of credentials grants the connector 
permission to perform a limited set of actions based on what permissions are configured in the connector's 
ConnectorSchema.

## Permissions
The permissions granted to a connector are defined in the `.spec.permissions` section of the ConnectorSchema.

```yaml
permissions:
  openAPIServices:
    - name: my-server-side-service
  channels:
    - name: cp4waiops-cartridge.my-actions
      operations:
        - read
    - name: cp4waiops-cartridge.my-data-ingest
      operations:
        - write
```

### Channels
The eventing channels that a connector can read from or write to are restricted based on the 
`.spec.permissions.channels` section of the ConnectorSchema. Any channels that the connector needs to access must 
be listed along with a list of operations that need to be permitted (can be "read" and/or "write"). Connectors are 
further restricted in their interactions on a _per instance_ basis such that, for a given Client-ID and Client-Secret,
the connector is only allowed to write an event to a channel if it includes the 
[CloudEvent extension attribute](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/primer.md#cloudevent-attribute-extensions)
`connectionid` set to the unique ID assigned to the connection-instance/credential, which should also match the 
Client-ID. The Connector-SDK will add this attribute to outbound messages for you automatically. Similarly, when 
reading from a channel, only events with the `connectionid` attribute set to match the UID of the 
connection-instance/credential will be returned to the connector.

### OpenAPI Services
In some cases, connectors may need to interact with 
[CloudEvent compatible](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/bindings/http-protocol-binding.md)
HTTP services running on the CP4WAIOps server. Services that the connector requires access to must be listed under the 
`.spec.permissions.openAPIServices` section of the ConnectorSchema. The provided service name must match the name of 
the Kubernetes service that the connector needs access to. The service may also further authenticate the connector and
its permissions via use of additional headers that will automatically be added to the request by the connector-bridge
server. These headers are `Client-ID`, `Client-Secret`, and `Authentication` which will include an OIDC Bearer token 
generated automatically using the connector's Client-ID and Client-Secret.