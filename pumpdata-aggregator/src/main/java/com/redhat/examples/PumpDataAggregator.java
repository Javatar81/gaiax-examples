package com.redhat.examples;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dataset.SimpleDataSet;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.model.rest.RestBindingMode;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.model.Sorts;

public class PumpDataAggregator extends RouteBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger(PumpDataAggregator.class);
	
	public static class PumpData {
		private final String source;
		private final String sensorName;
		private final BigDecimal value;
		private final long time;

		public PumpData(String source, String sensorName, BigDecimal value, long time) {
			super();
			this.source = source;
			this.sensorName = sensorName;
			this.value = value;
			this.time = time;
		}

		public String getSource() {
			return source;
		}

		public String getSensorName() {
			return sensorName;
		}

		public BigDecimal getValue() {
			return value;
		}

		public long getTime() {
			return time;
		}

		@Override
		public int hashCode() {
			return Objects.hash(sensorName, source, time, value);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PumpData other = (PumpData) obj;
			return Objects.equals(sensorName, other.sensorName) && Objects.equals(source, other.source)
					&& time == other.time && Objects.equals(value, other.value);
		}

		@Override
		public String toString() {
			return String.format("PumpData [source=%s, sensorName=%s, value=%s, time=%s]", source, sensorName, value,
					time);
		}

	}

	public static class PumpDataTextProcessor implements Processor {
		
		private static final Logger LOGGER = LoggerFactory.getLogger(PumpDataTextProcessor.class);
		
		@Override
		public void process(Exchange exchange) throws Exception {
			String inputBody = exchange.getIn().getBody(String.class);
			PumpData pumpData = parse(inputBody);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Processed input data: '{}'", inputBody);
				LOGGER.debug("Converted into '{}'", pumpData);
			}
			exchange.getIn().setBody(pumpData);
		}

		private static PumpData parse(String text) {
			String[] tokens = text.split(",");
			return new PumpData(tokens[0], tokens[1], new BigDecimal(tokens[2]), Long.parseLong(tokens[3]));
		}
	}
	
	public static class PumpDataDocumentProcessor implements Processor {
		
		private static final Logger LOGGER = LoggerFactory.getLogger(PumpDataDocumentProcessor.class);
		
		@Override
		public void process(Exchange exchange) throws Exception {
			Document[] document = exchange.getIn().getBody(Document[].class);
			Collection<PumpData> pumpData = parse(document);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Processed input data: '{}'", Arrays.asList(document));
				LOGGER.debug("Converted into '{}'", pumpData);
			}
			
			exchange.getIn().setBody(pumpData);
		}
		
		private static Collection<PumpData> parse(Document[] doc) {
			return Arrays.stream(doc).map(PumpDataDocumentProcessor::parse).collect(Collectors.toList());

		}
		
		private static PumpData parse(Document doc) {
			return new PumpData(doc.getString("source"), doc.getString("sensorName"), new BigDecimal(doc.getDouble("value")), doc.getLong("time"));
		}
		
	}

	@Override
	public void configure() throws Exception {
		bindToRegistry();
		LOGGER.debug("binding complete");
		configureRest();
		LOGGER.debug("rest configured");
		fromMqttToMongoDb();
		LOGGER.debug("mqtt route enabled");
		fromMongoDbToRest();
		LOGGER.debug("rest endpoint enabled");
		LOGGER.debug("route initialization completed");
	}

	private void bindToRegistry() throws Exception {
		String uriStr = String.format("mongodb://%s:%s@mongodb:27017/%s", propertyInject("database-user", String.class), propertyInject("database-password", String.class), propertyInject("database-name", String.class));
		MongoClient mongoClient = new MongoClient(new MongoClientURI(uriStr));
		bindToRegistry("myDb", mongoClient);
		SimpleDataSet simpleDataSet = new SimpleDataSet();
		bindToRegistry("data", simpleDataSet);
	}

	private void configureRest() {
		restConfiguration().component("undertow").bindingMode(RestBindingMode.json)
        	.dataFormatProperty("prettyPrint", "true")
        	.contextPath("/").port(8080)
        	.apiContextPath("/api-doc")
            .apiProperty("api.title", "User API").apiProperty("api.version", "1.0.0");
	}

	private void fromMongoDbToRest() {
		from("direct:find20")
			.setHeader(MongoDbConstants.LIMIT).constant(20)
			.setHeader(MongoDbConstants.SORT_BY).constant(Sorts.descending("time"))
			.to("mongodb:myDb?database={{database-name}}&collection=temperature&operation=findAll")
			.process(new PumpDataDocumentProcessor());
		 rest("/api").bindingMode(RestBindingMode.json)
		 	.get("/pump")
		 		.description("Retrieves the last 20 pump data entries")
		 		//.type(PumpDataAggregator.PumpData.class)
		 		.to("direct:find20");
	}

	private void fromMqttToMongoDb() {
		from("paho:{{topic}}?brokerUrl={{broker-url}}")
				.process(new PumpDataTextProcessor())
				.log("Hello:" + body().toString()).to("dataset:data")
				.to("direct:insert");
		from("direct:insert").to("mongodb:myDb?database={{database-name}}&collection=temperature&operation=save");
	}
}