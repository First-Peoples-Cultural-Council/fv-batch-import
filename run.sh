#!/bin/bash

## Basic script to process multiple bash files=
echo "Running against base url: ${BASE_URL} with workspace id ${SITE_WORKSPACE_GUID}"

for i in ./data/${SITE_WORKSPACE_GUID}/*${MIGRATOR_KEYWORD}*.csv; do
    # Process $i
    echo "Processing file ${i}...";
    java -Xmx1g -jar target/fv-batch-import-*.jar \
    -url "${BASE_URL}" \
    -username ${JENKINS_PROD_USERNAME} \
    -domain FV \
    -dialect-id ${SITE_WORKSPACE_GUID} \
    -csv-file \
    $i \
    -data-path \
    data/${SITE_WORKSPACE_GUID}/files/ \
    ${SKIP_VALIDATION_PARAM} \
    -password \
    ${JENKINS_PROD_PASSWORD} \
done
