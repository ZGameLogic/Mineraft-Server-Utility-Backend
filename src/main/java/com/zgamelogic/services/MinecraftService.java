package com.zgamelogic.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zgamelogic.data.database.curseforge.CurseforgeProject;
import com.zgamelogic.data.services.curseforge.CurseforgeMod;
import com.zgamelogic.data.services.minecraft.MinecraftServerPingData;
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
import java.net.InetSocketAddress;
import java.net.Socket;
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

    public static MinecraftServerPingData pingServer(String url, int port){
        final byte PACKET_HANDSHAKE = 0x00, PACKET_STATUSREQUEST = 0x00, PACKET_PING = 0x01;
        final int STATUS_HANDSHAKE = 1;

        int tries = 0;
        while(tries < 3) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(url, port), 1000);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                ByteArrayOutputStream handshake_bytes = new ByteArrayOutputStream();
                DataOutputStream handshake = new DataOutputStream(handshake_bytes);

                handshake.writeByte(PACKET_HANDSHAKE);
                writeVarInt(handshake, 4);
                writeVarInt(handshake, url.length());
                handshake.writeBytes(url);
                handshake.writeShort(port);
                writeVarInt(handshake, STATUS_HANDSHAKE);

                writeVarInt(out, handshake_bytes.size());
                out.write(handshake_bytes.toByteArray());

                out.writeByte(0x01);
                out.writeByte(PACKET_STATUSREQUEST);

                readVarInt(in);
                int id = readVarInt(in);

                int length = readVarInt(in);

                byte[] data = new byte[length];
                in.readFully(data);
                ObjectMapper om = new ObjectMapper();
                MinecraftServerPingData pingData = om.readValue(new String(data), MinecraftServerPingData.class);

                out.writeByte(0x09);
                out.writeByte(PACKET_PING);
                out.writeLong(System.currentTimeMillis());

                readVarInt(in);
                id = readVarInt(in);

                socket.close();
                return pingData;
            } catch (Exception e) {
                tries++;
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        return new MinecraftServerPingData();
    }

    private static void writeVarInt(DataOutputStream out, int paramInt) throws IOException {
        while (true) {
            if ((paramInt & 0xFFFFFF80) == 0) {
                out.writeByte(paramInt);
                return;
            }
            out.writeByte(paramInt & 0x7F | 0x80);
            paramInt >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int i = 0;
        int j = 0;
        while (true) {
            int k = in.readByte();
            i |= (k & 0x7F) << j++ * 7;
            if (j > 5) {
                throw new RuntimeException("VarInt too big");
            }
            if ((k & 0x80) != 128) {
                break;
            }
        }
        return i;
    }
}