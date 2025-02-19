package watercarrier;

import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import paddle.*;
import creek.*;

public class DuplicateDisk {

	private SenseDevice devices;
	private Map<String,SystemCommand> processes;
	private String bootDisk;

	public DuplicateDisk () {
		processes = new LinkedHashMap<>();
		devices = new SenseDevice();
	}

	public DuplicateDisk ( String bootDisk ) {
		this();
		this.bootDisk = bootDisk;
	}
	
	public String diskToFile ( String device, String file, String label ) {
		try {
			if (!devices.deviceList().contains(device)) throw new Exception( device+" not found" );
			safeOutput( file );
			runCommand( file, "./watercarrier/diskToFile.sh "+device+" "+file, label );
			return "Writing disk "+device+" to file "+file;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String fileToDisk ( String file, String device, String label ) {
		try {
			safeUnmount( device );
			safeOutput( device );
			runCommand( device, "./watercarrier/fileToDisk.sh "+file+" "+device, label );
			return "Writing file "+file+" to disk "+device;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String diskToDisk ( String input, String output, String label ) {
		try {
			safeUnmount( output );
			safeOutput( output );
			runCommand( output, "./watercarrier/diskToDisk.sh "+input+" "+output, label );
			return "Copying disk "+input+" to disk "+output;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String diskToDiskPartial ( String input, String output, int count, String label ) {
		try {
			safeUnmount( output );
			safeOutput( output );
			runCommand( output, "./watercarrier/diskToDiskPartial.sh "+input+" "+output+" "+count, label );
			return "Copying disk "+input+" to disk "+output+" ("+count+" * 4MiB blocks)";
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String directoryToDisk ( String dir, String device, String label ) {
		try {
			safeUnmount( device );
			safeOutput( device );
			runCommand( device, "./watercarrier/directoryToDisk.sh "+dir+" "+device, label );
			return "Copying path "+dir+" to disk "+device;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public void dd ( String in, String out ) throws Exception {
		runCommand( out, "./watercarrier/raw.sh "+in+" "+out, "IN: "+in+", OUT: "+out );
	}
	
	public void umount ( String device ) throws Exception {
		for (Tree data : safeDevicesTree().branches()) {
			if (data.keys().contains("children")) {
				for (Tree child : data.get("children").branches()) {
					String name = child.get("name").value();
					if (name.indexOf(device) > -1 || device.indexOf(name) > -1) {
						umount( child );
					}
				}
			}
			String name = data.get("name").value();
			if (name.indexOf(device) > -1 || device.indexOf(name) > -1) {
				umount( data );
			}
		}
	}
	
	public void umount ( Tree device ) throws Exception {
		for (String mount : device.get("mountpoints").values()) {
			if (! mount.equals("null")) {
				System.out.println( "Unmounting "+mount+"..." );
				SystemCommand umount = new SystemCommand( "umount "+mount );
				System.out.println( umount.output() );
				if (umount.exitValue() != 0) throw new Exception( "Unable to unmount "+mount );
			}
		}
	}

	public void safeUnmount ( String device ) throws Exception {
		if (device.equals("null")) return; // allow "/dev/null" for testing
		if (!safeDevices().contains(device)) {
			System.out.println( safeDevices() );
			throw new Exception( device+" is not a safe device" );
		}
		umount( device );		
	}
	
	public void safeOutput ( String output ) throws Exception {
		if (processes.containsKey(output) && !processes.get(output).finished())
			throw new Exception( "Process '"+processes.get(output).name()+"' is writing to device '"+output+"'" );
	}

	public void runCommand ( String output, String command, String label ) throws Exception {
	
		System.out.println( "**** '"+label+"' --> '"+output+"' ****" );
		System.out.println( command );
		
		SystemCommand ddProc = new SystemCommand(
			command,
			label,
			-1,     // no timeout
			false,  // not verbose
			true    // only output last line
		);
		
		processes.put( output, ddProc );
		new Thread( ddProc ).start();
	}
	
	public Set<String> addedDevices () {
		return devices.addedDevices();
	}
	
	public Set<String> safeDevices () {
		if (bootDisk==null) return addedDevices(); // if no boot disk specified, only allow devices added after boot
		Set<String> safe = new TreeSet<>();
		for (String device : devices.deviceList()) {
			if (
				( !Regex.exists( device, bootDisk ) ) // NOT the boot disk
				&&
				( Regex.exists( device, "^\\/dev\\/sd[a-z]$" ) || Regex.exists( device, "^\\/dev\\/mmcblk[0-9]" ) ) // IS a safe disk
			) {
				safe.add( device );
			}
		}
		//System.out.println( safe );
		return safe;
	}
	
	public Tree safeDevicesTree () {
		Tree deviceTree = devices.deviceTree();
		if (deviceTree == null) return null;
		Set<String> safe = safeDevices();
		Tree safeDevices = new JSON();
		for (Tree device : deviceTree.get("blockdevices").branches()) {
			String name = "/dev/"+device.get("name").value();
			if (safe.contains(name)) safeDevices.add( name, device );
		}
		return safeDevices;
	}
	
	public boolean changed () {
		return devices.changed();
	}
	
	public Map<String,SystemCommand> processes () {
		return processes;
	}
	
	public void cleanup () {
		for (Map.Entry<String,SystemCommand> entry : processes.entrySet()) {
			SystemCommand sc = entry.getValue();
			if (sc.finished()) processes.remove( entry.getKey() );
		}
	}
	
	public void cancel () {
		for (SystemCommand proc : processes.values()) {
			if (proc!=null && proc.running())  kill( proc );
		}
	}

	public void cancel ( String fragment ) {
		for (Map.Entry<String,SystemCommand> entry : processes.entrySet()) {
			if (entry.getKey().indexOf( fragment ) > -1) kill( entry.getValue() );
		}
	}
	
	public void kill ( SystemCommand proc ) {
		System.out.println( "Killing process:\n" );
		System.out.println( proc );
		if (proc!=null) {
			proc.kill();
		} else {
			System.out.println( "ERROR: null process!" );
		}
	}
		
	public Set<DiskData> status () {
		Set<DiskData> ops = new TreeSet<>();
		Tree deviceTree = safeDevicesTree();
		for (Tree data : deviceTree.branches()) {
			DiskData op = new DiskData( data, this, null ); // null indicates root parent
			ops.add( op );
			ops.addAll( op.children() );
		}
		return ops;
	}
	
	public static void testB ( String[] args ) throws Exception {
		DuplicateDisk dd = new DuplicateDisk();
		String output = "";
		
		Scanner scanner = new Scanner( System.in );
		
		while (true) {
			if (dd.changed()) {
				//System.out.println( dd.safeDevicesTree().serialize() );
				//System.out.println( "Devices: "+dd.statusTree().serialize() );
				System.out.println( "Select device > " );
				String input = scanner.nextLine().trim();
				if (input.equals("q")) break;
				if (!input.equals("")) {
					try {
						//dd.fileToDisk( args[0], input, input );
						dd.directoryToDisk( args[0], "/dev/"+input, input );
						System.out.println( "Cloning "+args[0]+" --> "+input+"..." );
						System.out.println( dd.status() );
					} catch (Exception e) {
						System.err.println( e );
					}
				} else {
					System.out.println( "Canceled" );
				}
			}
			Thread.sleep(500);
			String nextOutput = dd.status().toString();
			//System.out.println(nextOutput);
			if (!output.equals(nextOutput)) {
				output = nextOutput;
				System.out.println( output );
			}
		}
	}
	
	public static void main ( String[] args ) throws Exception {
		//testA(args);
		testB(args);		
	}

}
