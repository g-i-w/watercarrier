package watercarrier;

import java.util.*;
import java.io.*;
import paddle.*;
import creek.*;

public class DuplicateDisk {

	private SenseDevice devices;
	private Map<String,SystemCommand> processes;

	private void checkOutput ( String device ) throws Exception {
		if ( devices.baseline().contains( device ) ) throw new Exception( device+" was attached before DuplicateDisk started!" );
		if ( processes.keySet().contains( device ) ) throw new Exception( device+" is being written by another process!" );
	}

	private String checkPath ( String device ) throws Exception {
		File file = new File( device );
		if (!file.exists()) file.createNewFile();
		return file.getAbsolutePath();
	}
	
	
	public DuplicateDisk () {
		processes = new LinkedHashMap<>();
		devices = new SenseDevice();
	}

	public void dd ( String in, String out ) throws Exception {
		String input = checkPath( in );
		String output = checkPath( out );
		checkOutput( output );
		
		String name = "IN: "+input+", OUT:"+output;
		System.out.println( name );
		
		SystemCommand ddProc = new SystemCommand(
			"dd if="+input+" of="+output+" bs=4M status=progress",
			name,
			-1,     // no timeout
			false,  // not verbose
			true    // only output last line
		);
		
		processes.put( output, ddProc );
		new Thread( ddProc ).start();
	}
	
	public Table status ( Table table ) {
		table.append( new String[]{ "Device", "STDOUT", "STDERR" } );
		for (Map.Entry<String,SystemCommand> entry : processes.entrySet()) {
			SystemCommand sc = entry.getValue();
			String stdout = sc.stdout().text();
			String stderr = sc.stderr().text();
			table.append( new String[]{ entry.getKey(), stdout, stderr } );
		}
		return table;
	}

	public static void main ( String[] args ) throws Exception {
		DuplicateDisk dd = new DuplicateDisk();
		dd.dd( "/dev/zero", "zeros_0.img" );
		Thread.sleep(1000);
		dd.dd( "/dev/zero", "zeros_1.img" );
		while (true) {
			Thread.sleep(1000);
			System.out.println( dd.status( new SimpleTable() ) );
		}
	}

}
