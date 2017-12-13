package com.mcmoddev.lib.util;

import java.util.concurrent.ConcurrentHashMap;

public class LibIoC {

	protected ConcurrentHashMap<Class<?>, Object> container = new ConcurrentHashMap<Class<?>, Object>();
	protected static LibIoC _instance;
	
	protected LibIoC() {
	
	}
	
	public <K, V extends K> boolean register(Class<K> key, V value) {
	    return _instance.container.put(key, value) == null;
	}

	@SuppressWarnings("unchecked")
	public <K, V extends K> V resolve(Class<K> keyObject) {
	    return (V) container.get(keyObject);
	}
	
	public void wireup() {
//		ItemGroups.init();
//		
//		this.register(ITabProvider.class, ItemGroups.myTabs);
	}
	
	public static LibIoC getInstance() {
		return getInstance(true);
	}
	
	public static LibIoC getInstance(Boolean autoWirup) {
	      if(_instance == null) {
	         _instance = new LibIoC();
	         
	         if (autoWirup)
	        	 _instance.wireup();
	      }
	      
	      return _instance;
	   }
}