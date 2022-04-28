FROM registry.access.redhat.com/ubi8/ubi-minimal:latest

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
  mkdir -p /srv/git && \
  mkdir /conf && \
  # Permissions
  chmod ugo+rwx /srv/git && \
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
COPY --chown=1000:1000 ./ /srv/git/repo-name/

# Initialize the repo
RUN cd /srv/git/repo-name && \
    git init && \
    git config user.email "replaceme@replaceme.com" && \
    git config user.name "replaceme"

# Run the server
ENTRYPOINT ["/usr/sbin/httpd", "-DFOREGROUND"]