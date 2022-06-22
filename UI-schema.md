# Introduction
The UI schema in Watson AIOps allows you to dynamically generate UI forms for your connector. We offer a variety of different fields, data types, and validation mechanisms that will enable for your connector to be accessible from the Watson AIOps UI. This UI schema is defined for all connectors under the `uiSchema` in the ConnectorSchema custom object. The best reference for the UI schema is by looking at already existing ConnectorSchema objects and reviewing their `uiSchema` configuration. Note, that this document is the official documentation for this schema, which is a proprietary IBM schema with a custom form generator created for IBM Carbon.

## Design
To begin, please note that the Watson AIOps team **does not** create or maintain custom field designs. All designs are standardized across IBM through the [Carbon Design System Components](https://www.carbondesignsystem.com/components/overview/). It is considered good practice that generally very few tweaks are made to the styling or functionality of the Carbon components.

## Form Structure

```
type: example-logs-connection
displayName: "Example Logs"
categories: 
  - logs
url: https://yourwebsite.com/example-logs-connection
apiAdaptor: connection
datasourceType: logs
sidePanelTitle: "Configuring an Example Logs connection"
sidePanelDescription: "An Example Logs connection provides logs from examples that you provide Watson AIOps. We then help monitor your examples to detect anomalies."
sidePanelInfoHeader: "You'll need to provide the following information:"
sidePanelInfo:
  - "If you have any restrictive EgressNetworkPolicies in place, please ensure that they are updated to allow for this outbound connection."
  - "Type of connection"
  - "Service URL"
  - "REST API method"
  - "User ID and password credentials, API key, or token type, depending on the authentication method you choose"
  - "Certificate used to verify the SSL/TLS connection to the REST service (optional)"
hasOptionalConfig: true
sidePanelOptionalConfigHeader: "You can also optionally configure settings for performance by providing:"
sidePanelOptionalConfigList:
  - "A sampling interval for pulling data from custom sources"
  - "A base parallelism value to specify the number of threads that can run in parallel"
  - "Field mapping information to customize timestamp settings"
hasOptionalText: true
sidePanelOptionalText: "You can specify what kind of data you want to collect through a Kafka connection, either event or log data. You can then provide field mapping information for the specified log type to improve search performance."
hasAIModelType: true
AIModelTypeList:
  - "Log anomaly detection"
formSteps:
  - step: 
      id: addConnection
      name: "Add connection"
  - step:
      id: fieldMapping
      name: "Field mapping"
      isOptional: true
  - step:
      id: aiTraining
      name: "AI training and log data"
      isOptional: true
```

### Required top-level form attributes (excluding the side panel configuration)

The attributes below are at the top level of the uiSchema object and are required. The UI form may show a blank page for your connector if any of these properties are not provided or are configured incorrectly. Please closely follow the guidance below.

**type** is the unique key used to identify the form. This is used for all routing from the UI to the connector. Use only lowercase letters or dashes "-" for defining the type.

**displayName** is the name that shows up at the top of the form and on the connector tile.

**categories** contains all the category filters used to organize and sort the connectors in the UI. The categories that are currently allowed in the UI are "ChatOps", "CI/CD", "Events", "Logs", "Remote access", "Runbooks", "Tickets", "Metrics", and "Topology".

**url** is the link to the documentation for each connector and is used for "Learn more" and documentation links in the UI.

**apiAdaptor** is a property used to define which APIs should be called by the UI. The value should always be set to `connection` for custom connectors. The other API adaptors are for IBM internal APIs used for observers and runbooks.

**datasourceType** is the type of the data source we are interested in. Note, that the data sources that are currently supported by the connectors are "runbooks", "metrics", "logs", "events", "topology", "alerts", and "tickets". Most connections can use the "logs" type. Note, that we also have specific data types for "slack" and "teams" to meet the needs of these specific ChatOps connectors and their data, but they should not be used.

**formSteps** are used to define the pages in the form. Note that you must have at least one form step. Each form step must be defined based on the example above and must have a unique "id" property and any string for the "name" property. The "id" is used to define the form in the schema and the "name" is shown within the UI form itself. Note that the isOptional parameter cannot be applied to the first form step, but can be used on any other steps if all fields on that page are optional. The isOptional parameter is meant to improve the user experience and does not actually affect the form itself besides saying that page is optional.

**form** is where you will put all the code for the fields (more on this in the "Field Components" section below).

### Side panel form attributes

The side panel is an important part of the user experience in the data and tools connections page. This side panel is meant to show explain to the user what is required for them to configure their connection so they can pull their data in to Watson AIOps. Please note the parameters below are required unless otherwise stated and will be at the top level of the form like the attributes above.

**sidePanelTitle** is the title at the top of the side panel. This is usually in the format "Configuring a <your name> connection".

**sidePanelDescription** is the description at the top of the side panel. This should explain what kind of data you are bringing in to Watson AIOps and how it is being used.

**sidePanelInfoHeader** is used as the header for the sidePanelInfo. Almost all informational headers say "You'll need to provide the following information:".

**sidePanelInfo** is a YAML/JSON list where you state what fields are required to configure your connection in the UI. Note, that only required fields should be listed here and that optional fields are detailed elsewhere in the side panel.

**hasOptionalConfig** is a boolean value that can be defined as true or false. If this is set to true, you must also define the sidePanelOptionalConfigHeader and sidePanelOptionalConfigList properties. This property is used to enable or disable in the side panel where you want to state what optional parameters are required to configure your connection in the form.


**sidePanelOptionalConfigHeader** is optional, unless hasOptionalConfig is set to true. The header is used as a header for providing optional configuration in the form. Almost all optional configuration headers say "You can also optionally configure settings for performance by providing:".

**sidePanelOptionalConfigList** is a YAML/JSON list where you state what fields are optional to configure your connection in the UI. Note that this is optional, unless hasOptionalConfig is set as true. Only optional fields should be listed here.

**hasOptionalText** is a boolean value that can be defined as true or false. If this is set to true, you must also define sidePanelOptionalText.

**sidePanelOptionalText** is optional unless hasOptionalText is set to true. This text should be used for any information that you would like to share with the user about your connection or form that is not avaliable in other places in the UI.

**hasAIModelType** is a boolean value that can be defined as true or false. If this is set to true, you must also define AIModelTypeList. Setting this to true means that your connection will be used to train AI models.

**AIModelTypeList** is optional unless hasAIModelType is set to true. This is a YAML/JSON list where you can state what AI model types can be used for your connector.


## Field Components

The data and tools connections page offers many different fields that can be configured under the "form" property. Please read below about the common properties shared between each field schema and note examples of each component below.

**id** is required for every component and must be alphanumeric and unique to each component. The value of id is a string.

**element** is required for every component and is a string. For all fields below, the value for "element" will be "input".

**type** is required for every component and is a string. This is used to determine which component you want to use.

**isRequired** is configurable for every field, but is optional. The value of this is a boolean and can be true or false. If the value is set to true, the user will not be able to proceed to submitting the form or going to the next form page unless that field is filled out. An error will also be displayed if the field is left empty.

**apiMapping** is a string and defines the path to the data which is sent to the API. For example, if you define the apiMapping as "connector_config.primary.username" we will create a parent object connector_config with a child object primary. Inside primary, there will be a key of username with a value of the field that was inputted. This will be sent to the API and routed to the connector so the data from the UI can be parsed.

### Text
<img src="https://media.github.ibm.com/user/164457/files/a994bd80-8d8b-11eb-9b5d-70345d5652eb" width="720">

```
- id: name
  element: input
  type: text
  label: "Name"
  placeholder: "Enter a name that uniquely identifies the integration."
  apiMapping: connection_config.display_name
  isRequired: true
```

### Text Area
<img src="https://media.github.ibm.com/user/164457/files/aa2d5400-8d8b-11eb-99ea-2ed51ffec15e" width="720">

```
- id: description
  element: input
  type: textarea
  label: "Description (optional)"
  placeholder: "Enter a brief description of the integration."
  apiMapping: connection_config.description
```

### Password
<img src="https://media.github.ibm.com/user/164457/files/aa2d5400-8d8b-11eb-9c7d-9de44b442c1a" width="720">

```
- id: password
  element: input
  type: password
  label: "Password"
  placeholder: "Enter a password for the integration."
  apiMapping: connection_config.password
  isRequired: true
```

### Dropdown
<img src="https://media.github.ibm.com/user/164457/files/aa2d5400-8d8b-11eb-974f-b80dae365c80" width="720">

```
- id: type
  element: input
  type: dropdown
  label: "Type"
  items: ["restapi", events", "messages", "integrations"]
  helperText: "Incoming data must be in JSON format."
  apiMapping: connection_config.type
```

Additionally, you can optionally configure the "isRange" property. The value for this can be a boolean and set to true or false. If this is configured, "items" can be passed a range of numbers. For example, if you set "items" as "1:50" the dropdown will display all values from 1 to 50 inclusive.

### JSON
<img src="https://media.github.ibm.com/user/164457/files/05f7dd00-8d8c-11eb-9f10-727a5f8f7602" width="720">

```
- id: postJSON
  element: input
  type: json
  label: "Mapping"
  apiMapping: connection_config.body
  defaultValue: '{"rolling_time": 10,"instance_id_field": "kubernetes.container_name","log_entity_types": "kubernetes.namespace_name,kubernetes.host,kubernetes.container_name","message_field": "@rawstring","standard_entity_types": {"pod_name":"pod","node_name":"node"},"timestamp_settings": {"timestamp_field": "_ts","pattern": "yyyy-MM-dd hh:mm:ss z"}, "codec": "custom"}'
```

### Toggle
<img src="https://media.github.ibm.com/user/164457/files/05f7dd00-8d8c-11eb-998b-3bdd61e5d458" width="720">

```
- id: data_flow
  element: input
  type: toggle
  defaultToggled: false
  label: "Data flow"
  labelOff: "Off"
  labelOn: "On"
  apiMapping: connection_config.data_flow
```

### File
<img src="https://media.github.ibm.com/user/164457/files/73583d80-8d8d-11eb-90b5-7ebdf38b62cc" width="480">

```
- id: entity_extraction
  element: input
  type: file
  showInEdit: false
  label: "{{connector.pagerduty.form.entity_extraction.label}}"
  apiMapping: entity_extraction
```

### Radio
<img src="https://media.github.ibm.com/user/164457/files/73f0d400-8d8d-11eb-850b-8f9a3a7433b4" width="480">

```
- id: collection_mode
  element: input
  type: radio
  label: "Mode"
  items: ["Live data for continuous AI training and anomaly detection", "Live data for initial AI training"]
  itemKeys: ["inference", "live"]
  apiMapping: connection_config.collection_mode
```

### Number
<img src="https://media.github.ibm.com/user/164457/files/1c069d00-8d8e-11eb-9280-5e184144b48a" width="360">

```
- id: kafka_partitions
  element: input
  type: number
  label: "Kafka partitions (1-500)"
  min: 1
  max: 500
  step: 1
  defaultValue: 1
  helperText: "Specify the number of kafka partitions. The default value is one."
  apiMapping: connection_config.kafka_partitions
```

### Date Picker (Single)
<img src="https://media.github.ibm.com/user/164457/files/72bfa700-8d8d-11eb-8760-583930478423" width="360">

```
- id: start_date
  element: input
  type: date
  label: "Start date"
  placeholder: "mm/dd/yyyy"
  apiMapping: connection_config.schedule.start_date
```

### Date Picker (Range)
<img src="https://media.github.ibm.com/user/164457/files/88819c00-8d8e-11eb-8ce5-b328b7384d30" width="360">

```
- id: date_range
  element: input
  type: dateRange
  startDateLabel: "Date picker label"
  endDateLabel: "Date picker label"
  startDatePlaceholder: "mm/dd/yyyy"
  endDatePlaceholder: "mm/dd/yyyy"
  apiMappingStart: connection_config.start
  apiMappingEnd: connection_config.end
```

### Time Picker
<img src="https://media.github.ibm.com/user/164457/files/95b82e80-e5c0-11ec-98ab-722a1c784138" width="360">

```
- id: start_time
  element: input
  type: time
  label: "Time"
  apiMappingTime: connection_config.observer_parameters.schedule.start_time
  apiMappingSuffix: connection_config.observer_parameters.schedule.am_pm
```

### Hidden Text
This element does not show up for the user it but it allows us to insert additional information in to an API request if it is required.

```
- id: mapping
  element: input
  type: hiddenText
  defaultValue: '{"codec": "null"}'
  apiMapping: mapping
```
