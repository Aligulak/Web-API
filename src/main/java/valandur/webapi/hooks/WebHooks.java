package valandur.webapi.hooks;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.Platform;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.util.Tuple;
import valandur.webapi.WebAPI;
import valandur.webapi.json.JsonConverter;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WebHooks {

    public enum WebHookType {
        ALL, CUSTOM, CHAT, ACHIEVEMENT, PLAYER_JOIN, PLAYER_LEAVE, PLAYER_DEATH, PLAYER_KICK, PLAYER_BAN, COMMAND,
    }

    private static Map<String, WebHook> commandHooks = new HashMap<>();
    private static Map<WebHookType, List<WebHook>> hooks = new HashMap<>();

    public static Map<String, WebHook> getCommandHooks() {
        return commandHooks;
    }

    private static final String configFileName = "hooks.conf";
    private static String userAgent = WebAPI.NAME + "/" + WebAPI.VERSION;

    public static void reloadConfig() {
        WebAPI api = WebAPI.getInstance();

        Platform platform = Sponge.getPlatform();
        String mc = platform.getContainer(Platform.Component.GAME).getVersion().orElse("?");
        String sponge = platform.getContainer(Platform.Component.IMPLEMENTATION).getVersion().orElse("?");
        userAgent = WebAPI.NAME + "/" + WebAPI.VERSION + " Sponge/" + sponge + " Minecraft/" + mc + " Java/" + System.getProperty("java.version");

        Tuple<ConfigurationLoader, ConfigurationNode> tup = api.loadWithDefaults(configFileName, "defaults/" + configFileName);
        ConfigurationNode config = tup.getSecond();

        try {
            // Add command hooks
            List<WebHook> cmds = config.getNode("command").getList(TypeToken.of(WebHook.class));
            for (WebHook hook : cmds) {
                if (!hook.isEnabled()) continue;
                commandHooks.put(hook.getName(), hook);
            }

            // Add event hooks
            ConfigurationNode eventNode = config.getNode("events");
            for (WebHookType type : WebHookType.values()) {
                List<WebHook> typeHooks = eventNode.getNode(type.toString().toLowerCase()).getList(TypeToken.of(WebHook.class));
                hooks.put(type, typeHooks.stream().filter(WebHook::isEnabled).collect(Collectors.toList()));
            }

            // Add "all" hooks
            List<WebHook> allHooks = eventNode.getNode("all").getList(TypeToken.of(WebHook.class));
            hooks.put(WebHookType.ALL, allHooks.stream().filter(WebHook::isEnabled).collect(Collectors.toList()));
        } catch (ObjectMappingException e) {
            e.printStackTrace();
        }
    }

    public static void notifyHooks(WebHookType type, String message) {
        List<WebHook> notifyHooks = new ArrayList<>(hooks.get(type));
        notifyHooks.addAll(hooks.get(WebHookType.ALL));
        for (WebHook hook : notifyHooks) {
            notifyHook(hook, type, null, new HashMap<>(), message);
        }
    }

    public static void notifyHook(WebHook hook, String source, Map<String, Tuple<String, String>> params) {
        Map<String, String> contentMap = params.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().getSecond()));
        notifyHook(hook, WebHookType.CUSTOM, source, params, JsonConverter.toString(contentMap, true));
    }
    private static void notifyHook(WebHook hook, WebHookType eventType, String source, Map<String, Tuple<String, String>> params, String content) {
        List<WebHookParam> reqParams = hook.getParams();
        if (reqParams.size() != params.size()) return;

        // Add source parameter
        if (source != null) params.put("source", new Tuple<>(source, source));

        String address = hook.getAddress();
        for (Map.Entry<String, Tuple<String, String>> entry : params.entrySet()) {
            address = address.replace("{" + entry.getKey() + "}", entry.getValue().getFirst());
        }
        final String finalAddress = address;

        String data = null;
        if (content != null) {
            try {
                data = hook.getDataType() == WebHook.WebHookDataType.JSON ? content : "body=" + URLEncoder.encode(content, "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        final String finalData = data;

        CompletableFuture.runAsync(() -> {
            HttpURLConnection connection = null;
            try {
                //Create connection
                URL url = new URL(finalAddress);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(hook.getMethod().toString());
                for (WebHookHeader header : hook.getHeaders()) {
                    String val = header.getValue();
                    for (Map.Entry<String, Tuple<String, String>> entry : params.entrySet()) {
                        val = val.replace("{" + entry.getKey() + "}", entry.getValue().getFirst());
                    }
                    connection.setRequestProperty(header.getName(), val);
                }
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("X-WebAPI-Event", eventType.toString());
                connection.setRequestProperty("charset", "utf-8");
                if (finalData != null && hook.getMethod() != WebHook.WebHookMethod.GET) {
                    connection.setRequestProperty("Content-Type", hook.getDataTypeHeader());
                    connection.setRequestProperty("Content-Length", Integer.toString(finalData.getBytes().length));
                }
                connection.setUseCaches(false);

                //Send request
                if (finalData != null && hook.getMethod() != WebHook.WebHookMethod.GET) {
                    connection.setDoOutput(true);

                    DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                    wr.writeBytes(finalData);
                    wr.close();
                }

                //Get Response
                int code = connection.getResponseCode();
                if (code != 200) {
                    WebAPI.getInstance().getLogger().warn(hook.getName() + ": RESPONSE CODE: " + code);
                } else {
                    InputStream is = connection.getInputStream();
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = rd.readLine()) != null) {
                        response.append(line);
                        response.append('\r');
                    }
                    rd.close();
                    WebAPI.getInstance().getLogger().info(hook.getName() + ": " + response.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}
