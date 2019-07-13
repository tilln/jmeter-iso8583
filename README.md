# jmeter-iso8583 [![travis][travis-image]][travis-url]

[travis-image]: https://travis-ci.org/tilln/jmeter-iso8583.svg?branch=master
[travis-url]: https://travis-ci.com/tilln/jmeter-iso8583

Overview
------------

### Dependencies

* [org.jpos / jpos](https://search.maven.org/remotecontent?filepath=org/jpos/jpos/2.1.3/jpos-2.1.3.jar)
* [org.bouncycastle / bcprov-jdk15on](https://search.maven.org/remotecontent?filepath=org/bouncycastle/bcprov-jdk15on/1.61/bcprov-jdk15on-1.61.jar)
* [org.bouncycastle / bcpg-jdk15on](https://search.maven.org/remotecontent?filepath=org/bouncycastle/bcpg-jdk15on/1.61/bcpg-jdk15on-1.61.jar)
* [org.jdom / jdom2](https://search.maven.org/remotecontent?filepath=org/jdom/jdom2/2.0.6/jdom2-2.0.6.jar)
* [org.osgi / org.osgi.core](https://search.maven.org/remotecontent?filepath=org/osgi/org.osgi.core/6.0.0/org.osgi.core-6.0.0.jar)
* [commons-cli / commons-cli](https://search.maven.org/remotecontent?filepath=commons-cli/commons-cli/1.4/commons-cli-1.4.jar)
* [org.yaml / snakeyaml](https://search.maven.org/remotecontent?filepath=org/yaml/snakeyaml/1.24/snakeyaml-1.24.jar)
* [org.hdrhistogram / HdrHistogram](https://search.maven.org/remotecontent?filepath=org/hdrhistogram/HdrHistogram/2.1.11/HdrHistogram-2.1.11.jar)
    
### Limitations

Some exceptions are currently only logged in the Q2 log but not in the JMeter log:
- Packager exceptions in ChannelAdaptor on receive,
- Configuration exceptions when deploying incorrect config (e.g. packager file not found)

Workaround: Log level DEBUG.
