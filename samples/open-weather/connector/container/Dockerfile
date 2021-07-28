## This should really be a multi-stage dockerfile, that builds the source and then the image

FROM openliberty/open-liberty:kernel-slim-java8-openj9-ubi

# Add Liberty server configuration including all necessary features
COPY --chown=1001:0  server.xml /config/

RUN features.sh

COPY --chown=1001:0  weather.war /config/dropins/

## The following are examples of security related artifacts you may want to import into the image
# COPY --chown=1001:0 tls.crt /etc/x509/certs/tls.crt
# COPY --chown=1001:0 tls.key /etc/x509/certs/tls.key

## The following are examples of security flags you may want to turn on
# ENV SEC_TLS_TRUSTDEFAULTCERTS=true
# ENV SEC_IMPORT_K8S_CERTS=true

RUN configure.sh