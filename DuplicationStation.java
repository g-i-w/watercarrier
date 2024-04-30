package watercarrier;

import java.util.*;
import creek.*;
import paddle.*;


public class DuplicationStation extends ServerState {

	SenseDevice sd;

	public DuplicationStation ( int port ) throws Exception {
		(sd = new SenseDevice()).checkDevices();
		ServerHTTP server = new ServerHTTP (
			this,
			port,
			"DuplicationStation Server",
			1024,
			4000
		);
		while( server.starting() ) Thread.sleep(1);
	}
	
	public void received ( Connection c ) {
		print( c );
		if (c instanceof InboundHTTP) {
			InboundHTTP session = (InboundHTTP)c;
			if (session.request().path().equals("/devices")) {
				session.response(
					new ResponseHTTP( "added: "+sd.addedDevices()+"\nremoved: "+sd.removedDevices()+"\n"+session.request().query() )
				);
			} else {
				session.response(
					new ResponseHTTP( "not found" )
				);
			}
		} else if (c instanceof OutboundHTTP) {
			OutboundHTTP session = (OutboundHTTP)c;
			System.out.println(
				"----\nResponse:\n\n"+
				(new String(session.response().data()))+
				"\n----"
			);
		}
	}
	
	public static void main ( String[] args ) throws Exception {
		DuplicationStation ds = new DuplicationStation( 7777 );
	}

}
