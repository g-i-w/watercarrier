package watercarrier;

import java.util.*;
import creek.*;
import paddle.*;


public class DuplicationStation extends ServerState {

	DuplicationDirectory duplication;

	public DuplicationStation ( String dir, int port ) throws Exception {
		duplication = new DuplicationDirectory( dir );
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
			InboundHTTP session = (InboundHTTP)c;
			if (session.request().path().equals("/duplication")) {
			
				String statusMessage = "";
			
				// process duplication command
				Map<String,String> query = session.request().query();
				System.out.println( "********************\nQuery: "+query+"\n********************\n" );
				if (query.containsKey("file") && query.containsKey("device") && query.containsKey("command")) {
					String file = query.get("file");
					String disk = query.get("disk");
					String command = query.get("command");
					if (command.equals("start")) {
						statusMessage = duplication.fileToDisk( file, disk );
					} else if (command.equals("cancel")) {
						statusMessage = duplication.cancel(disk);
					}
				}
			
				// respond with HTML
				session.response(
					new ResponseHTTP(
						new String[]{ "Content-Type", "text/html" },
						
						"<html>\n<head>\n"+
						"<title>Duplication</title>\n"+
						//"<meta http-equiv=\"refresh\" content=\"4\" />\n"+
						"</head>\n<body onload=\"window.history.pushState({}, document.title, '/duplication' );\">\n"+

						"<div style=\"background-color:lightgray;width:100%;\"><a href=\"/duplication\">Refresh Page</span></a></div>\n"+
						
						"<form name=\"duplicationForm\" action=\"/duplication\" onsubmit=\"confirmSubmit();\">\n"+
						"<input type=\"hidden\"  name=\"command\"  value=\"\">\n"+
						"<h2>Image Files:</h2>\n"+
						duplication.filesHTML()+"\n<br>\n"+
						"<h2>Removable Media:</h2>\n"+
						duplication.devicesHTML()+"\n<br>\n"+
						"<input type=\"submit\"  value=\"Start\">\n"+
						"<h2>Status:</h2><br>"+statusMessage+"\n"+
						duplication.statusHTML()+"\n<br>\n"+
						"</form> <br>\n"+

						"<script>\n"+
						"function confirmSubmit () { document.duplicationForm.command.value = 'start'; return confirm('Start writing?'); }\n"+
						"</script>\n"+

						"</body>\n</html>\n"
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
