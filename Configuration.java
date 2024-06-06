package watercarrier;

import java.util.*;
import creek.*;
import paddle.*;


public class Configuration extends ServerState {

	TemplateFile livingwater;
	String livingwaterServicePath = "/etc/systemd/system/livingwater-hotspot.service";
	//String livingwaterServicePath = "watercarrier/livingwater-hotspot.service"; //testing
	
	TemplateFile configuration;
	String changePasswordScript = "./watercarrier/changePassword.sh";
	String checkPasswordScript = "./watercarrier/checkPassword.sh";
	//String changePasswordScript = "./watercarrier/changePassword-dummy.sh"; //testing
	
	private boolean something ( Map<String,String> map, String str ) {
		return map.get(str)!=null && !map.get(str).equals("");
	}
	
	private void updateLivingwater () {
		try {
			String text = FileActions.read( livingwaterServicePath );
			List<String> items = Regex.groups( text, "ssid\\s+(\\S+)\\s+password\\s+(\\S+)" );
			//System.out.println( "items: "+items );
			configuration
				.replace( "wifi-ssid", items.get(0) )
				.replace( "wifi-password", items.get(1) )
			;
		} catch (Exception e) {
			e.printStackTrace();		
		}
	}
	
	private void blankStatus () {
		configuration
			.replace( "wifi-status", "" )
			.replace( "password-status", "" )
		;
	}

	public Configuration ( int port ) throws Exception {
		livingwater = new TemplateFile( "watercarrier/livingwater-hotspot.template", "////" );
		configuration = new TemplateFile( "watercarrier/configuration.html", "////" );
		blankStatus();
		updateLivingwater();
		ServerHTTP server = new ServerHTTP (
			this,
			port,
			"Configuration Server",
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
			Map<String,String> query = session.request().query();
			System.out.println( session.request().path() );
			
			if (something(query,"new") && something(query,"old")) {
				//System.err.println( "********** Changing password **********\n" );
				SystemCommand command = new SystemCommand( changePasswordScript+" "+query.get("old")+" "+query.get("new") );
				String output = command.output();
				//System.err.println( output );
				String status =
					(
						command.exitValue()==0 ?
						"<div class=ok>Changed password for <b>servant</b> from <b>"+query.get("old")+"</b> to <b>"+query.get("new")+"</b><br>"+
						"You can now login using <b>ssh&nbsp;servant@bible.local</b> with password <b>"+query.get("new")+"</b></div><br>"
						:
						"<div class=error>ERROR: unable to update password<br>"+
						command.stderr().text()+"</div>"
					)
				;
				configuration.replace( "password-status", status );
			}
			
			if (something(query,"wifi-ssid") && something(query,"wifi-password")) {
				String status = "";
				//String cmdStr = checkPasswordScript+" "+query.get("p");
				//System.err.println( cmdStr );
				//SystemCommand checkCommand = new SystemCommand( cmdStr );
				//System.err.println( checkCommand.output() );
				//int exitVal = checkCommand.exitValue();
				//System.err.println( exitVal );
				//if (exitVal==0) {
				if (new SystemCommand( checkPasswordScript+" "+query.get("p") ).success()) {
					livingwater
						.replace( "wifi-ssid", query.get("wifi-ssid") )
						.replace( "wifi-password", query.get("wifi-password") )
					;
					try {
						status = "<div class=ok>Restarting WiFi network with SSID: <b>"+query.get("wifi-ssid")+"</b> password: <b>"+query.get("wifi-password")+"</b></div>";

                                                FileActions.write( livingwaterServicePath, livingwater.toString() );
                                                System.err.println( new SystemCommand( "systemctl daemon-reload" ).output() );

						SystemCommand restartCmd = new SystemCommand( "systemctl restart livingwater-hotspot.service" );
						System.err.println( restartCmd.output() );
						status += "<br>"+( restartCmd.exitValue()==0 ? "<div class=ok>Done.</div>" : "<div class=error>Error while restarting:</div><br>"+restartCmd.output() );

						System.err.println( new SystemCommand( "systemctl status livingwater-hotspot.service" ).output() );
						
						updateLivingwater();
						
					} catch (Exception e) {
						e.printStackTrace();
						status = "<div class=error>ERROR while updating WiFi settings:<br>"+e+"</div>";
						
					}
				} else {
					status = "<div class=error>ERROR: incorrect server password</div>";
				}
				configuration.replace(
					"wifi-status",
					status
				);
			}
			
			// HTTP response
			session.response(
				new ResponseHTTP(
					new String[]{ "Content-Type", "text/html" },
					configuration.toString()
				)
			);
			
			blankStatus();
		}
		
	}
	
	
	public static void main ( String[] args ) throws Exception {
		Configuration config = new Configuration( Integer.parseInt(args[0]) );
	}			
	
}

