
### Connector UI schema details
- The name property at the top level of the schema needs to match the type in the UI schema AND the extension_name in the ConfigMap. The displayName in the UI schema should match the display_name in the ConfigMap as well.
- You need to create a ConfigMap such as the one below. This should be done for each new connector.
- The schema property in the ConnectorSchema, if not configured correctly, may block your payload. It can be left as as {} to validate the integration in first pass.Based on payload details(to be added) write the schema property using JSON schema.
- You need to have the deploymentType field in the UI schema or the backend will not accept the payload (see example below)
UI schema deploymentType (required and can be used for any form in the same format)

```
- id: orchestration
    element: input
    type: radio
    label: "{{connector.common.form.orchestration.label}}"
    items: 
      - "{{connection_form.dynamic_item.local}}"
      - "{{connection_form.dynamic_item.remote}}"
    itemKeys: ["local", "microedge"]
    apiMapping: connection_config.deploymentType
    formStep: addConnection
ConfigMap example for a new connector
kind: ConfigMap
apiVersion: v1
metadata:
  name: extendedtype-extensions
  namespace: cp4waiops
  uid: 9365fff0-3540-480a-a859-2da5c90cbd39
  resourceVersion: '276964163'
  creationTimestamp: '2022-04-05T13:26:09Z'
  labels:
    icpdata_addon: 'true'
  managedFields:
    - manager: Mozilla
      operation: Update
      apiVersion: v1
      time: '2022-04-05T13:26:09Z'
      fieldsType: FieldsV1
      fieldsV1:
        'f:data':
          .: {}
          'f:extensions': {}
        'f:metadata':
          'f:labels':
            .: {}
            'f:icpdata_addon': {}
data:
  extensions: |-
    [
      {
       "extension_point_id": "zen_external_svc_conn",
       "extension_name": "nagios",
       "display_name": "Nagios",
       "meta": {},
       "details": {}
      }
    ]
```
