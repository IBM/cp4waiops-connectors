## Build application
FROM registry.access.redhat.com/ubi8/openjdk-11:latest as builder
WORKDIR /build

USER root

# Download and cache dependencies beforehand
RUN mkdir /build/grpc-java-sdk && mkdir /build/connector
COPY connector/pom.xml /build/connector/
RUN cd /build/connector && mvn dependency:go-offline -B

COPY connector /build/connector
RUN cd /build/connector && mvn install

## Final image
FROM icr.io/appcafe/websphere-liberty:22.0.0.9-kernel-java11-openj9-ubi

# Add Liberty server configuration including all necessary features
COPY --chown=1001:0  container/server.xml /config/
RUN configure.sh

COPY --chown=1001:0 container/import-certs.sh /opt/import-certs.sh
COPY --from=builder --chown=1001:0  /build/connector/target/connector-template.war /config/apps/

## The following are examples of security related artifacts you may want to mount to the image at runtime.
# /etc/x509/certs/tls.crt
# /etc/x509/certs/tls.key
# example argument: --mount type=bind,source=/path/to/certs,destination=/etc/x509/certs,readonly

## The following are examples of security flags you may want to turn on
# ENV SEC_TLS_TRUSTDEFAULTCERTS=true
# ENV SEC_IMPORT_K8S_CERTS=true

USER 1001

RUN configure.sh
