package com.mmd.lib.util;

import com.google.common.base.Joiner;
import com.mmd.lib.MMDLib;

import com.mmd.lib.common.types.Constants;
import com.mmd.lib.common.types.LocationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.language.IModFileInfo;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class DataHelpers {
    private static final Logger LOGGER = MMDLib.LOGGER;

    public static List<ResourceLocation> getMaterialsList(IModFileInfo info) {
        return buildFileList(info, LocationType.MATERIAL);
    }

    public static List<ResourceLocation> getBlocksList(IModFileInfo info) {
        return buildFileList(info, LocationType.BLOCK);
    }

    public static List<ResourceLocation> getItemsList(IModFileInfo info) {
        return buildFileList(info, LocationType.ITEM);
    }

    private static String getResourcePathBase(LocationType type) {
        return switch(type) {
            case MATERIAL -> Constants.PathBits.materials;
            case ITEM -> Constants.PathBits.items;
            case BLOCK -> Constants.PathBits.blocks;
            case CONFIG -> Constants.PathBits.config;
            case NONE -> "";
            default -> "";
        };
    }

    public static List<ResourceLocation> buildFileList(IModFileInfo info, LocationType type) {
        String p = getResourcePathBase(type);

        if (p.isEmpty() || p.matches("")) return Collections.emptyList();

        Path root = info.getFile().getSecureJar().getPath(p);
        try {
            return Files.walk(root).map(root::relativize)//path -> root.relativize(path))
                    .filter(path -> path.getNameCount() <= 64) // logical bounds checking
                    .filter(path -> path.toString().toLowerCase(Locale.ENGLISH).endsWith(".json"))
                    .map(path -> Joiner.on('/').join(path))
                    .map(path -> path.substring(0, path.length() - 5)) // strip the extension
                    .map(path -> new ResourceLocation(info.getMods().get(0).getModId(), path))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            if (!(ex instanceof NoSuchFileException)) {
                LOGGER.error("Exception trying to iterate files", ex);
            }
            return Collections.emptyList();
        }
    }

    public static String loadFile(ResourceLocation fileLoc, String extension, LocationType resourceType) {
        ModFile mf = (ModFile) ModList.get().getModFileById(fileLoc.getNamespace()).getFile();
        String pathRoot = getResourcePathBase(resourceType);
        String base = pathRoot.concat("/").concat(fileLoc.getPath()).concat(".").concat(extension);
        Path p = mf.getSecureJar().getPath(base);
        StringBuilder buffer = new StringBuilder();
        try(InputStream is = p.toUri().toURL().openStream();
            InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr)) {
            br.lines().forEach(l -> buffer.append(String.format("\n%s",l)));
        } catch (IOException e) {
            LOGGER.error("Exception reading data: ", e);
            return "";
        }
        return buffer.toString().strip();
    }
}

