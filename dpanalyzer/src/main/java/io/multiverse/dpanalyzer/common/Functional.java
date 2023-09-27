package io.multiverse.dpanalyzer.common;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.net.util.SubnetUtils;

import io.vertx.core.Future;
import io.vertx.core.impl.CompositeFutureImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Functional helper class.
 *
 * @author Amar Abane
 */
public final class Functional {

	private Functional() {
	}

	public static <R> Future<List<R>> allOfFutures(List<Future<R>> futures) {
    return CompositeFutureImpl.all(futures.toArray(new Future[futures.size()]))
      .map(v -> futures.stream()
        .map(Future::result)
        .collect(Collectors.toList())
      );
    }

	public static List<Byte> bytesToList(byte[] bytes) {
		final List<Byte> list = new ArrayList<>();
		for (byte b : bytes) {
			list.add(b);
		}
		return list;
	}

	public static String parseSubnetAddress(String cidrIp) {
		try {
			SubnetUtils subnet = new SubnetUtils(cidrIp);
			return subnet.getInfo().getNetworkAddress();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
		return "";
	}

	public static boolean isBase64(String str) {
		if (str.isEmpty()) {
			return false;
		}
		Base64.Decoder decoder = Base64.getDecoder();
		try {
			decoder.decode(str);
			return true;
		} catch(IllegalArgumentException iae) {
			return false;
		}
	}

	public static UUID getUUID(String op) {
		if (op == null) {
			return UUID.randomUUID();
		}
		try {
			return UUID.fromString(op);
		} catch (IllegalArgumentException e) {
			return UUID.randomUUID();
		}
	}

	public static String validateAndConvertMAC(String str) {
		if (str == null) {
			return "";
		}
		// String regex = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
		String regex = "^([0-9A-Fa-f]{2}[:-])"
				+ "{5}([0-9A-Fa-f]{2})|"
				+ "([0-9a-fA-F]{4}\\."
				+ "[0-9a-fA-F]{4}\\."
				+ "[0-9a-fA-F]{4})$";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(str);
		if (m.matches()){
			String norm = str.replaceAll("[^a-fA-F0-9]", "");
			return norm.replaceAll("(.{2})", "$1"+":").substring(0,17);
		} else {
			return "";
		}
	}

	public static boolean checkCidrIp(String str) {
		if (str == null) {
			return false;
		}
		String regex = "^([0-9]{1,3}\\.){3}[0-9]{1,3}(\\/([0-9]|[1-2][0-9]|3[0-2]))$";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(str);
		return m.matches();
	}
	
	public static boolean isValidHostIp(String str) {
		if (str == null) {
			return false;
		}
		String regex = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}"
				+ "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(str);
		return m.matches();
	}
	
	public static boolean isValidHostname(String str) {
		if (str == null) {
			return false;
		}
		String regex = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*"
				+ "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(str);
		return m.matches();
	}

	public static JsonObject computePatched(JsonObject origin, JsonArray patch) throws IllegalArgumentException {
		String sPatched = rawPatch(origin.encode(), patch.encode());
		return new JsonObject(sPatched);
	}

	public static String rawPatch(String sOrigin, String sPatch) throws IllegalArgumentException {
		try {
			javax.json.JsonReader origReader = javax.json.Json.createReader(new ByteArrayInputStream(sOrigin.getBytes()));
			javax.json.JsonObject origin = origReader.readObject();

			javax.json.JsonReader patchReader = javax.json.Json.createReader(new ByteArrayInputStream(sPatch.getBytes()));
			javax.json.JsonPatch patch = javax.json.Json.createPatch(patchReader.readArray());

			patchReader.close();
			origReader.close();

			javax.json.JsonObject patched = patch.apply(origin);

			return patched.toString();
		} catch (javax.json.JsonException e) {
			throw new IllegalArgumentException("error in json patch");
		}
	}
}
