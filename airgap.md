# Connector Airgap Deployment Process

You may wish to deploy your custom connector in an air-gapped environment (one which is isolated from the public internet). In this case your environment may be unable to communicate with any external service such as Github. You may need to then host a git repository service containing your deployment manifests within the cluster and configure the registry mirror and copy your images to this internal registry.

## Prerequisites
- A production grade Docker V2 compatible registry, such as Quay Enterprise, JFrog Artifactory, or Docker Registry
- An online node that can copy images from the source image registry to the production grade internal image registry
- The online node must have skopeo installed
`yum install -y skopeo`
- Access to the Red Hat OpenShift Container Platform cluster as a user with the `cluster-admin` role.

## Create the gitserver
Follow the instructions for [creating the Git Server](/bundle-manifest.md#use-in-air-gapped-clusters)


## Configure the registry mirror
Create a new `ImageContentSourcePolicy` on your Red Hat OpenShift cluster to enable the redirection of requests to pull images from a repository on a mirrored image registry.

Replace `<image>/<name>` with the proper name and `<example.io/subdir>` with your internal image registry repository, then run the command on the inf node of your Red Hat OpenShift cluster:

```
oc create -f - << EOF
apiVersion: operator.openshift.io/v1alpha1
kind: ImageContentSourcePolicy
metadata:
name: icr-mirror
spec:
repositoryDigestMirrors:
- mirrors:
    - <example.io/subdir>
    source: cp.icr.io/cp/<image>/<name>
- mirrors:
    - example.io/subdir
    source: icr.io/cpopen
EOF
```

Note: Do not prefix mirrors with http:// or https:// and ensure that they do not have trailing / characters and make sure there is no whitespace after EOF

The mirror will be rolled out to all nodes on the cluster, cycled one at a time, temporarily making them unschedulable before rebooting.

Enter the following command to observe the nodes:

```
watch oc get nodes
```
Note: Red Hat OpenShift Container Platform 4.7 and later do not reboot the nodes.

Once all nodes have rebooted, verify the ImageContentSourcePolicy was applied with `oc debug` to query the mirrors on the host nodes.

```
$  oc debug node/worker0.subdomain
Starting pod/worker0examplecom-debug ...
To use host binaries, run `chroot /host`
Pod IP: 12.34.56.789
If you don't see a command prompt, try pressing enter.

# chroot /host
# cat /etc/containers/registries.conf
unqualified-search-registries = ["registry.access.redhat.com", "docker.io"]

[[registry]]
prefix = ""
location = "cp.icr.io/cp/<image>/<name>"
mirror-by-digest-only = true

[[registry.mirror]]
    location = "example.io/subdir"

[[registry]]
prefix = ""
location = "icr.io/cpopen"
mirror-by-digest-only = true

[[registry.mirror]]
    location = "example.io/subdir"
```

Note: Further RedHat Documentation [here](https://access.redhat.com/documentation/en-us/openshift_container_platform/4.6/html/updating_clusters/updating-restricted-network-cluster)

## Copying images from source to target internal image registry
We outlined the steps to configure the cluster to redirect external image registry requests to an internal registry through the ImageContentSourcePolicy. Now we must populate the internal registry with the images from the source image registry.

Complete the following steps from the online node:

1. Log in to the IBM® Entitled Container Registry with the proper credentials.
```
skopeo login cp.icr.io
```
2. Log in to your internal production grade image registry with the proper credentials.
```
skopeo login example.io
```

3. Use skopeo copy to copy the following images from the IBM Entitled Container Registry to your internal production grade image registry. Replace the <variables> so that the first image points to the source image registry and the second points to the internal image registry.

```
skopeo copy --all docker://cp.icr.io/cp/<image>/<name>/<image>@sha256:<digest> docker://example.io/subdir/<image>@sha256:<digest>
```

## Verify pulling images from the mirrored registry
Enter the following commands from the inf node of your OpenShift cluster:

1. Pick a worker node from `oc get nodes` and enter the following:
```
oc debug node/<worker node>
```
A command prompt must be presented.

2. Switch to host binaries by entering the chroot /host command.

```
$ oc debug node/worker0.example.com
Starting pod/worker0examplecom-debug ...
To use host binaries, run `chroot /host`
Pod IP: 12.34.56.789
If you don't see a command prompt, try pressing enter.
# chroot /host
```
3. Enter the `podman login` command to authenticate your mirrored image registry.

```
$ podman login example.io
Username: sampleemail@email.com
Password:
Login Succeeded!
```
4. Attempt to pull one of the images from the source image registry.

```
$ podman pull cp.icr.io/cp/<image>/<name>/<image>@sha256:<digest>
Trying to pull cp.icr.io/cp/<image>/<name>/<image>@sha256:<digest>...
Getting image source signatures

Copying blob ...
Writing manifest to image destination
Storing signatures
```
 
5. Verify that the image is pulled.

```
$ podman images | grep cp.icr.io/cp/<image>/<name>/<image>
cp.icr.io/cp/<image>/<name>/<image>    <none>     <digest>   22 hours ago    58 MB
```

## Red Hat OpenShift Container Registry pull secret

For images to be pulled properly, the OpenShift global pull secret must contain credentials to access your internal container registry.

1. Create a base64 encoded string of the credentials used to access your internal container registry.

Note: The following example uses example.io/subdir as the internal container registry.

- Use the credentials to access your example.io/subdir internal container registry.

```
echo -n "<username>:<password>" | base64 -w0
```

2. Create an `auth.json` to include the base64 encoded string of your credentials.
```
{
  "auth": "<base64 encoded string from previous step>",
  "username":"<example.io username>",
  "password":"<example.io generated entitlement key>"
}
```
3. Enter the following command to include the auth.json in your .dockerconfigjson.

```
oc get secret/pull-secret -n openshift-config -ojson | \
jq -r '.data[".dockerconfigjson"]' | \
base64 -d - | \
jq '.[]."example.io" += input' - authority.json > temp_config.json
```


4. Verify that your auth credentials were created in the resulting file:

```
$ cat temp_config.json
{
    "auths": {
        "quay.io": {
        "auth": "",
        "email": ""
        },
        "registry.connect.redhat.com": {
        "auth": "",
        "email": ""
        },
        "registry.redhat.io": {
        "auth": "",
        "email": ""
        },
        "example.io": {
        "auth": "<base64 encoded string created in previous step>",
        "username": "<example.io username>",
        "password": "<example.io password>"
        }
    }
}
```

5. Apply the updated configuration to the OpenShift cluster.

```
oc set data secret/pull-secret -n openshift-config --from-file=.dockerconfigjson=temp_config.json
```
6. Verify that your pull-secret is updated

```
oc get secret/pull-secret -n openshift-config -ojson | \
jq -r '.data[".dockerconfigjson"]' | \
base64 -d -
```

The change will be rolled out to all nodes on the cluster, cycled one at a time, temporarily making them unschedulable before rebooting.

7. Enter the watch oc get nodes command to observe the nodes.

```
$ oc get nodes
NAME                             STATUS                        ROLES    AGE   VERSION
master0.example.com            NotReady,SchedulingDisabled   master   99d   v1.19.0+43983cd
master1.example.com            Ready                         master   99d   v1.19.0+43983cd
master2.example.com            Ready                         master   99d   v1.19.0+43983cd
worker0.example.com            NotReady,SchedulingDisabled   worker   99d   v1.19.0+43983cd
worker1.example.com            Ready                         worker   99d   v1.19.0+43983cd
worker2.example.com            Ready                         worker   99d   v1.19.0+43983cd
```
Note: Red Hat OpenShift Container Platform 4.7 and later do not reboot the nodes.

8. When the global pull secret is updated, you can remove the temporary files.
```
rm authority.json temp_config.json
```