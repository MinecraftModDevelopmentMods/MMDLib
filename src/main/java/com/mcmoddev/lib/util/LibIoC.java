package com.mcmoddev.lib.util;

import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;

import net.minecraft.util.ResourceLocation;

public class LibIoC {

	protected ConcurrentHashMap<HashTuple2<Class<?>, String>, Object> container = new ConcurrentHashMap<>();
	protected static LibIoC _instance;
	
	protected LibIoC() {
	
	}
	
	public <K, V extends K> boolean register(Class<K> key, V value) {
	    return _instance.container.put(new HashTuple2<Class<?>, String>(key, ""), value) == null;
	}

	public <K, V extends K> boolean register(Class<K> key, V value, ResourceLocation resourceLocation) {
	    return _instance.container.put(new HashTuple2<Class<?>, String>(key, resourceLocation.toString()), value) == null;
	}
	
	public <K, V extends K> boolean register(Class<K> key, V value, String instanceName) {
	    return _instance.container.put(new HashTuple2<Class<?>, String>(key, instanceName), value) == null;
	}
	
	@SuppressWarnings("unchecked")
	public <K, V extends K> V resolve(Class<K> keyObject) {
	    return (V) this.resolve(keyObject, "");
	}
	
	@SuppressWarnings("unchecked")
	public <K, V extends K> V resolve(Class<K> keyObject, ResourceLocation resourceLocation) {
		return (V) this.resolve(keyObject, resourceLocation.toString());
	}
	
	@SuppressWarnings("unchecked")
	public <K, V extends K> V resolve(Class<K> keyObject, String instanceName) {
	    return (V) container.get(new HashTuple2<Class<?>, String>(keyObject, instanceName));
	}
	
	public void wireup() {
		// TODO: Put code here for preliminary wireup e.g. ITabProvider to a concrete such as Tabcontainer
	}
	
	public static LibIoC getInstance() {
		return getInstance(true);
	}
	
	public static LibIoC getInstance(boolean autoWirup) {
	      if(_instance == null) {
	         _instance = new LibIoC();
	         
	         if (autoWirup)
	        	 _instance.wireup();
	      }
	      
	      return _instance;
	   }
}