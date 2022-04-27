## CICD and devops process for connector development


### Connector composition
Develop your connector with the below components
- A connector container (gRPC client, running either in-cluster or remotely)
- Processor containers(0..* ) (backend processors running in-cluster)
- A UI tile (ConnectorSchema YAML)
- Kafka topics (0..* YAMLs)


### Build container image
- Create a Dockerfile
- Set the desired environment variables
- Set the parent image in dockerfile for building the container image.
- With multi-stage builds, you use multiple FROM statements in your Dockerfile. Each FROM instruction can use a different base, and each of them begins a new stage of the build. You can selectively copy artifacts from one stage to another, leaving behind everything you don't want in the final image.
- Example : https://github.com/IBM/cp4waiops-connectors-java-template/blob/main/container/Dockerfile
- Build the docker image manually as below or configure travis job to build the image evrytime change gets merged to main or release branches
```
docker build -f ConnectorDockerfile -t connector-template .

```
#### Connector templates based on grpc-java-sdk
Release versions of the sdk can be found [here](https://github.ibm.com/quicksilver/grpc-java-sdk/releases). Make sure
that `connector/pom.xml` uses the correct version. For example, `v1.0.1` maps to `1.0.1` in the `pom.xml` file.

To upgrade to another version of the SDK, modify the value of `CONNECTOR_SDK_VERSION` in the `Makefile`.

1. Set the environment variable GITHUB_TOKEN to a github api key with full repo access.
2. Run `make download-connector-sdk` from the `open-liberty` folder.
- Example template :https://github.com/IBM/cp4waiops-connectors-java-template

### CICD automation
- Configure travis or jenkins job for the connector as part of CICD process to automatically build and push the connector image to artifactory location.
- The image can be used for deploying on cp4waiops cluster by creating BundleManifest artifacts.

### k8s deployment files in bundle manifest format
- Create the prerequisites artifacts :
   - Connectorschema : For creating a connector for a data source that is not yet supported in Watson AIOps you first need to create a ConnectorSchema. ConnectorSchema CR provides content for the UI to render the corresponding form in the Data and tool integrations section of the product and it validates completed UI forms. Refer the example in ui-schema details section.
   - Microedgeconfiguration CR: If the connector supports remote orchestration create the microedgeconfiguration CR in order for the bootstrap script to provide the container image location to download and run on a remote VM.
- Create the standard k8s deployment artifacts : Deployment, service, serviceaccount etc.
- Refer example BundleManifest template with folder structre and files : https://github.com/IBM/cp4waiops-connectors-java-template/tree/main/bundle-artifacts
