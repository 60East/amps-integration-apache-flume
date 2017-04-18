# AMPS Flume Source

This project allows you to directly use an AMPS messaging subscription as an
input source into Apache Flume. This is done by implementing a custom pollable
Flume source.

## Build

1. First make sure you have at least Maven 3.3 installed.

2. Create a ${basedir}/jars directory and place the 5.2.0.1 or later AMPS Java client JAR under it.

3. Execute "mvn clean install".

4. The resulting JAR will be at: target/AMPSFlumeSource-1.0.0-SNAPSHOT.jar


# Installation

To install the AMPS custom Flume source as a plugin within your Flume
installation, follow the instructions below:

1. Create the plugins.d directory if it doesn't exist in the Flume home
directory.

2. Create the following directory & file structure under plugins.d:

```
    plugins.d/
        AMPSFlume/
            lib/
                AMPSFlumeSource-1.0.0-SNAPSHOT.jar
            libext/
                amps_client.jar
```

More information can be found about Flume plugins in the
[Flume User Guide](https://flume.apache.org/FlumeUserGuide.html).
Look at the plugins.d section.

# Example Run

1. Copy the example Flume config from test/resources/flume-conf.properties
to $FLUME_HOME/conf/.

2. Start an AMPS server using the config at test/resources/amps-config.xml.

3. From $FLUME_HOME, start Flume with:
    bin/flume-ng agent -Xmx512m -c conf/ -f conf/flume-conf.properties -n agent -Dflume.root.logger=DEBUG,console
4. Send JSON messages to AMPS at tcp://127.0.0.1:9007/amps/json on the
FlumeTopic.
5. You should see your messages logged to the Flume console log due to the
example config's logger sink.

