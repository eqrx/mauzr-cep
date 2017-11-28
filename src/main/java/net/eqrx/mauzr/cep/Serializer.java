package net.eqrx.mauzr.cep;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.espertech.esper.client.Configuration;

/**
 * Bidirectorional Serializer for MQTT
 * 
 * @author Alexander Sowitzki
 *
 */
@SuppressWarnings("unchecked")
public class Serializer {
	/** Format to class mapping */
	public static final Map<String, String> FORMAT_MAPPING = new HashMap<>();
	/** Topic to structure mapping */
	private final Map<String, List<Map<String, String>>> mapping = new HashMap<>();
	/** Esper configuration */
	private final Configuration configuration;

	static {
		// Add mappings
		FORMAT_MAPPING.put("s", "java.lang.String");
		FORMAT_MAPPING.put("I", "java.lang.Integer");
		FORMAT_MAPPING.put("f", "java.lang.Float");
	}

	public Serializer(Configuration configuration) {
		this.configuration = configuration;
	}

	/**
	 * Add mapping for topic.
	 * 
	 * @param topic
	 *            topic to map
	 * @param structure
	 *            Message structure
	 */
	@SuppressWarnings("rawtypes")
	public void addMapping(String topic, List<Map<String, String>> structure) {
		try {
			// Constructed esper type
			Map esperType = new HashMap();
			// Iterate over stucture
			for (Map element : structure) {
				// Put contents in type
				esperType.put(element.get("name"), Class.forName(FORMAT_MAPPING.get(element.get("format"))));
			}
			// Memember work
			mapping.put(topic, structure);
			// Add to configuration
			this.configuration.addEventType(topic, esperType);
		} catch (ClassNotFoundException e) {
			throw new AssertionError();
		}
	}

	@SuppressWarnings("rawtypes")
	public byte[] pack(String topic, Map data) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream stream = new DataOutputStream(bytes);

		for (Map<String, String> element : this.mapping.get(topic)) {
			Object value = data.get(element.get("name"));
			switch (element.get("format")) {
			case "I":
				stream.writeInt((int) value);
			case "s":
				stream.writeUTF((String) value);
			case "f":
				stream.writeFloat((float) value);
			}
		}
		return bytes.toByteArray();
	}

	@SuppressWarnings("rawtypes")
	public Object unpack(String topic, byte[] data) throws IOException {
		DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data));

		Map result = new HashMap();
		for (Map<String, String> element : mapping.get(topic)) {
			Object value = null;
			switch (element.get("format")) {
			case "I":
				value = stream.readInt();
			case "s":
				value = stream.readUTF();
			case "f":
				value = stream.readFloat();
			}
			result.put(element.get("name"), value);
		}
		return result;
	}
}
