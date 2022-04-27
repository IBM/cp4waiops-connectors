## Steps to integrate and deploy a connector from cp4waiops

## Create the necessery container images, deployment artifacts
- Build the connector container image as per cicd devops process
-  Publish your image to docker registry. The container images from IBM should be in the IBM Cloud Container Registry, while container images from business partners and the user community can come from any registry.
- Formulate the required k8s deployment artifacts for the connector container deployment.
- Every connector will have an equivalent UI tile that produces configuration, which is then passed to the client via the Configuration gRPC method. The data element in the Cloud Event streamed in this method is a JSON serialization of the ConnectorConfiguration.spec that was created from a completed UI tile. Create the UI schema/form as per ui-schema creation guidelines.
- Follow the bundleManifest guidelines from [here](https://github.com/IBM/cp4waiops-connectors/bundle-manifest) to create bundleManifest deployment artifacts.



## Publish the deployment artifacts
- All new connectors in Cloud Pak for Watson AIOps (3.3+) are based on the new gRPC framework and  are installed via a BundleManifest.
 - Publish the bundleManifest deployment artifacts by creating the pull request to **public** GitHub repository located [here](https://github.com/IBM/cp4waiops-connectors).
- All artifacts (BundleManifest, deployment files referencing container images, etc) all perfectly fit a GitOps approach, thus our pipeline can hook right into this repository.
- Each quarterly release of the Cloud Pak for Watson AIOps can pickup relevant contributions from this github repository provided that all the required criterias are met.

## Deployment options and boostrap

There are two ways to deploy a gRPC connector or automation action:

### local-edge:
- In this deployment mode the container is deployed in the same namespace as Watson AIOps, therefore it requires that a set of Kubernetes deployment artifacts (using kustomize) are made available in the BundleManifest (more details in this section). The bootstrap information will be injected to the container via kustomize patches to the deployment artifact.
- Refer the example `java-grpc-connector-template` deployment artifact `/bundle-artifacts/connector/deployment.yaml` for the required files to be volume mounted

### micro-edge:
- In this deployment mode the container is started manually by the user, most of the time in a remote VM that sits in a different network boundary. This mode does not require Kubernetes deployment artifacts, but it does require the user to fetch the necessary bootstrap information from the cp4waiops UI after connection creation is complete with Remote orchestration option.
- Micro-edge configuration files will be present in the container at runtime in following location which is volume mounted by the bootstrap script and need to be watched for updates while the program runs
```
$ ls /bindings/grpc-bridge/
ca.crt  client-id  client-secret  host  id  port  tls.crt  tls.key
```
- In order for the bootstrap script to provide the container image location to download and run on a remote VM, microedgeconfiguration CR needs to added as per the CR specification to bundleManifest artifacts prereqs folder.
