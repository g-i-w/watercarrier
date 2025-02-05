package watercarrier;

import java.util.*;
import creek.*;
import paddle.*;

public class NetControl extends ServerState {

	Map<String,SystemCommand> domainPublishers;
	
	
	public NetControl ( int port ) throws Exception {
		new ServerHTTP (
			this,
			port,
			"EBiblePrinterServer",
			1024, // inbound memory size
			20000  // timeout [ms]
		);
	}
	
	@Override
	public void received ( Connection c ) {
		InboundHTTP session = http( c );
		
		Tree ifaceState = null;
		
		try {
			ifaceState = new JSON().deserialize( new SystemCommand( "ip -json addr" ).output() );
			System.out.println( ifaceState.serialize() );
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Set<String> ipv4 = new HashSet<>();
		for (Tree iface : ifaceState.branches()) {
			String ifname = iface.get("ifname").value();
			if (ifname.equals("lo")) continue;
			Tree addrInfo = iface.get("addr_info");
			if (addrInfo==null) continue;
			for (Tree addrFamily : addrInfo.branches()) {
				String family = addrFamily.get("family").value();
				String addr = addrFamily.get("local").value();
				if (family.equals("inet")) ipv4.add( addr );
			}
		}
		System.out.println( ipv4 );
		
		/*
		for (String ip : domainPublishers.keySet()) {
			if (!ipv4.contains(ip)) {
				domainPublishers.get(ip).kill();
				domainPublishers.remove(ip);
			}
		}
		for (String ip : ipv4) {
			if (!domainPublishers.containsKey(ip)) {
				domainPublishers.put( ip, new SystemCommand( "avahi-publish -a "+domainName+" "+ip ) );
			}
		}
		*/
		
		session.response( new ResponseHTTP( new String[]{ "Content-Type", "text/plain" }, ipv4.toString() ) );
		
		
	}
	
	public static void main ( String[] args ) throws Exception {
		new NetControl( Integer.valueOf( args[0] ) );
	}

}
