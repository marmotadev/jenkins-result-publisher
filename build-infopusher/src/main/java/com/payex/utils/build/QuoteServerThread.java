package com.payex.utils.build;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class QuoteServerThread {
	private BufferedReader in;

	public QuoteServerThread() throws IOException {
	    this("QuoteServer");
	}

	public QuoteServerThread(String name) throws IOException {
//	    super(name);
	    DatagramSocket socket = new DatagramSocket(4445);

	    try {
	        in = new BufferedReader(new FileReader("one-liners.txt"));
	    }   
	    catch (FileNotFoundException e){
	        System.err.println("Couldn't open quote file.  Serving time instead.");
	    }
		
		
InetAddress packet;
		//		
//		byte[] buf = new byte[256];
//		DatagramPacket packet = new DatagramPacket(buf, buf.length);
//		socket.receive(packet);
//		
//		
//		String dString = null;
//		if (in == null)
//		    dString = new Date().toString();
//		else
//		    dString = getNextQuote();
//		buf = dString.getBytes();
//		
//		
//		InetAddress address = packet.getAddress();
//		int port = packet.getPort();
//		packet = new DatagramPacket(buf, buf.length, address, port);
//		socket.send(packet);
//		
		
	}   
}
