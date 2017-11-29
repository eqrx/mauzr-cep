package net.eqrx.mauzr.cep;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;

/**
 * Main class.
 *
 * @author Alexander Sowitzki
 */
public class Main {
	/** Event type configuration for esper */
	private final Configuration esperConfig = new Configuration();
	/** Provider for esper functions */
	private EPServiceProvider esperProvider;
	/** Serializer to convert between esper and mqtt */
	private final Serializer serializer = new Serializer(esperConfig);
	/** MQTT client */
	private MqttClient mqttc;

	/** Program entry point */
	public static void main(String[] args) {
		try {
			new Main(args[0]);
			while (true) {
				Thread.sleep(Long.MAX_VALUE);
			}
		} catch (InterruptedException e) {
			System.exit(0);
		} catch (IOException | ClassNotFoundException | MqttException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Initiate and execute program.
	 *
	 * @throws IOException
	 *             IO related errors
	 * @throws ClassNotFoundException
	 *             Invalid class mapping
	 * @throws MqttException
	 *             MQTT failures
	 */
	@SuppressWarnings("rawtypes")
	public Main(String configPath) throws IOException, ClassNotFoundException, MqttException {
		YamlReader reader = new YamlReader(new FileReader(configPath));

		Map config = (Map) reader.read();
		config = (Map) config.get("mauzr-cep");
		setupCEP((Map) config.get("cep"));
		initMQTT((Map) config.get("mqtt"), (Map) config.get("cep"));
	}

	/**
	 * Setup and establish MQTT connection.
	 * 
	 * @param mqttConfig
	 *            MQTT configuration section
	 * @param cepConfig
	 *            CEP configuration section
	 * @throws IOException
	 *             IO related errors
	 * @throws MqttException
	 *             MQTT failures
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void initMQTT(Map mqttConfig, Map cepConfig) throws IOException, MqttException {
		List<Map<String, String>> mqttHostConfigs = (List<Map<String, String>>) mqttConfig.get("hosts");
		// Only one host is supported here
		Map<String, String> mqttHostConfig = mqttHostConfigs.get(0);

		// Build broker URI
		String protocol = "tcp";
		if (mqttHostConfig.containsKey("ca")) {
			protocol = "ssl";
		}
		String broker = String.format("%s://%s:%s", protocol, mqttHostConfig.get("host"), mqttHostConfig.get("port"));
		this.mqttc = new MqttClient(broker, (String) mqttHostConfig.get("user"));

		// File connection options
		MqttConnectOptions options = new MqttConnectOptions();
		options.setCleanSession(false);
		if (mqttHostConfig.containsKey("password")){
			options.setUserName((String) mqttHostConfig.get("user"));
			options.setPassword(((String) mqttHostConfig.get("password")).toCharArray());
		}
		options.setKeepAliveInterval(Integer.parseInt((String) mqttConfig.get("keepalive")) / 1000);
		if (mqttHostConfig.containsKey("ca")) {
			options.setSocketFactory(TLS.getSocketFactory((String) mqttHostConfig.get("ca")));
		}

		// Set callback
		mqttc.setCallback(new MqttCallback() {
			@Override
			public void messageArrived(String topic, MqttMessage message) throws Exception {
				try {
					System.out.println(String.format("Received from %s", topic));
					// Call serializer to unpack map and put it into the CEP engine
					esperProvider.getEPRuntime().sendEvent((Map) serializer.unpack(topic, message.getPayload()), topic);
				} catch (Exception e) {
					e.printStackTrace();
					throw e;
				}
			}

			@Override
			public void deliveryComplete(IMqttDeliveryToken arg0) {
			}

			@Override
			public void connectionLost(Throwable arg0) {
			}
		});

		// Connector to broker
		mqttc.connectWithResult(options);

		// Subscribe in case of new session
		for (Map<String, String> inputConfig : (List<Map<String, String>>) cepConfig.get("inputs")) {
			this.mqttc.subscribe(inputConfig.get("topic"), Integer.parseInt(inputConfig.get("qos")));
		}

	}

	/**
	 * Setup CEP subsystem.
	 * 
	 * @param cepConfig
	 *            CEP configuration section
	 * @throws MqttException
	 *             MQTT failures
	 * @throws ClassNotFoundException
	 *             Invalid class mapping
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void setupCEP(Map cepConfig) throws MqttException, ClassNotFoundException {
		// Add event definitions for inputs
		for (Map inputConfig : (List<Map>) cepConfig.get("inputs")) {
			String topic = (String) inputConfig.get("topic");
			this.serializer.addMapping(topic, (List<Map<String, String>>) inputConfig.get("structure"));
		}
		// Load configuration
		this.esperProvider = EPServiceProviderManager.getDefaultProvider(this.esperConfig);

		// Load statements
		for (Map<String, String> statementConfig : (List<Map<String, String>>) cepConfig.get("statements")) {
			String topic = (String) statementConfig.get("topic");

			// Define type for serializer
			Object structure = statementConfig.get("structure");
			this.serializer.addMapping(topic, (List<Map<String, String>>) structure);

			// Create statement
			EPStatement statement = this.esperProvider.getEPAdministrator()
					.createEPL((String) statementConfig.get("statement"));

			// Fetch information for publish
			int qos = Integer.parseInt((String) statementConfig.get("qos"));
			boolean retain = ((String) statementConfig.get("retain")).equals("True");

			// Add statement listener
			statement.addListener(new UpdateListener() {
				@Override
				public void update(EventBean[] newEvents, EventBean[] oldEvents) {
					for (EventBean bean : newEvents) {
						System.out.println(String.format("Publishing to %s", topic));
						try {
							mqttc.publish(topic, serializer.pack(topic, (Map) bean.getUnderlying()), qos, retain);
						} catch (MqttException | IOException e) {
							e.printStackTrace();
						}
					}
				}
			});
		}
	}
}
