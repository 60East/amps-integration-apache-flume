# AMPS Flume Components

This project allows you to directly use an AMPS messaging subscription as an
input source into Apache Flume. This is done by implementing a custom pollable
Flume source. It also provides a Flume sink that can publish a stream into AMPS.

For more in depth information on the AMPS / Apache Flume integration, see our [Crank Up Apache Flume](http://www.crankuptheamps.com/blog/posts/2017/04/18/crank-up-flume-with-amps/) blog article.

## Build

1. First make sure you have at least Maven 3.3 installed.

2. Create a `${basedir}/jars` directory and place the 5.3.3.0 or later AMPS Java client JAR under it.

3. Execute `mvn clean install`.

4. The resulting JAR will be at: `target/AMPSFlumeComponents-1.1.0-SNAPSHOT.jar`


# Installation

To install the AMPS custom Flume source as a plugin within your Flume
installation, follow the instructions below:

1. Create the `plugins.d` directory if it doesn't exist in the Flume home
directory.

2. Create the following directory & file structure under `plugins.d`:

```
    plugins.d/
        AMPSFlume/
            lib/
                AMPSFlumeComponents-1.1.0-SNAPSHOT.jar
            libext/
                amps_client.jar
```

More information can be found about Flume plugins in the
[Flume User Guide](https://flume.apache.org/FlumeUserGuide.html).
Look at the `plugins.d` section.

# Example Run

Included in the project repository is a working example that shows off the powerful aggregated subscription feature of AMPS.

1. Copy the example Flume config from `src/test/resources/flume-conf.properties` to `$FLUME_HOME/conf/`.

2. Start an AMPS server using the config at `src/test/resources/amps-config.xml`.

3. Create the temporary output directory specified in the above configuration:
```
    mkdir /tmp/amps-flume/
```

4. From $FLUME_HOME, start Flume with:
```
    bin/flume-ng agent -Xmx512m -c conf/ -f conf/flume-conf.properties -n agent -Dflume.root.logger=INFO,console
```

5. In a new window, subscribe to the AMPSFlumeSinkTest topic using the AMPS spark utility:
```
    spark subscribe -server localhost:9007 -topic AMPSFlumeSinkTest
```

6. In another window, publish the example JSON messages to the Orders topic using the AMPS spark utility:
```
    spark publish -server localhost:9007 -topic Orders -rate 1 -file src/test/resources/messages.json
```

7. Notice that we are publishing at a rate of 1 message per a second, so that in the output we can see the aggregate fields change over time as updates arrive. The messages are replicated to two Flume memory channels, feeding two separate sinks (a file_roll sink and the custom AMPS sink). After about 15 seconds our aggregated subscription view of the data gives us all the results under the output directory (`/tmp/amps-flume/`) and the window with the AMPSFlumeSinkTest subscription (which the AMPS sink publishes to).

8. Please send any issues or questions to support@crankuptheamps.com
