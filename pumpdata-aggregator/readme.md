#Installation

1. Run command `oc apply -f deploy/template.yaml`. Please note that this template is based on the [MongoDB Ephemeral Template](https://github.com/sclorg/mongodb-container/blob/master/examples/mongodb-ephemeral-template.json).
2. Go to service catalog and search for "pump".
3. Select Pump Data Aggregator and click on Instantiate Template. Make sure to insert the respective broker url.

#Development

To run the integration code in Java class PumpDataAggregator with live-reload use kamel cli with the following parameters:

```bash
kamel run src/main/java/com/redhat/examples/PumpDataAggregator.java --dev -d camel-undertow -d camel-swagger-java --secret integration-secret
 ```

This will generate a custom resource of type Integration. If you would like to push code changes of the integration logic you must copy sources.content part into the template.yaml.

In order to run the integration in debug mode run:

```bash
kamel run src/main/java/com/redhat/examples/PumpDataAggregator.java --dev -d camel-undertow -d camel-swagger-java --secret integration-secret  --property logging.level.com.redhat.examples=DEBUG
 ```
 