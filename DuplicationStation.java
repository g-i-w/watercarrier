package watercarrier;

import java.util.*;
import creek.*;
import paddle.*;


public class DuplicationStation extends ServerState {

	DuplicationDirectory duplication;
	TemplateFile biblesdTemplate;
	TemplateFile biblelocalsdTemplate;

	public DuplicationStation ( String dir, int port ) throws Exception {
		duplication = new DuplicationDirectory( dir );
		biblesdTemplate = new TemplateFile( "watercarrier/biblesd.html", "////" );
		biblelocalsdTemplate = new TemplateFile( "watercarrier/biblelocalsd.html", "////" );
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
		super.received( c );
		if (c instanceof InboundHTTP) {
			// convert type to InboundHTTP
			InboundHTTP session = (InboundHTTP)c;
			System.out.println( session.request().path()+" "+session.request().query() );
			
			// check path
			if (session.request().path().equals("/biblesd")) {
			
				// process the query key=value data
				String statusMessage = duplication.processQuery( session.request().query() );
				
				// fill in blanks in the TemplateFile
				biblesdTemplate.replace( "statusMessage", statusMessage );
				biblesdTemplate.replace( "deviceDivs", duplication.devicesHTML( "biblesd", "biblesd.img.gz", "fileToDisk" ) );
			
				// HTTP response
				session.response(
					new ResponseHTTP(
						new String[]{ "Content-Type", "text/html" },
						biblesdTemplate.toString()
					)
				);
				
			} else if (session.request().path().equals("/biblelocalsd")) {
			
				// process the query key=value data
				String statusMessage = duplication.processQuery( session.request().query() );
				
				// fill in blanks in the TemplateFile
				biblelocalsdTemplate.replace( "statusMessage", statusMessage );
				biblelocalsdTemplate.replace( "deviceDivs", duplication.devicesHTML( "biblelocalsd", "mmcblk0", "diskToDisk" ) );
			
				// HTTP response
				session.response(
					new ResponseHTTP(
						new String[]{ "Content-Type", "text/html" },
						biblelocalsdTemplate.toString()
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
