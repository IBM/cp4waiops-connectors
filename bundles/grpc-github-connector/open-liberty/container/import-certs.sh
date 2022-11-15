#!/bin/bash

set -e -o pipefail

if [ -z "$SRC_CERT_DIR" ]; then
    SRC_CERT_DIR=/webhook
fi

if [ -z "$DST_CERT_DIR" ]; then
    DST_CERT_DIR=/opt/cert
fi

if [ -z "$CUSTOM_CERT_DIR" ]; then
    CUSTOM_CERT_DIR=/opt/custom_certs
fi

function waitForFile() {
    filename="$1"
    echo "waiting for $filename to appear"
    while [ ! -f "$filename" ]; do
        sleep 10
    done
    echo "$filename found"
}

crtFilename="$SRC_CERT_DIR/tls.crt"
keyFilename="$SRC_CERT_DIR/tls.key"
dstCrtFilename="$DST_CERT_DIR/tls.crt"
dstStoreFilename="$DST_CERT_DIR/tls.p12"
keystoreFilename="$DST_CERT_DIR/keystore.p12"
keystorePassFilename="$DST_CERT_DIR/jks.pass"

waitForFile "$crtFilename"
waitForFile "$keyFilename"

PASS=$(head -c 128 /dev/urandom | base64 -w 0)
echo "$PASS" > "$keystorePassFilename"

echo `ls $DST_CERT_DIR`
rm -f $keystoreFilename

echo "importing root CAs to keystore"
keytool -importkeystore \
    -srckeystore /opt/java/openjdk/lib/security/cacerts \
    -srcstorepass changeit \
    -destkeystore "$keystoreFilename" \
    -deststorepass "$PASS" \
    -deststoretype PKCS12

echo "converting private key and cert to pkcs12"
openssl pkcs12 -export \
    -in "$crtFilename" \
    -inkey "$keyFilename" \
    -name "$dstStoreFilename" \
    -out "$dstStoreFilename" \
    -passin pass: \
    -passout "file:$keystorePassFilename"

echo "copying private key and cert to keystore"
keytool -importkeystore \
    -srcstoretype PKCS12 \
    -srckeystore "$dstStoreFilename" \
    -srcstorepass "$PASS" \
    -destkeystore "$keystoreFilename" \
    -deststorepass "$PASS" \
    -destkeypass "$PASS" \
    -deststoretype PKCS12

if [ -d $CUSTOM_CERT_DIR ]; then
    echo "importing custom certificates to keystore"
    for f in $(ls $CUSTOM_CERT_DIR); do
        echo "copying $f"
        keytool -importcert \
            -alias "$CUSTOM_CERT_DIR/$f" \
            -file "$CUSTOM_CERT_DIR/$f" \
            --keystore "$keystoreFilename" \
            -storepass "$PASS" \
            -storetype PKCS12 \
            -noprompt
    done
fi

serverXMLFile="config/configDropins/overrides/server.xml"

echo "overriding server.xml"
echo '<server>' > "$serverXMLFile"
echo '    <keyStore id="defaultKeyStore" location="'"$keystoreFilename"'" password="'"$PASS"'" type="PKCS12"/>' >> "$serverXMLFile"
echo '</server>' >> "$serverXMLFile"
