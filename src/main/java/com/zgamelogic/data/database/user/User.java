package com.zgamelogic.data.database.user;

import com.zgamelogic.data.services.auth.Notification;
import com.zgamelogic.data.services.auth.Permission;
import com.zgamelogic.data.services.discord.DiscordToken;
import com.zgamelogic.data.services.discord.DiscordUser;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.*;

@Data
@Entity
@ToString
@NoArgsConstructor
@Table(name = "msu_users")
public class User {

    @Id
    private String id;
    private String username;
    private String email;
    private Date lastLoggedIn;
    private String refreshToken;

    @ElementCollection
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "object_name")
    @Column(name = "permissions")
    private Map<String, String> permissions = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "user_notifications", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "server_name")
    @Column(name = "notifications")
    private Map<String, String> notifications = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "user_device_ids", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "device")
    private List<String> deviceIds;

    public User(DiscordUser discordUser, DiscordToken token){
        id = discordUser.getId();
        username = discordUser.getUsername();
        email = discordUser.getEmail();
        lastLoggedIn = new Date();
        permissions = new HashMap<>();
        refreshToken = token.getRefresh_token();
    }

    public void updateUser(DiscordUser discordUser, DiscordToken token){
        username = discordUser.getUsername();
        email = discordUser.getEmail();
        refreshToken = token.getRefresh_token();
    }

    public void addPermission(String obj, String perm){
        if(permissions.containsKey(obj)){
            permissions.put(obj, permissions.get(obj) + perm);
        } else {
            permissions.put(obj, perm);
        }
    }

    public void addPermission(Permission permission){
        addPermission(permission.getServer(), permission.getPermission());
    }

    public void removePermission(String obj, String perm){
        if(!permissions.containsKey(obj)) return;
        String newPerms = permissions.get(obj).replace(perm, "");
        permissions.put(obj, newPerms);
    }

    public void removePermission(Permission permission){
        removePermission(permission.getServer(), permission.getPermission());
    }

    public void addNotification(String obj, String perm){
        if(notifications.containsKey(obj)){
            notifications.put(obj, notifications.get(obj) + perm);
        } else {
            notifications.put(obj, perm);
        }
    }

    public void addNotification(Notification notification){
        addNotification(notification.getServer(), notification.getNotification());
    }

    public void removeNotification(String obj, String perm){
        if(!notifications.containsKey(obj)) return;
        String newPerms = notifications.get(obj).replace(perm, "");
        notifications.put(obj, newPerms);
    }

    public void removeNotification(Notification notification){
        removeNotification(notification.getServer(), notification.getNotification());
    }
}
