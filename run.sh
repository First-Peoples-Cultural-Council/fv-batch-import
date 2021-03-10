#!/bin/bash

## Basic script to process multiple bash files=
echo "Running against base url: ${BASE_URL} with workspace id ${SITE_WORKSPACE_GUID}"

for i in ./data/${SITE_WORKSPACE_GUID}/*${MIGRATOR}*.csv; do
    echo "Processing file ${i} ($1) using migrator for ${MIGRATOR} ...";
    if [[ "$i" != *"errors"* ]]; then
      java -Xmx1g -jar target/fv-batch-import-*.jar -url "${BASE_URL}" -username ${JENKINS_PROD_USERNAME} -domain FV -dialect-id ${SITE_WORKSPACE_GUID} -csv-file $i -data-path data/${SITE_WORKSPACE_GUID}/files/ $1 -password ${JENKINS_PROD_PASSWORD}
      echo "${i} Completed."
    fi
done
