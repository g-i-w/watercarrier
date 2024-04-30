package watercarrier;

import java.util.*;
import java.io.*;
import paddle.*;

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

	public static void main(String[] args) throws Exception {
		SenseDevice sd = new SenseDevice();
		while(true) {
			if (sd.changed()) {
				System.out.println( "added: "+sd.addedDevices()+", removed: "+sd.removedDevices() );
				sd.init();
			}
			Thread.sleep(1000);
		}
	}
}
