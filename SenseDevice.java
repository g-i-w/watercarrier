package watercarrier;

import java.util.*;
import java.io.*;
import paddle.*;
import creek.*;

public class SenseDevice {

	Set<String> baselineDevices = new TreeSet<>();
	
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
	}
	
	public Set<String> baseline () {
		return baselineDevices;
	}
	
	public boolean changed () {
		return !deviceList().equals( baselineDevices );
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
	
	public Table deviceInfo () {
		return deviceInfo( addedDevices() );
	}

	public Table deviceInfo ( Set<String> devices ) {
		String lsblkOutput = (new SystemCommand( "lsblk --json --output name,size" )).output();
		Tree blkTree = null;
		try {
			blkTree = new JSON( lsblkOutput );
		} catch (Exception e) {
			e.printStackTrace();
		}
		Tree infoTree = new JSON();
		System.out.println( devices );
		for (Tree device : blkTree.get("blockdevices").branches()) {
			String deviceName = device.get("name").value();
			if (devices.contains(deviceName)) {
				infoTree.add( deviceName, device.get("size").value() );
			}
		}
		return new SimpleTable().data( infoTree.paths() );
	}

	public static void main(String[] args) throws Exception {
		SenseDevice sd = new SenseDevice();
		while(true) {
			if (sd.changed()) {
				System.out.println( sd.deviceInfo( sd.addedDevices() ) );
				System.out.println( "added: "+sd.addedDevices()+", removed: "+sd.removedDevices() );
				sd.init();
			}
			Thread.sleep(1000);
		}
	}
}
