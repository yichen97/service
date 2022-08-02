package com.fanruan;

import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIONamespace;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.Transport;
import com.fanruan.cache.ClientCache;
import com.fanruan.cache.ClientState;
import com.fanruan.exception.ParamException;
import com.fanruan.pojo.MyDataSource;
import com.fanruan.pojo.message.RpcResponse;
import com.fanruan.pojo.message.SimpleMessage;
import com.fanruan.serializer.KryoSerializer;
import com.fanruan.serializer.Serializer;
import com.fanruan.utils.CodeMsg;
import com.fanruan.utils.Commons;
import com.fanruan.utils.GlobalExceptionHandler;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public class ServerStater{
    protected static final Logger logger = LogManager.getLogger();

    public static final Serializer serializer = new KryoSerializer();

    public static final Gson gson = new Gson();

    public static SocketIOServer server;

    public static ClientCache cache;


    public ServerStater(String[] DBs){
        try{
            loadConfig();
            for(String DBName : DBs){
                SocketIONamespace namespace = server.addNamespace("/" + DBName);
                addEvent(namespace);
            }
            server.start();
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void addEvent(SocketIONamespace nameSpace){
        logger.debug("配置事件监听");
        nameSpace.addConnectListener(client -> {
            logger.info(nameSpace.getName() + "- socket connected!");
            String agentID = Commons.getAgentID(client);
            if(agentID == null){
                // 如果连接信息错误，发送异常信息，关闭socket
                GlobalExceptionHandler.sendException(client, new ParamException(CodeMsg.CLIENT_ID_ERROR));
                logger.info("连接信息错误：agentID, 连接关闭");
                client.disconnect();
            }
            String dbName = client.getNamespace().getName();
            // 缓存连接
            cache.saveClient(agentID, dbName, client);

        });

        // rpc响应
        nameSpace.addEventListener("RPCResponse", byte[].class, ((client, data, ackRequest) -> {
            RpcResponse rpcResponse = serializer.deserialize(data, RpcResponse.class);
            logger.info("RPCResponse: " + (rpcResponse.getStatus() ? "success" : "fail"));
        }));

        // 处理错误事件
        nameSpace.addEventListener("ErrorMessage", String.class, ((client, data, ackRequest) -> {
            logger.info("Error: " + data);
        }));
    }

    private void loadConfig() throws IOException {
        logger.debug("加载配置");
        SocketConfig socketConfig = new SocketConfig();
        // 是否开启 Nagle 算法
//        socketConfig.setTcpNoDelay(true);

        com.corundumstudio.socketio.Configuration config =
                new com.corundumstudio.socketio.Configuration();

        InputStream in = this.getClass().getResourceAsStream("/socketIO.properties");
        Properties props = new Properties();
        InputStreamReader inputStreamReader = new InputStreamReader(in, "UTF-8");
        props.load(inputStreamReader);

        config.setSocketConfig(socketConfig);
        config.setHostname(props.getProperty("host"));
        config.setPort(Integer.parseInt(props.getProperty("port")));
        config.setBossThreads(Integer.parseInt(props.getProperty("bossCount")));
        config.setWorkerThreads(Integer.parseInt(props.getProperty("workCount")));
        config.setAllowCustomRequests(Boolean.parseBoolean(props.getProperty("allowCustomRequests")));
        config.setUpgradeTimeout(Integer.parseInt(props.getProperty("upgradeTimeout")));
        config.setPingTimeout(Integer.parseInt(props.getProperty("pingTimeout")));
        config.setPingInterval(Integer.parseInt(props.getProperty("pingInterval")));
        config.setTransports(Transport.WEBSOCKET);
        in.close();

        server = new SocketIOServer(config);
        cache = new ClientCache();

        server.addConnectListener(client -> {
            String agentID = Commons.getAgentID(client);
            if(agentID == null){
                // 如果连接信息错误，发送异常信息，关闭socket
                GlobalExceptionHandler.sendException(client, new ParamException(CodeMsg.CLIENT_ID_ERROR));
                logger.info("连接信息错误：agentID, 连接关闭");
                client.disconnect();
            }
        });

        // 添加客户端连接监听器
        server.addDisconnectListener(client -> {
            String agentID = Commons.getAgentID(client);
            if(agentID == null){
                // 如果连接信息错误，发送异常信息，关闭socket
                GlobalExceptionHandler.sendException(client, new ParamException(CodeMsg.CLIENT_ID_ERROR));
                logger.info("agentID: 连接关闭");
                client.disconnect();
            }
            // 缓存连接
            cache.deleteAgentByID(agentID);
            logger.info("agentID: " + agentID + "连接关闭");
            logger.info("agentId: " + agentID + "连接已删除");
        });

        server.addEventListener("message", String.class, ((client, data, ackRequest) -> {
            logger.info("Error: " + data);
        }));
    }
}

