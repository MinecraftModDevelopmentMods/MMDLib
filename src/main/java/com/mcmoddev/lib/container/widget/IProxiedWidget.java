package com.mcmoddev.lib.container.widget;

public interface IProxiedWidget extends IWidget {
    IWidget getOriginalWidget();
}
