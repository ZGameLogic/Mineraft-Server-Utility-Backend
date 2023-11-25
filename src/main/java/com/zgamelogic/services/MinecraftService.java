package com.zgamelogic.services;

import com.zgamelogic.data.database.curseforge.CurseforgeProject;
import com.zgamelogic.data.services.curseforge.CurseforgeMod;
import com.zgamelogic.data.services.minecraft.MinecraftServerVersion;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public abstract class MinecraftService {

    public static HashMap<String, HashMap<String, MinecraftServerVersion>> getMinecraftServerVersions(String curseforgeToken, List<CurseforgeProject> projects){
        HashMap<String, HashMap<String, MinecraftServerVersion>> versions = new HashMap<>();
        HashMap<String, MinecraftServerVersion> vanillaVersions = new HashMap<>();
        try {
            Document doc = Jsoup.connect("https://mcversions.net").get();
            LinkedList<Thread> threads = new LinkedList<>();
            doc.getElementsByClass("ncItem").forEach(element -> {
                String id = element.id();
                if(id.toLowerCase().contains("w") || id.toLowerCase().contains("pre") || id.toLowerCase().contains("rc")) return;
                String downloadPage = element.select("a").get(0).absUrl("href");
                    threads.add(new Thread(() -> {
                        try {
                            Document downloadDoc = Jsoup.connect(downloadPage).get();
                            String downloadServerLink = downloadDoc.select("a:contains(Download Server Jar)").get(0).select("a").get(0).absUrl("href");
                            synchronized (vanillaVersions){
                                vanillaVersions.put(id, new MinecraftServerVersion(id, downloadServerLink));
                            }
                        } catch (IOException | IndexOutOfBoundsException ignored) {}
                    }));
                    threads.getLast().start();
            });
            while(!threads.isEmpty()) threads.removeIf(thread -> !thread.isAlive());
        } catch (IOException ignored) {}
        versions.put("vanilla", vanillaVersions);

        projects.forEach(project -> {
            HashMap<String, MinecraftServerVersion> projectVersions = new HashMap<>();
            CurseforgeMod mod = CurseforgeService.getCurseforgeMod(curseforgeToken, project.getId());
            if(mod.getServerFileName() == null || mod.getServerFileUrl() == null) return;
            projectVersions.put(mod.getServerFileName(), new MinecraftServerVersion(mod.getServerFileName(), mod.getServerFileUrl()));
            versions.put(project.getName(), projectVersions);
        });

        return versions;
    }

    public static void downloadServer(File dir, String link){
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.execute(link, HttpMethod.GET, requestCallback -> {
            requestCallback.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }, clientHttpResponse -> {
            FileCopyUtils.copy(clientHttpResponse.getBody(), new FileOutputStream(dir.getPath() + "/server.jar"));
            return null;
        });
    }
}