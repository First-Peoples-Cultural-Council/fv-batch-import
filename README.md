# FirstVoices Batch Import #

The following scripts enable processing a CSV file and creating the appropriate documents and blob within FirstVoices.
It can be used to import words, phrases, songs, stories, alphabets and potentially more items into FirstVoices with audio, images and videos.

## IMPORTANT NOTE ##
Currently only FVWordMigrator has been tested and refactored.
Others will need more testing with data and may need some tweaking to run.

## How to Use ##
There are a number of 'Migrators' included that can be generated as runnable jar files.
To change which 'Migrator' is being used, update pom.xml, changing the mainClass in the maven-jar-plugin:

```
            <plugin> 
                <groupId>org.apache.maven.plugins</groupId> 
                <artifactId>maven-jar-plugin</artifactId>
                <configuration>
                    <archive>
                        <manifest>
                            <addClasspath>true</addClasspath>
                            <classpathPrefix>lib/</classpathPrefix>
                            <mainClass>*task/FVWordMigrator*</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
```

Run `mvn clean install` to generate the jar file (will be in target/fv-batch-import.x.x.x.jar)

To execute the batch upload for words, for example, run the following command:

```java -Xmx1g -jar target/fv-batch-import-1.0.0.jar \
        -url "http://localhost:8080/nuxeo"
        -username YOUR_USER
        -domain FV
        -dialect-id 00f00f0-aef0-468f-adc7-d0e00000d09
        -csv-file
        /path/to/csv/words.csv
        -data-path
        /path/to/media/files/
        -password
```

You will be promoted for a password once you run the command.
Parameters will differ depending on the Migrator being executed; memory allocation on the size of the import.
