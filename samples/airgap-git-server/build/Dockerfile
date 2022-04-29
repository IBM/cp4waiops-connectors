FROM registry.access.redhat.com/ubi8/ubi-minimal:latest

ARG REPO
ARG EMAIL
ARG USERNAME
ARG BUNDLE_ARTIFACTS_DIR
ARG COMMIT_MESSAGE

# Install Dependencies
RUN microdnf install httpd httpd-tools mod_ssl git -y && \
  # Remove default ssl config
  rm -f /etc/httpd/conf.d/ssl.conf \
    /usr/share/doc/perl-IO-Socket-SSL/certs/client-key.enc \
    /usr/share/doc/perl-IO-Socket-SSL/certs/client-key.pem \
    /usr/share/doc/perl-IO-Socket-SSL/certs/server-ecc-key.pem \
    /usr/share/doc/perl-IO-Socket-SSL/certs/server-key.enc \
    /usr/share/doc/perl-IO-Socket-SSL/certs/server-key.pem \
    /usr/share/doc/perl-IO-Socket-SSL/certs/server2-key.pem \
    /usr/share/doc/perl-Net-SSLeay/examples/server_key.pem && \
  mkdir -p /srv/git/$REPO && \
  mkdir /conf && \
  # Permissions
  chmod ugo+rwx /srv/git && \
  chmod ugo+rwx /srv/git/$REPO && \
  chmod ugo+rwx /conf && \
  chmod ugo+rwx /run/httpd && \
  chmod ugo+rwx /etc/httpd && \
  chmod ugo+rwx /var/log/httpd && \
  chmod ugo+rwx /usr/lib64/httpd/modules && \
  chmod ugo+rwx /var/lib/httpd

USER 1000

# Copy Config Files
COPY --chown=1000:1000 httpd.conf /etc/httpd/conf/
COPY --chown=1000:1000 git-server.conf /etc/httpd/conf.d/

# Copy All Manifests and Directory Structures
COPY --chown=1000:1000 $BUNDLE_ARTIFACTS_DIR/ /srv/git/$REPO/$BUNDLE_ARTIFACTS_DIR/

# Initialize the repo
RUN cd /srv/git/$REPO && \
    git init && \
    git config user.email "$EMAIL" && \
    git config user.name "$USERNAME" && \
    git add . && \
    git commit -m "$COMMIT_MESSAGE"

# Run the server
ENTRYPOINT ["/usr/sbin/httpd", "-DFOREGROUND"]