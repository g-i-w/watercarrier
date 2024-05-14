package watercarrier;

import java.util.*;
import creek.*;
import paddle.*;


public class DuplicationStation extends ServerState {

	DuplicationDirectory duplication;
	TemplateFile duplicationTemplate;
	TemplateFile biblesdTemplate;

	public DuplicationStation ( String dir, int port ) throws Exception {
		duplication = new DuplicationDirectory( dir );
		duplicationTemplate = new TemplateFile( "watercarrier/duplication.html", "////" );
		biblesdTemplate = new TemplateFile( "watercarrier/biblesd.html", "////" );
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
		//print( c );
		if (c instanceof InboundHTTP) {
			// convert type to InboundHTTP
			InboundHTTP session = (InboundHTTP)c;
			
			// check path
			if (session.request().path().equals("/duplication")) {
			
				// process the query key=value data				
				String statusMessage = duplication.processQuery( session.request().query() );
				
				// fill in blanks in the TemplateFile
				duplicationTemplate
					.replace( "filesTable", duplication.filesHTML() )
					.replace( "devicesTable", duplication.devicesHTML() )
					.replace( "statusTable", duplication.statusHTML() )
					.replace( "statusMessage", statusMessage )
				;
			
				// HTTP response
				session.response(
					new ResponseHTTP(
						new String[]{ "Content-Type", "text/html" },
						duplicationTemplate.toString()
					)
				);
				
			} else if (session.request().path().equals("/biblesd")) {
			
				// process the query key=value data
				session.request().query().put( "file", "biblesd-20240426.img.gz" );
				String statusMessage = duplication.processQuery( session.request().query() );
				
				// fill in blanks in the TemplateFile
				biblesdTemplate.replace( "deviceDivs", duplication.devicesCommandStatusHTML( "biblesd", "biblesd-20240426.img.gz" ) );
			
				// HTTP response
				session.response(
					new ResponseHTTP(
						new String[]{ "Content-Type", "text/html" },
						biblesdTemplate.toString()
					)
				);
				
			} else {
				session.response(
					new ResponseHTTP( "not found" )
				);
			}
			
		} else if (c instanceof OutboundHTTP) {
			OutboundHTTP session = (OutboundHTTP)c;
			/*System.out.println(
				"----\nResponse:\n\n"+
				(new String(session.response().data()))+
				"\n----"
			);*/
		}
	}
	
	public static void main ( String[] args ) throws Exception {
		DuplicationStation ds = new DuplicationStation( args[0], Integer.parseInt(args[1]) );
	}

}
