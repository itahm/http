package com.itahm.http;

import java.io.Closeable;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;


public abstract class Listener extends Timer implements Runnable, Closeable {

	private final ServerSocketChannel channel;
	private final ServerSocket listener;
	private final Selector selector;
	private final ByteBuffer buffer;
	private static final Set<Request> connections = new HashSet<Request>();
	
	private Boolean closed = false;
	
	public Listener() throws IOException {
		this("0.0.0.0", 80);
	}

	public Listener(String ip) throws IOException {
		this(ip, 80);
	}
	
	public Listener(int tcp) throws IOException {
		this("0.0.0.0", tcp);
	}
	
	public Listener(String ip, int tcp) throws IOException {
		this(new InetSocketAddress(InetAddress.getByName(ip), tcp));
	}
	
	public Listener(InetSocketAddress addr) throws IOException {
		channel = ServerSocketChannel.open();
		listener = channel.socket();
		selector = Selector.open();
		buffer = ByteBuffer.allocateDirect(1024);
		
		listener.bind(addr);
		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_ACCEPT);
		
		new Thread(this).start();
		
		onStart();
	}
	
	private void onConnect() {
		SocketChannel channel = null;
		Request request;
		
		try {
			channel = this.channel.accept();
			request = new Request(channel, this);
			
			channel.configureBlocking(false);
			channel.register(this.selector, SelectionKey.OP_READ, request);
			
			connections.add(request);
			
			return;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (channel != null) {
			try {
				channel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void onRead(SelectionKey key) {
		SocketChannel channel = (SocketChannel)key.channel();
		Request request = (Request)key.attachment();
		int bytes = 0;
		
		this.buffer.clear();
		
		try {
			bytes = channel.read(buffer);
			
			if (bytes != -1) {
				if (bytes > 0) {
					this.buffer.flip();
					
					request.parse(this.buffer);
				}
				
				return;
			}
		} catch (IOException ioe) {
			// RESET에 의한 예외일 수 있음. client의 reset을 막을수는 없기에...
			//System.out.println(ioe.getMessage());
		}
		
		closeRequest(request);
	}

	public void closeRequest(Request request) {
		request.close();
		
		connections.remove(request);
		
		onClose(request);
	}
	
	public int getConnectionSize() {
		return connections.size();
	}
	
	@Override
	public void close() throws IOException {
		synchronized (this.closed) {
			if (this.closed) {
				return;
			}
		
			this.closed = true;
		}
		
		for (Request request : connections) {
			request.close();
		}
			
		connections.clear();
		
		cancel();
		
		this.selector.wakeup();
	}

	@Override
	public void run() {
		Iterator<SelectionKey> iterator = null;
		SelectionKey key = null;
		int count;
		
		while(!this.closed) {
			try {
				count = this.selector.select();
			} catch (IOException e) {
				e.printStackTrace();
				
				continue;
			}
			
			if (count > 0) {
				iterator = this.selector.selectedKeys().iterator();
				while(iterator.hasNext()) {
					key = iterator.next();
					iterator.remove();
					
					if (!key.isValid()) {
						continue;
					}
					
					if (key.isAcceptable()) {
						onConnect();
					}
					else if (key.isReadable()) {
						onRead(key);
					}
				}
			}
		}
		
		try {
			this.selector.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			this.listener.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		onStop();
	}
	
	abstract protected void onRequest(Request request);
	abstract protected void onClose(Request request);
	abstract protected void onStart();
	abstract protected void onStop();
	
	public static void main(String [] args) throws IOException {
		final Set<Request> req = new HashSet<Request>();
		
		final Listener server = new Listener(2015) {

			@Override
			protected void onRequest(Request request) {
				
				request.getRequestURI();
				request.getRequestMethod();
				
				try {
					
					request.sendResponse(Response.getInstance(200, "OK","{\"test\":\"good\"}"));

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				req.add(request);
			}

			@Override
			protected void onClose(Request request) {
				req.remove(request);
			}

			@Override
			protected void onStart() {
				System.out.println("HTTP Server running...");
			}

			@Override
			protected void onStop() {
				System.out.println("stop HTTP Server.");
			}
			
		};
		
		System.in.read();
		
		server.close();
	}
	
}
