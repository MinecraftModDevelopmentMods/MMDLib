package com.mcmoddev.lib.API;

import com.mcmoddev.lib.init.Materials;

public interface IMMDEvent<T extends IMMDApi> {
	T getApi();
	Materials getMaterialRegistry();
}
