package com.miner.socket;


import android.util.Log;

import com.miner.listener.OnSocketStateListener;
import com.miner.utils.DateUtil;


import java.io.BufferedWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Created by Mr.Zhang on 2017/12/18.
 */

public class SocketClient {
    static Socket client;
    OnSocketStateListener listener;
    private int port;
    private String TAG = "SocketClient";
    /*连接线程*/
    private Thread connectThread;
    private OutputStream outputStream;
    private String host;
    /*默认重连*/
    private boolean isReConnect = true;
    private InputStream inputStream;
    private BufferedWriter bufferedWriter;
    private int reconnect = 0;
    private String savePath;
    private FileOutputStream fout;
    private ScheduledExecutorService executor;
    private int testnum=1;


    public SocketClient(String shost, int sport, String savePath, OnSocketStateListener listener) {
        this.listener = listener;
        this.savePath = savePath;
        host = shost;
        port = sport;
        conn();
    }

    public void onDestroy() {
        isReConnect = false;
        releaseSocket();
    }

    private void conn() {
        if (client == null && connectThread == null) {
            connectThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        client = new Socket(host, port);
                        if (client.isConnected() && !client.isClosed()) {
                            /*发送心跳数据*/
                            sendBeatData();
                        }else {
                            client.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        listener.socketstate("disconnected");
                    }
                }
            });
              /*启动连接线程*/
            connectThread.start();
        }
    }

    private void sendBeatData() {
        //定时器
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(
                new EchoServer(),
                0,
                waiting(),
                TimeUnit.SECONDS);
    }

    class EchoServer implements Runnable {
        @Override
        public void run() {
            try {
                outputStream = client.getOutputStream();
                bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
                bufferedWriter.write("test");
                bufferedWriter.flush();
                Log.e(TAG, "run: test" );
                if (testnum>12){
                    listener.socketstate("connected");
                }
                testnum++;
            } catch (Exception e) {
                e.printStackTrace();
                        /*发送失败说明socket断开了或者出现了其他错误*/
                testnum=1;
                listener.socketstate("disconnected");
                Log.e(TAG, "sendBeatData:" + e.toString());
                /*重连*/
                reConn();

            }
        }
    }

    private long waiting() {
        if (reconnect > 20) {
            return 600;
        }
        if (reconnect > 30) {
            onDestroy();
        }

        return reconnect <= 7 ? 2 : 10;
    }

    /*发送数据*/
    public void sendOrder(final String order) {
        if (client != null && client.isConnected()) {
            /*发送指令*/
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        outputStream = client.getOutputStream();
                        if (outputStream != null) {
                            bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
                            bufferedWriter.write(order);
                            bufferedWriter.flush();
                            receiveData();
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }).start();

        } else {
            listener.socketstate("disconnected");
        }
    }

    /**
     * 接收数据
     */
    public void receiveData() {
        if (client != null && client.isConnected()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        inputStream = client.getInputStream();
                        String dateTime= DateUtil.getDateTimeFromMillis(System.currentTimeMillis());
                        File file = new File(savePath + "/"+dateTime+".log");
                        fout = new FileOutputStream(file);
                        int len = 0;
                        if (inputStream != null) {
                            // 客户端接收服务器端的响应，读取服务器端向客户端的输入流
                            // 缓冲区
                            while (true) {
                                byte[] buffer = new byte[inputStream.available()];
                                // 读取缓冲区
                                len = inputStream.read(buffer);
                                if (len == -1) break;
                                fout.write(buffer, 0, len);
                                fout.flush();
                            }
                            fout.close();
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                        listener.socketstate("disconnected");
                    }
                }
            }).start();

        } else {
            listener.socketstate("disconnected");
        }
    }

    private void reConn() {
        try {
            Thread.sleep(2000);
            releaseSocket();
            reconnect++;
            Log.e(TAG, "reConn: "+reconnect );
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
    }

    /*释放资源*/
    private void releaseSocket() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (outputStream != null) {
            try {
                outputStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            outputStream = null;
        }
        if (inputStream != null) {
            try {
                inputStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = null;
        }
        if (client != null) {
            try {
                client.close();

            } catch (IOException e) {
            }
            client = null;
        }

        if (connectThread != null) {
            connectThread = null;
        }

          /*重新初始化socket*/
        if (isReConnect) {
            conn();
        }

    }
}
