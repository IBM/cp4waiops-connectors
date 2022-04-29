# Connector Deployment Using BundleManifests
The BundleManifest Spec includes two sections
to be filled out. These are [prereqs](#Prereqs) and [instanced](#Instanced). Both sections reference a folder within a
branch of a Github repo which should correspond to a Kustomize chart. Your BundleManifest spec should match the one
below, but with the directories replaced with your own directories within this repo containing your deployment
manifests.
```yaml
apiVersion: connectors.aiops.ibm.com/v1beta1
kind: BundleManifest
metadata:
   name: sample
spec:
   prereqs:
      repo: https://github.com/org/repo
      branch: main
      authSecret:
         name: github-secret
      components:
         - name: deployment
           path: /bundle-artifacts/prereqs
           type: kustomize
           kustomization:
              images:
                 - name: ibm-grpc-sample-connector
                   newName: hyc-cp4mcm-team-docker-local.artifactory.swg-devops.com/cp/aiopsedge/java-grpc-connector-template
                   newTag: unreleased-main-latest
   instanced:
      repo: https://github.com/org/repo
      branch: main
      authSecret:
         name: github-secret
      components:
         - name: connector
           path: /bundle-artifacts/connector
           type: kustomize
           kustomization:
              images:
                 - name: ibm-grpc-sample-connector
                   newName: hyc-cp4mcm-team-docker-local.artifactory.swg-devops.com/cp/aiopsedge/java-grpc-connector-template
                   newTag: unreleased-main-latest
```

## Prereqs
The .spec.prereqs section points to a [Kustomize chart](samples/bundle-manifest/prereqs) that contains all the
deployment manifests that need to be created during the installation of CP4WAIOps. This _must_ include the connector's
[ConnectorSchema](samples/bundle-manifest/prereqs/schema.yaml) and _may_ include any other install-time resources
that are needed including [Kafka topics](samples/bundle-manifest/prereqs/topics.yaml) and 
[microedge configuration](samples/bundle-manifest/prereqs/microedgeconfiguration.yaml).

## Instanced
The .spec.instanced section points to a [Kustomize chart](samples/bundle-manifest/connector) that contains all the
deployment manifests that need to be created upon creation of a new connection for the given connector type when
deployed automatically in Kubernetes by the system. A copy of these resources will be created for _every_ connection of
the given connector type. The connection's UID will be applied as a
[name suffix](https://github.com/kubernetes-sigs/kustomize/blob/master/examples/transformerconfigs/README.md#prefixsuffix-transformer)
to all the resources defined in the chart using Kustomize. You will need to rely on this functionality along with the
[name-reference transformer](https://github.com/kubernetes-sigs/kustomize/blob/master/examples/transformerconfigs/README.md#name-reference-transformer)
to automatically bootstrap the orchestrated connector with the information that it needs to connect to the
connector-bridge. For an example of this, you can look at the Sample connector's
[transformer configurations](samples/bundle-manifest/connector/kustomization.yaml#L12).

### Kustomization
In some cases, end-users may wish to create multiple connections of the same type. In order to support this, each
local connection that is created will receive its own unique credential that must be used to authenticate with the gRPC
server. The name of this secret follows the pattern: `connector-<connectionid>`. Additionally, in order for multiple
instances of the same type of connector to be deployed independently to manage unique connections, the resources that
are applied to the cluster must also be uniquely named. In order to achieve this and in order to provide a means to
mount the dynamically-named secret, the unique`connectionid` of each connection will be added as a
[name suffix](https://github.com/kubernetes-sigs/kustomize/blob/d9c4c749e25141b51f477aa36b60d3d05f27ba59/examples/transformerconfigs/README.md#prefixsuffix-transformer)
to each instanced Kustomize chart, resulting in an independent set of resources in k8s for each connection instance. In
order to be able to mount the dynamically-named credential-secret and to be able to manage any other
fields in instanced resources that must dynamically set based on the `connectionid`, the
[Kustomize name reference transformer](https://github.com/kubernetes-sigs/kustomize/blob/d9c4c749e25141b51f477aa36b60d3d05f27ba59/examples/transformerconfigs/README.md#name-reference-transformer)
should be used. This transformer _may_ be used wherever it is needed, but _must_ be used in the following cases:
1. To dynamically adjust the name of the mounted secret containing the connector's gRPC credentials
2. To dynamically modify labels and label selectors on resources that require them. This includes, but may not be
   limited to: Deployments, Services, Prometheus PodMonitors, and Prometheus ServiceMonitors
3. To dynamically adjust the value of any fields in a resource that _directly_ reference the same or another resource
   by name. This includes, but may not be limited to: the `spec/to/name` field in OpenShift Routes, and the
   `'metadata/annotations/service.beta.openshift.io\/serving-cert-secret-name'` field in Services that are using
   certificates that are generated by OpenShift

For reference, please review the usage of name reference transformers in the
[provided sample](samples/bundle-manifest/connector).

#### Resource Name Restrictions
Due to the use of the name-suffix transformation, the length of the name of all k8s resources defined in the instanced
section of the BundleManifest will be extended by 37 characters such that the final name of each resource will be 
`<provided-name>-<connectionid>`. Therefore, all 
[length restrictions on resource names](https://kubernetes.io/docs/concepts/overview/working-with-objects/names/) must 
be met for the final name of each k8s resource contained within the instanced section of the bundle, which is equal to 
the length of the supplied name for the resource plus 37.

## Testing Connector BundleManifests
BundleManifests can be tested against branches a hosted Git repo on clusters with an AIOpsEdge installation. The
AIOpsEdge is installed as a subcomponent of a cp4waiops installation. It can also be installed independently from the 
IBM operator catalog.
### Step 1 (Private Repo or GHE only): Create a Github Authentication Secret
1. Navigate to https://github.com/settings/tokens
2. Click `Generate New Token`
3. Enter a description into the `Note` field and then click `Generate Token` at the bottom of the page
4. Copy the newly generated access token
5. Create a Kubernetes secret containing your Github credentials
```shell
oc create secret generic github-secret --from-literal=username=<Github-Username> --from-literal=password=<Github-Access-Token>
```

### Step 2: Apply Modified BundleManifest
1. Modify your BundleManifest to point to the desired branch, repo, and authentication secret
2. Apply the modified BundleManifest to the cluster
3. Wait for the BundleManifest to reach the `Configured` phase
```     
NAME     STATUS
sample   Configured
```
### Step 3: Test a locally deployed connection
1. Apply a ConnectorConfiguration to the cluster with `local` selected for the .spec.deploymentType
```yaml
apiVersion: connectors.aiops.ibm.com/v1beta1
kind: ConnectorConfiguration
metadata:
  name: sample-test-conn
spec:
  type: sample
  deploymentType: local
  config:
     cpuThreshold: 80
     severityLevel: 6
     expirySeconds: 30
     enableCPUHeavyWorkload: true
```
2. Wait for the ConnectorConfiguration's status to indicate that the connector is `Running`
```
NAME               STATUS
sample-test-conn   Running
```

## Reloading Pods on Secret Changes
In many cases, it may be beneficial to reload your connector pod when the contents of a k8s secret used by the pod are 
modified. The AIOpsEdge operator provided limited capabilities for reloading pods automatically when those pods are 
managed by a k8s deployment. The label `connectors.aiops.ibm.com/restart-on-secret-changed` can be added to a k8s 
deployment, which will result in the following behavior. The label triggers a new deployment rollout when it is 
detected that the secrets a deployment references have changed. The logic will detect secrets that have been mounted 
as a secret-volume or projected-volume, or are referenced from the env section of any of the container specs.

## Use in Air-Gapped Clusters
In some cases you may wish to deploy your connector in an air-gapped environment (one which is isolated from the public 
internet). In this case your environment may be unable to communicate with Github or any other public Git repository 
hosting service. If this is the case then you may need to host a git repository service containing your deployment 
manifests within the cluster. For an example of how to do this, see the provided [sample](/samples/airgap-git-server).
In order to use the sample you must fill out the [build-args](/samples/airgap-git-server/build-args.env) file to 
configure the image build (which requires docker). You must also fill out the 
[values file](/samples/airgap-git-server/deploy/connector-airgap/values.yaml) for the 
[helm chart](/samples/airgap-git-server/deploy/connector-airgap) to configure deployment of the git-server and any 
BundleManifests that will be deployed using it. You may then use the provided 
[make targets](/samples/airgap-git-server/Makefile) to build and deploy the git-server to your cluster.