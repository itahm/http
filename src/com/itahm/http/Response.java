package com.itahm.http;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Response {

	public final static String CRLF = "\r\n";
	public final static String FIELD = "%s: %s"+ CRLF;
	public final static String OK = "OK";
	public final static String UNAUTHORIZED = "Unauthorized";
	public final static String BADREQUEST = "Bad request";
	public final static String VERSIONNOTSUP = "HTTP Version Not Supported";
	public static final String CONFLICT = "Conflict";
	
	private Map<String, String> header;
	private String startLine;
	private byte [] body;
	
	private Response () {
		header = new HashMap<String, String>();
	}
	
	public static Response getInstance(int status, String reason) {
		return getInstance(status, reason, "");
	}
	
	public static Response getInstance(int status, String reason, File file) {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			FileChannel fc = raf.getChannel();
			int size = (int)fc.size();
		
			if (size > 0) {
				ByteBuffer buffer = ByteBuffer.allocate(size);
				
				fc.read(buffer);
				
				buffer.flip();
				
				return getInstance(status, reason, StandardCharsets.UTF_8.decode(buffer).toString());
			}
			
		} catch (IOException ioe) {
		}
		
		return null;
	}
	
	public static Response getInstance(int status, String reason, String body) {
		Response response = new Response();
		
		response.setResponseStatus(status, reason);
		response.setResponseHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
		response.setResponseHeader("Access-Control-Allow-Origin", "http://itahm.com");
		response.setResponseHeader("Access-Control-Allow-Credentials", "true");
		
		response.setResponseBody(body);
		
		return response;
	}
	
	public Response setResponseHeader(String name, String value) {
		this.header.put(name, value);
		
		return this;
	}
	
	public Response setResponseBody(String body) {
		try {
			this.body = body.getBytes(StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			this.body = new byte[0];
		}
		
		return this;
	}
	
	public ByteBuffer build() throws IOException {
		if (this.startLine == null || this.body == null) {
			throw new IOException("malformed http request!");
		}
		
		StringBuilder sb = new StringBuilder();
		Iterator<String> iterator;		
		String key;
		byte [] header;
		byte [] message;
		
		sb.append(this.startLine);
		sb.append(String.format(FIELD, "Content-Length", String.valueOf(this.body.length)));
		
		iterator = this.header.keySet().iterator();
		while(iterator.hasNext()) {
			key = iterator.next();
			
			sb.append(String.format(FIELD, key, this.header.get(key)));
		}
		
		sb.append(CRLF);
		
		header = sb.toString().getBytes(StandardCharsets.US_ASCII.name());
		
		message = new byte [header.length + this.body.length];
		
		System.arraycopy(header, 0, message, 0, header.length);
		System.arraycopy(this.body, 0, message, header.length, this.body.length);
		
		return ByteBuffer.wrap(message);
	}
	
	public Response setResponseStatus(int status, String reason) {
		this.startLine = "HTTP/1.1 "+ status +" "+ reason +CRLF;
		
		return this;
	}
	
}
