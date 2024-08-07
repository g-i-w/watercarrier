package watercarrier;

import java.util.*;
import java.io.*;
import paddle.*;
import creek.*;

public class SenseDevice {

	Set<String> baselineDevices = new TreeSet<>();
	Set<String> lastCheck;
	
	public SenseDevice () {
		init();
	}

	public static Set<String> deviceList () {
		Set<String> list = new TreeSet<>();
		for (String device : (new SystemCommand( "ls /dev" )).output().split( "\n" )) list.add( device );
		return list;
	}
	
	public void init () {
		baselineDevices = deviceList();
		lastCheck = baselineDevices;
	}
	
	public Set<String> baseline () {
		return baselineDevices;
	}
	
	public boolean changed () {
		Set<String> thisCheck = deviceList();
		if (!lastCheck.equals( thisCheck )) {
			lastCheck = thisCheck;
			return true;
		}
		return false;
	}
	
	public Set<String> addedDevices () {
		Set<String> added = new TreeSet<>( deviceList() );
		added.removeAll( baselineDevices );
		return added;
	}

	public Set<String> removedDevices () {
		Set<String> removed = new TreeSet<>( baselineDevices );
		removed.removeAll( deviceList() );
		return removed;
	}
	
	/*public Table deviceInfo () {
		return deviceInfo( addedDevices() );
	}*/
	
	public Tree deviceTree () {
		try {
			return new JSON(
				(new SystemCommand( "lsblk --json --output name,size,mountpoints" )).output()
			);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/*public Table deviceInfo ( Set<String> devices ) {
		//System.out.println( devices );
		Tree deviceTree = deviceTree();
		if (deviceTree == null) return new SimpleTable();
		Tree infoTree = new JSON();
		for (Tree device : deviceTree.get("blockdevices").branches()) {
			String deviceName = device.get("name").value();
			if (devices.contains(deviceName)) {
				infoTree.add( deviceName, device.get("size").value() );
			}
		}
		return new SimpleTable().data( infoTree.paths() );
	}*/

	public static void main(String[] args) throws Exception {
		SenseDevice sd = new SenseDevice();
		while(true) {
			if (sd.changed()) {
				System.out.println( sd.deviceTree() );
				System.out.println( "added: "+sd.addedDevices()+", removed: "+sd.removedDevices() );
				sd.init();
			}
			Thread.sleep(1000);
		}
	}
}
