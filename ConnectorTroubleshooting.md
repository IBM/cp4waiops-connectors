# Connector Troubleshooting

Connectors operate asynchronously, sometimes from a remote location. This guide provides help for determining the
cause of common problems and solutions.

## Logging

Logging levels for OpenLiberty containers can be modified by following the OpenLiberty
[documentation](https://openliberty.io/docs/22.0.0.3/log-trace-configuration.html#configuaration).
In practice, this generally means either modifying the `/config/server.xml` file directly, or the creating the
`/config/configDropins/overrides/server.xml` override file with the desired log level. For example:
```xml
<server>
    <logging consoleFormat="simple"
        consoleSource="message,trace"
        consoleLogLevel="info"
        traceFileName="stdout"
        traceFormat="BASIC"
        traceSpecification="com.ibm.aiops.connectors.*=all" />
</server>
```

## Deployment Errors

### BundleManifest is in a Retrying State

When there is a problem found retrieving or using the bundle referenced by the BundleManifest resource, the status
will show that it is either in an errored or retrying state.

If the RepositoryReady status condition on the BundleManifest resource is failing, this would indicate that the
repository could not be pulled. Either the repository does not exist, or it cannot be accessed due to a network or authentication error. Refer to the BundleManifest documentation to address this.

An example error if the `authSecret` was not set to a valid secret:
```
Message: `failed to pull latest deployment manifests from repo, if previously deployed older versions of the deployment manifests may still be deployed and functional, check .status.resources for more details`
```

If the DeployablesReady condition is failing, then the resources are failing to deploy. Check the Kubernetes events for
the BundleManifest and GitApp resources. These can be observed on the OpenShift events page, or by querying the
Kubernetes api: `oc get events`. These events will contain more detailed information that can be used to determine the
problem.

### GitApp Event Contains: Attempted to Overwrite Resource

This event indicates that the GitApp failed to install or update because it conflicts with the resources installed
by another GitApp. For the two to be compatible, the conflicting resource will need to be renamed in one of the
bundles.

## Communication Errors Between the Connector and the CP4WAIOps Server

The following sections address communication errors that users may run into.

### TLS Errors

The following exception indicates that the connector was unable to validate the server certificate.
```
[4/7/22, 15:34:57:716 UTC] 0000003c StandardConne W   configuration stream terminated with an error
                                 io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
Channel Pipeline: [SslHandler#0, ProtocolNegotiators$ClientTlsHandler#0, WriteBufferingAndExceptionHandler#0, DefaultChannelPipeline$TailContext#0]
    ...
Caused by: javax.net.ssl.SSLHandshakeException: PKIX path validation failed: java.security.cert.CertPathValidatorException: signature check failed
    ...
Caused by: sun.security.validator.ValidatorException: PKIX path validation failed: java.security.cert.CertPathValidatorException: signature check failed
    ...
Caused by: java.security.cert.CertPathValidatorException: signature check failed
    ...
Caused by: java.security.SignatureException: Signature does not match.
    ...
```

This can happen if the server certificate is refreshed and the connector still trusts the old certificate. If deployed
using the microedge script, then redownload the script and execute it to update the certificates. If deployed on the
server, and the connector does not automatically detect updates to certificates, the pod may need to be restarted.

If still seeing the error, verify that the `tls.crt` entry in the `connector-bridge-connection-info` secret on
the openshift cluster running the server matches the certificate being used by the connector. If it does, then the
connector is likely the victim of a man in the middle attack as someone is attempting to intercept the requests between
the server and the connector.

### Authentication Errors

The following exception indicates that the connector failed to authenticate with the server.
```
[4/7/22, 16:47:38:047 UTC] 00000047 StandardConne W   configuration stream terminated with an error
                                 io.grpc.StatusRuntimeException: UNAUTHENTICATED: unable to authenticate client, invalid client_id or client_secret in encoded credentials
    ...
```

This can happen if the connector credentials are revoked. If deployed using the microedge script, then redownload the
script and execute it to update the credentials. If deployed on the server, then the credentials will be automatically
recreated once the problem is detected.

### Connection Frequently Dies

The following exception indicates that communication between the connector and server was terminated unexpectedly.
```
[4/7/22, 17:10:59:264 UTC] 0000004b StandardConne W   configuration stream terminated with an error
                                 io.grpc.StatusRuntimeException: UNAVAILABLE: Network closed for unknown reason
    ...
```

If this happens frequently, and both the connector and server are healthy, this could indicate a problem with either
the network or a firewall. For example, the firewall may be setup to terminate connections older than a minute. This
may degrade performance as the connector will need to reconnect to the server and resend unreceived events.

### Connector is Stuck Waiting for Configuration

The following log message (FINE level) indicates that the connector is establishing a configuration stream with the
server:
```
[4/7/22, 17:10:59:264 UTC] 00000042 StandardConne 1 sending configuration: event={...}
```

If the connector never receives configuration from the server, then it will be stuck in a waiting state. This problem
can be seen in development if multiple users are using the same connection. To resolve the problem, each developer
should use their own connection. This can also be seen if the connector component name defined in the ConnectorSchema
does not match the component name being used by the connector. In that case, change the ConnectorSchema and connector
code so that they match.

## Performance Issues

The connector should have a metrics endpoint available at either `/h/metrics` or `/metrics` that can be used to
determine the cause of many performance issues. If deployed on an OpenShift cluster and the connector has a PodMonitor
or ServiceMonitor setup, [user workload](https://docs.openshift.com/container-platform/4.10/monitoring/enabling-monitoring-for-user-defined-projects.html)
monitoring can be enabled to periodically scrape this endpoint. The user can then issue queries through the OpenShift
Monitoring UI. Below are some useful metrics common to many connectors. Connectors may have metrics specific to the
connector as well.

Note:
If metrics are not showing up for a connector, that would indicate that either the connector is not correctly configured to output metrics
or that there is a problem with [user workload monitoring](https://docs.openshift.com/container-platform/4.10/monitoring/enabling-monitoring-for-user-defined-projects.html)

| Metric Name | Description |
| --- | --- |
| connectors_sdk_runthread_starts_total | the number of times the connector run thread has been started |
| connectors_sdk_configurationstream_starts_total | the number of times the configuration stream thread has been started |
| connector_sdk_produce_starts_total | the number of times a produce stream thread has been started |
| connector_sdk_consume_starts_total | the number of times a consume stream thread has been started |
| connectors_sdk_connectorexceptions_thrown_total | the number of exceptions thrown by the connector |
| connectors_sdk_configuration_processtime_seconds_count | the number of times the connector has attempted to configure or reconfigure itself |
| connectors_sdk_configuration_processtime_seconds_sum | aggregate time spent in configuration or reconfiguration |
| connectors_sdk_configuration_processtime_seconds_max | maximum time spent in a single attempt to configure or reconfigure |
| connectors_sdk_action_processtime_seconds_max | maximum time spent processing an individual event received from a consume stream |
| connectors_sdk_action_processtime_seconds_count | number of consume stream messages processed |
| connectors_sdk_action_processtime_seconds_sum | aggregate time spent processing consume stream messages |
| connector_sdk_consume_received_total | number of consume stream messages received |
| connector_sdk_consume_dropped_total | number of invalid consume stream messages dropped |
| connector_sdk_produce_sent_total | number of cloud events sent to the server |
| connector_sdk_produce_verified_total | number of cloud events the server has verified as being received |
| connector_sdk_producer_badevents_total | the number of sent events without a destination that have been dropped |
| connector_sdk_produce_dropped_total | the number of cloud events dropped for being too large, or because the server has rejected them |
| connector_sdk_status_failed_total | the number of times status failed to be sent to the server |
| connector_sdk_status_sent_total | the number of times status was sent to the server |
| connectors_sdk_vault_lookup_duration_seconds_max | maximum time spent performing a vault lookup |
| connectors_sdk_vault_lookup_duration_seconds_count | the number of vault lookups attempted |
| connectors_sdk_vault_lookup_duration_seconds_sum | aggregate time spent performing vault lookups |
| connectors_sdk_vault_lookup_errors_total | the number of errors encountered attempting to lookup a value in vault |
| connectors_sdk_vault_renewal_duration_seconds_max | maximum time spent performing a vault token renewal |
| connectors_sdk_vault_renewal_duration_seconds_count | the number of vault token renewals attempted |
| connectors_sdk_vault_renewal_duration_seconds_sum | aggregate time spent renewing vault tokens |
| connectors_sdk_vault_renewal_errors_total | number of errors encountered attempting to renew vault tokens |

Some example PromQL queries for a connector with id `dabc7a3f-9c44-4505-8890-58907297cd7b`:
```
sum by (channel_name)(irate(connector_sdk_produce_sent_total{connector_id="dabc7a3f-9c44-4505-8890-58907297cd7b"}[1m]) * 60)
```

```
sum by (channel_name)(irate(connector_sdk_produce_verified_total{connector_id="dabc7a3f-9c44-4505-8890-58907297cd7b"}[1m]) * 60)
```

### After a Connection Is Created, No Connection Pod Comes Up
- Your `ConnectorConfiguration` instance may have no status for several minutes
- After 5 minutes the `ConnetorConfiguration` instance shows the error `unable to determine status of Connector component, connection may have been interrupted`
- No connection pod starts up

Check the events via `oc get events` and search for your connector's name. You may see errors like:
`failed to get status of prereqs GitApp`
