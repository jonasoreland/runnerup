/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.export;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormCrawler {

	protected Set<String> cookies = new HashSet<String>();
	protected FormValues formValues = new FormValues();

	public FormCrawler() {
		super();
		logout();
	}

	protected void logout() {
		cookies.clear();
		formValues.clear();
	}

	protected interface Writable {
		void write(OutputStream out) throws IOException;
	}

	protected class StringWritable implements Writable {
		String s;

		public StringWritable(String s) {
			this.s = s;
		}

		public void write(OutputStream out) throws IOException {
			out.write(s.getBytes());
		}
	};

	class Part<Value extends Writable> {
		public Part(String name, Value value) {
			this.name = name;
			this.value = value;
		}

		String name = null;
		String filename = null;
		String contentType = null;
		String contentTransferEncoding = null;
		Value value = null;
	};
	
	public class FormValues extends HashMap<String,String> implements Writable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -5681066662144155533L;

		FormValues() {
			super();
		}

		@Override
		public void write(OutputStream o) throws IOException {
			boolean first = true;
			DataOutputStream out = new DataOutputStream(o);
			for (String k : keySet()) {
				if (!first)
					out.writeByte('&');
				out.writeBytes(URLEncoder.encode(k));
				out.writeByte('=');
				out.writeBytes(URLEncoder.encode(get(k)));
				first = false;
			}
		}
	}
	
	public static void postMulti(HttpURLConnection conn, Part<?> parts[]) throws IOException {
		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary = "*****" + Long.toString(System.currentTimeMillis())
				+ "*****";
		conn.setRequestProperty("Content-Type",
				"multipart/form-data; boundary=" + boundary);
		DataOutputStream outputStream = new DataOutputStream(
				conn.getOutputStream());
		for (int i = 0; i < parts.length; i++) {
			outputStream.writeBytes(twoHyphens + boundary + lineEnd);
			outputStream.writeBytes("Content-Disposition: form-data; name=\""
					+ parts[i].name + "\"");
			if (parts[i].filename != null)
				outputStream.writeBytes("; filename=\"" + parts[i].filename
						+ "\"");
			outputStream.writeBytes(lineEnd);

			if (parts[i].contentType != null)
				outputStream.writeBytes("Content-Type: " + parts[i].contentType
						+ lineEnd);
			if (parts[i].contentTransferEncoding != null)
				outputStream.writeBytes("Content-Transfer-Encoding: "
						+ parts[i].contentTransferEncoding + lineEnd);
			outputStream.writeBytes(lineEnd);
			parts[i].value.write(outputStream);
			outputStream.writeBytes(lineEnd);
		}
		outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
		outputStream.flush();
		outputStream.close();
	}

	protected void addCookies(HttpURLConnection conn) {
		boolean first = true;
		StringBuffer buf = new StringBuffer();
		for (String cookie : cookies) {
			if (!first)
				buf.append("; ");
			buf.append(cookie.split(";", 2)[0]);
			first = false;
		}
		conn.addRequestProperty("Cookie", buf.toString());
	}

	protected void getCookies(HttpURLConnection conn) {
		List<String> connCookies = conn.getHeaderFields().get("Set-Cookie");
		if (connCookies != null) {
			cookies.addAll(connCookies);
		}
	}

	protected String getFormValues(HttpURLConnection conn) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		StringBuffer buf = new StringBuffer();
		String s = null;
		while ((s = in.readLine()) != null) {
			buf.append(s);
		}
		String html = buf.toString();
		Map<String, String> values = parseHtml(html);
		formValues.putAll(values);
		return html;
	}

	private Map<String, String> parseHtml(String html) {
		Matcher matcher = inputPattern.matcher(html);
		Map<String, String> parameters = new HashMap<String, String>();

		while (matcher.find()) {
			Map<String, String> attributes = parseAttributes(matcher.group(1));
			String name = attributes.get("name");
			if (name != null) {
				String value = attributes.get("value");
				if (value == null) {
					value = "";
				}
				parameters.put(name, value);
			}
		}
		return parameters;
	}
	private static Map<String, String> parseAttributes(String attributesStr) {
		Map<String, String> attributes = new HashMap<String, String>();
		Matcher matcher = attributePattern.matcher(attributesStr);
		while (matcher.find()) {
			String key = matcher.group(1);
			String value = "";
			String g = matcher.group(2).trim();
			if (g != null) {
				value = g;
			}
			attributes.put(key, value.trim());
		}
		return attributes;
	}

	/**
	 * The regex pattern to find a form input parameter in HTML.
	 */
	private static final Pattern inputPattern =
			Pattern.compile("<input(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern attributePattern =
			Pattern.compile("(\\w+)=\"(.*?)\"");

	protected String findName(Set<String> names, String s) {
		for (String k : names) {
			if (k.indexOf(s) != -1)
				return k;
		}
		return null;
	}
}